"""Offline regression tests for the ignored, machine-local Bob post-commit hook.

Run: python -I -B .githooks/tests/test_bob_notes_hook.py -v
Use --hook PATH to test another reviewed copy. A missing hook explicitly skips
all checks; it is not a passing security verification. Requires Bash and standard
text utilities (Git Bash on Windows). No real Git repository is needed or used.
"""

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest


HOOK_SOURCE = Path(__file__).resolve().parents[1] / "post-commit"
COMMIT = "a" * 40
MATCHED_FILE = "src/committed.txt"
UNCOMMITTED_FILE = "src/uncommitted.txt"
TIMEOUT_SECONDS = 30

# Log argc followed by every argument as NUL-delimited fields. There is no
# delegation to real Git: only these synthetic local operations are permitted.
GIT_STUB = r"""#!/bin/bash
printf '%s\0' "$#" "$@" >> "$BOB_TEST_GIT_LOG"

case "${1:-}" in
    fetch|push|pull|ls-remote|remote|clone|send-pack|receive-pack)
        printf 'Remote Git operation blocked by offline stub\n' >&2
        exit 97
        ;;
esac

if [[ "$#" == 2 && "$1" == rev-parse && "$2" == HEAD ]]; then
    printf '%s\n' "$BOB_TEST_COMMIT"
elif [[ "$#" == 2 && "$1" == rev-parse && "$2" == --show-toplevel ]]; then
    printf '%s\n' "$PWD"
elif [[ "$#" == 5 && "$1" == diff-tree && "$2" == --no-commit-id &&
        "$3" == --name-only && "$4" == -r && "$5" == "$BOB_TEST_COMMIT" ]]; then
    printf '%s\n' 'src/committed.txt'
elif [[ "$#" == 4 && "$1" == show && "$2" == "$BOB_TEST_COMMIT" &&
        "$3" == -- && "$4" == src/committed.txt ]]; then
    printf '%s\n' 'diff --git a/src/committed.txt b/src/committed.txt' '+synthetic change'
elif [[ "$#" == 8 && "$1" == notes && "$2" == --ref &&
        "$3" == refs/notes/bob && "$4" == add && "$5" == -f &&
        "$6" == -F && "$8" == "$BOB_TEST_COMMIT" ]]; then
    # The only file the stub may read is a generated note inside this fixture.
    case "$7" in
        "$TMPDIR"/*) ;;
        *) printf 'Note outside fixture blocked\n' >&2; exit 97 ;;
    esac
    while IFS= read -r line || [[ -n "$line" ]]; do
        printf '%s\n' "$line"
    done < "$7" > "$BOB_TEST_ADDED_NOTE"
else
    printf 'Unexpected Git operation blocked by offline stub\n' >&2
    exit 97
fi
"""


def synthetic_note(path):
    return json.dumps(
        {"path": path, "source": "offline-hook-test", "bobDiff": ""},
        separators=(",", ":"),
    ) + "\n"


