# Security

This document covers the repository's local `compress` implementation in `scripts/compress.py`. It does not certify another plugin installation or dismiss a scanner finding without investigation.

## Data sent to the model

Compression sends the selected file's full text in a prompt to the configured Claude backend. A validation repair sends the original, candidate output, and validation errors again. Obtain authorization and review the content before running it; a normal filename does not make a file safe to transmit.

When `ANTHROPIC_API_KEY` is set and the Anthropic Python SDK is installed, the script uses that SDK. Otherwise it invokes `claude --print` through a fixed argument list, with the prompt on stdin and without `shell=True`. The CLI's own authentication, provider, configuration loading, and network behavior depend on the active host; they are not guaranteed by this script to use desktop authentication or a particular endpoint.

## Sensitive-path rejection

Before detection or reading file contents, the script resolves the supplied path and rejects likely sensitive names and locations, including:

- `.env`, `.env.*`, `.netrc`, credentials, secrets, passwords, and common SSH key names.
- Key/certificate extensions such as `.pem`, `.key`, `.p12`, `.pfx`, and `.jks`.
- Paths under `.ssh`, `.aws`, `.gnupg`, `.kube`, or `.docker`.
- Names containing normalized tokens such as `api_key`, `access-key`, `password`, or `token`.

This is a filename/path heuristic, **not a content secret scanner**. It can reject harmless names and miss sensitive content in ordinary files. It is not an OS sandbox or a defense against concurrent filesystem changes.

Files larger than 500,000 bytes are rejected before a backend call. Code/config files and existing `*.original.md` backups are skipped by file detection.

## Writes and failure handling

The orchestrator reads the selected file and creates a sibling `<stem>.original.md` backup; it does not deliberately traverse unrelated files. The model backend/CLI has its own behavior outside this file-I/O scope.

- Empty or whitespace-only input is not sent to the backend.
- An existing backup aborts the operation without overwriting it or calling the backend.
- A missing, empty, whitespace-only, or unchanged **initial** response does not create a backup or overwrite the input.
- The backup is written and read back before the input is touched. A mismatch aborts and attempts to remove that bad backup. A backup read error propagates while the input remains untouched and the saved backup is retained.
- The candidate is then written to the input and validated. There are at most two validation passes and one targeted model repair. If both passes fail normally, the original is restored and that operation's backup is removed.

This is not an atomic transaction or a guarantee of recovery from every exception, interruption, or disk error. In particular, errors after the candidate write may leave the candidate and backup for manual recovery. Review the result and retain independent backups for important files.

## Offline regression tests

From the repository root:

```bash
python -I -B -m unittest discover -s .agents/skills/compress/scripts/tests -p 'test_*.py' -v
```

Tests use synthetic files in temporary directories and mock `call_claude`; they do not read credentials or invoke the SDK, CLI, MCP, or network. They cover rejection before detection/read/backend use, empty input and initial responses, existing backups, backup verification failures, normal success, targeted repair, and validation-exhaustion restore. They do not prove full equivalence with `caveman-compress` or validate a real backend.

## Reporting a vulnerability

Follow the project's security reporting channel if one is available. Do not put real credentials, private file contents, or unredacted prompts in a public issue.