class DefaultGateCases:
    """Run the same four pending-note states with unset and explicit-zero gates."""

    @classmethod
    def setUpClass(cls):
        if not HOOK_SOURCE.is_file():
            raise unittest.SkipTest(
                "Ignored local post-commit hook is missing; provide --hook PATH. "
                "No hook safety checks ran."
            )
        cls.bash = shutil.which("bash")
        if cls.bash is None:
            raise unittest.SkipTest("Bash unavailable; no hook safety checks ran.")
        cls.hook_bytes = HOOK_SOURCE.read_bytes()

    def setUp(self):
        temporary = TemporaryDirectory(prefix="bob-notes-hook-test-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.repo = self.root / "repo"
        self.bin = self.repo / "bin"
        self.scratch = self.root / "tmp"
        self.home = self.root / "home"
        self.pending = self.repo / ".bob" / "notes" / "pending-notes.txt"
        self.hook = self.repo / "hooks" / "post-commit"
        self.log = self.root / "git-calls.bin"
        self.added_note = self.root / "added-note.jsonl"
        self.config = self.repo / ".git" / "config"
        for directory in (
            self.bin, self.scratch, self.home, self.pending.parent,
            self.hook.parent, self.config.parent,
        ):
            directory.mkdir(parents=True, exist_ok=True)
        self.hook.write_bytes(self.hook_bytes)
        self.config.write_text("# Synthetic fixture; never use real Git.\n", encoding="utf-8")
        self.original_config = self.config.read_bytes()
        stub = self.bin / "git"
        stub.write_text(GIT_STUB, encoding="utf-8", newline="\n")
        stub.chmod(0o700)

        # Whitelist the environment, rather than forwarding credentials, Git
        # configuration, shell startup files/functions, or the caller's opt-in.
        runtime_dirs = [str(Path(self.bash).parent)]
        if os.name != "nt":
            runtime_dirs.extend(("/usr/bin", "/bin"))
        self.env = {
            key: os.environ[key]
            for key in ("SystemRoot", "WINDIR")
            if key in os.environ
        }
        self.env.update({
            "PATH": os.pathsep.join(runtime_dirs),
            "HOME": self.home.as_posix(),
            "USERPROFILE": self.home.as_posix(),
            "XDG_CONFIG_HOME": self.home.as_posix(),
            "TMPDIR": self.scratch.as_posix(),
            "TMP": self.scratch.as_posix(),
            "TEMP": self.scratch.as_posix(),
            "LC_ALL": "C",
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_SYSTEM": os.devnull,
            "GIT_TERMINAL_PROMPT": "0",
            "BOB_TEST_GIT_LOG": self.log.as_posix(),
            "BOB_TEST_ADDED_NOTE": self.added_note.as_posix(),
            "BOB_TEST_COMMIT": COMMIT,
        })
        self.env.pop("BOB_NOTES_REMOTE_SYNC", None)
        if self.gate_value == "0":
            self.env["BOB_NOTES_REMOTE_SYNC"] = "0"
        else:
            self.assertIsNone(self.gate_value)

        prerequisite = self.run_bash(
            "-c",
            ('[[ "$OSTYPE" == msys* ]] || exit 77; ' if os.name == "nt" else "")
            + 'for tool in grep cut tr mktemp mv rm timeout; do '
            'command -v "$tool" >/dev/null || exit 77; done; '
            'printf "%s\\n" "$TMPDIR"',
        )
        if prerequisite.returncode == 77:
            self.skipTest("Bash utilities unavailable; use Git Bash on Windows. No hook safety checks ran.")
        self.assertEqual(prerequisite.returncode, 0, prerequisite.stderr)
        self.shell_scratch = prerequisite.stdout.strip()
        syntax = self.run_bash("-n", self.hook.as_posix())
        self.assertEqual(syntax.returncode, 0, syntax.stderr)

    def run_bash(self, *arguments):
        return subprocess.run(
            [self.bash, "--noprofile", "--norc", *arguments],
            cwd=self.repo,
            env=self.env,
            stdin=subprocess.DEVNULL,
            capture_output=True,
            encoding="utf-8",
            errors="replace",
            timeout=TIMEOUT_SECONDS,
            check=False,
        )

    def run_hook(self):
        # Prefix PATH inside Bash so Windows drive-letter/path-list conversion
        # cannot accidentally put a real Git ahead of the stub.
        result = self.run_bash(
            "-c",
            'export PATH="$PWD/bin:$PATH"; '
            '[[ "$(command -v git)" == "$PWD/bin/git" ]] || exit 98; '
            'exec "$BASH" --noprofile --norc "$PWD/hooks/post-commit"',
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.config.read_bytes(), self.original_config)
        fields = self.log.read_bytes().split(b"\0")
        self.assertEqual(fields.pop(), b"")
        calls = []
        while fields:
            count = int(fields.pop(0))
            self.assertGreaterEqual(count, 0)
            self.assertGreaterEqual(len(fields), count)
            calls.append(tuple(field.decode("utf-8") for field in fields[:count]))
            del fields[:count]
        for call in calls:
            self.assertFalse(
                {"fetch", "push", "pull", "ls-remote", "remote", "clone",
                 "send-pack", "receive-pack"}.intersection(call),
                f"Remote sync or remote discovery attempted: {call!r}",
            )
        return calls

    def pending_calls(self):
        return [
            ("rev-parse", "HEAD"),
            ("rev-parse", "--show-toplevel"),
            ("diff-tree", "--no-commit-id", "--name-only", "-r", COMMIT),
        ]

    def test_pending_missing(self):
        self.assertEqual(self.run_hook(), [("rev-parse", "HEAD")])
        self.assertFalse(self.pending.exists())
        self.assertFalse(self.added_note.exists())

    def test_pending_empty(self):
        self.pending.write_bytes(b"")
        self.assertEqual(self.run_hook(), [("rev-parse", "HEAD")])
        self.assertEqual(self.pending.read_bytes(), b"")
        self.assertFalse(self.added_note.exists())

    def test_pending_without_matching_note(self):
        pending = synthetic_note(UNCOMMITTED_FILE)
        self.pending.write_text(pending, encoding="utf-8", newline="\n")
        self.assertEqual(self.run_hook(), self.pending_calls())
        self.assertEqual(self.pending.read_text(encoding="utf-8"), pending)
        self.assertFalse(self.added_note.exists())

    def test_matching_note_is_attached_locally(self):
        matched = synthetic_note(MATCHED_FILE)
        remaining = synthetic_note(UNCOMMITTED_FILE)
        self.pending.write_text(matched + remaining, encoding="utf-8", newline="\n")
        calls = self.run_hook()
        self.assertEqual(calls[:-1], self.pending_calls() + [("show", COMMIT, "--", MATCHED_FILE)])
        self.assertEqual(len(calls[-1]), 8)
        self.assertEqual(calls[-1][:6], ("notes", "--ref", "refs/notes/bob", "add", "-f", "-F"))
        self.assertTrue(calls[-1][6].startswith(self.shell_scratch + "/"), calls[-1])
        self.assertEqual(calls[-1][7], COMMIT)
        self.assertEqual(self.added_note.read_text(encoding="utf-8"), matched)
        self.assertEqual(self.pending.read_text(encoding="utf-8"), remaining)


class UnsetGateTests(DefaultGateCases, unittest.TestCase):
    gate_value = None


class ZeroGateTests(DefaultGateCases, unittest.TestCase):
    gate_value = "0"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--hook", type=Path, default=HOOK_SOURCE)
    options, unittest_arguments = parser.parse_known_args()
    HOOK_SOURCE = options.hook.resolve()
    unittest.main(argv=[sys.argv[0], *unittest_arguments])
