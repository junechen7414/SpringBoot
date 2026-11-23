"""Offline regression tests for the legacy compress entry point."""

import importlib
import importlib.util
import io
import sys
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import mock


# Load this scripts package by its absolute location, not a generic "scripts"
# import that could resolve to another skill or repository on sys.path.
SCRIPTS_DIR = Path(__file__).resolve().parents[1]
PACKAGE_NAME = "_legacy_compress_scripts_under_test"
package_spec = importlib.util.spec_from_file_location(
    PACKAGE_NAME,
    SCRIPTS_DIR / "__init__.py",
    submodule_search_locations=[str(SCRIPTS_DIR)],
)
if package_spec is None or package_spec.loader is None:
    raise ImportError(f"Cannot load compress scripts from {SCRIPTS_DIR}")
package = importlib.util.module_from_spec(package_spec)
sys.modules[PACKAGE_NAME] = package
package_spec.loader.exec_module(package)
compress = importlib.import_module(f"{PACKAGE_NAME}.compress")


ORIGINAL = """# Offline example

The project uses a careful review process before a small update is accepted.
Read https://example.test/guide for the documented workflow.

```sh
python sample.py
```
"""
COMPRESSED = """# Offline example

Review changes before accepting updates.
Read https://example.test/guide for workflow.

```sh
python sample.py
```
"""
INVALID = COMPRESSED.replace("https://example.test/guide", "the guide")


class CompressFileTests(unittest.TestCase):
    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.source = self.root / "guide.md"
        self.backup = self.root / "guide.original.md"
        self.source.write_text(ORIGINAL)
        self.backend = self.enterContext(
            mock.patch.object(compress, "call_claude", return_value=COMPRESSED)
        )
        self.enterContext(redirect_stdout(io.StringIO()))

    def test_sensitive_paths_are_rejected_before_detection_read_or_backend(self):
        for relative_path in (
            "secrets.txt",
            "credentials.md",
            "SECRETS.TXT",
            "api_key_guide.md",
            "client.pem",
            ".ssh/guide.md",
            ".SSH/reference.md",
        ):
            with self.subTest(path=relative_path):
                source = self.root / relative_path
                source.parent.mkdir(parents=True, exist_ok=True)
                source.write_text(ORIGINAL)
                with (
                    mock.patch.object(compress, "should_compress") as detect,
                    mock.patch.object(Path, "read_text") as read,
                    mock.patch.object(Path, "write_text") as write,
                ):
                    with self.assertRaisesRegex(ValueError, "looks sensitive"):
                        compress.compress_file(source)
                    detect.assert_not_called()
                    read.assert_not_called()
                    write.assert_not_called()
                    self.backend.assert_not_called()
                self.assertEqual(source.read_text(), ORIGINAL)
                self.assertFalse(source.with_name(source.stem + ".original.md").exists())

    def test_sensitive_check_uses_resolved_path(self):
        resolved_path = self.root / ".ssh" / "guide.md"
        with (
            mock.patch.object(Path, "resolve", return_value=resolved_path) as resolve,
            mock.patch.object(compress, "should_compress") as detect,
            mock.patch.object(Path, "read_text") as read,
        ):
            with self.assertRaisesRegex(ValueError, "looks sensitive"):
                compress.compress_file(self.source)
            resolve.assert_called_once_with()
            detect.assert_not_called()
            read.assert_not_called()
            self.backend.assert_not_called()
        self.assertEqual(self.source.read_text(), ORIGINAL)
        self.assertFalse(self.backup.exists())

    def test_empty_input_does_not_call_backend_or_write_files(self):
        for original in ("", " \n\t  \n"):
            with self.subTest(original=repr(original)):
                self.source.write_text(original)
                with (
                    mock.patch.object(Path, "write_text") as write,
                    mock.patch.object(compress, "validate") as validate,
                ):
                    self.assertFalse(compress.compress_file(self.source))
                    self.backend.assert_not_called()
                    write.assert_not_called()
                    validate.assert_not_called()
                self.assertEqual(self.source.read_text(), original)
                self.assertFalse(self.backup.exists())

    def test_empty_response_does_not_write_files(self):
        for response in (None, "", " \n\t"):
            with self.subTest(response=repr(response)):
                self.backend.reset_mock()
                self.backend.return_value = response
                with (
                    mock.patch.object(Path, "write_text") as write,
                    mock.patch.object(compress, "validate") as validate,
                ):
                    self.assertFalse(compress.compress_file(self.source))
                    self.backend.assert_called_once_with(
                        compress.build_compress_prompt(ORIGINAL)
                    )
                    write.assert_not_called()
                    validate.assert_not_called()
                self.assertEqual(self.source.read_text(), ORIGINAL)
                self.assertFalse(self.backup.exists())

    def test_unchanged_response_does_not_write_files(self):
        for response in (ORIGINAL, f"\n {ORIGINAL} \n"):
            with self.subTest(response=repr(response)):
                self.backend.reset_mock()
                self.backend.return_value = response
                with (
                    mock.patch.object(Path, "write_text") as write,
                    mock.patch.object(compress, "validate") as validate,
                ):
                    self.assertFalse(compress.compress_file(self.source))
                    self.backend.assert_called_once_with(
                        compress.build_compress_prompt(ORIGINAL)
                    )
                    write.assert_not_called()
                    validate.assert_not_called()
                self.assertEqual(self.source.read_text(), ORIGINAL)
                self.assertFalse(self.backup.exists())

    def test_existing_backup_is_preserved_without_calling_backend(self):
        previous_backup = "An earlier offline draft.\n"
        self.backup.write_text(previous_backup)
        with mock.patch.object(Path, "write_text") as write:
            self.assertFalse(compress.compress_file(self.source))
            self.backend.assert_not_called()
            write.assert_not_called()
        self.assertEqual(self.source.read_text(), ORIGINAL)
        self.assertEqual(self.backup.read_text(), previous_backup)

    def test_backup_readback_mismatch_does_not_write_source(self):
        read_text = Path.read_text
        write_text = Path.write_text

        def read_with_bad_backup(path, *args, **kwargs):
            if path == self.backup:
                return "Incomplete offline backup.\n"
            return read_text(path, *args, **kwargs)

        with (
            mock.patch.object(Path, "read_text", autospec=True, side_effect=read_with_bad_backup),
            mock.patch.object(Path, "write_text", autospec=True, side_effect=write_text) as write,
            mock.patch.object(compress, "validate") as validate,
        ):
            self.assertFalse(compress.compress_file(self.source))
            self.assertEqual(write.call_args_list, [mock.call(self.backup, ORIGINAL)])
            validate.assert_not_called()
        self.assertEqual(self.source.read_text(), ORIGINAL)
        self.assertFalse(self.backup.exists())
        self.backend.assert_called_once()

    def test_backup_readback_error_does_not_write_source(self):
        read_text = Path.read_text
        write_text = Path.write_text

        def read_with_backup_error(path, *args, **kwargs):
            if path == self.backup:
                raise OSError("Offline backup read failed")
            return read_text(path, *args, **kwargs)

        with (
            mock.patch.object(Path, "read_text", autospec=True, side_effect=read_with_backup_error),
            mock.patch.object(Path, "write_text", autospec=True, side_effect=write_text) as write,
            mock.patch.object(compress, "validate") as validate,
        ):
            with self.assertRaisesRegex(OSError, "Offline backup read failed"):
                compress.compress_file(self.source)
            self.assertEqual(write.call_args_list, [mock.call(self.backup, ORIGINAL)])
            validate.assert_not_called()
        self.assertEqual(self.source.read_text(), ORIGINAL)
        self.assertEqual(self.backup.read_text(), ORIGINAL)
        self.backend.assert_called_once()

    def test_success_keeps_verified_backup_and_compressed_source(self):
        with mock.patch.object(compress, "validate", wraps=compress.validate) as validate:
            self.assertTrue(compress.compress_file(self.source))
            validate.assert_called_once_with(self.backup, self.source)
        self.backend.assert_called_once_with(compress.build_compress_prompt(ORIGINAL))
        self.assertEqual(self.source.read_text(), COMPRESSED)
        self.assertEqual(self.backup.read_text(), ORIGINAL)

    def test_validation_failure_retries_with_fix_prompt(self):
        self.backend.side_effect = [INVALID, COMPRESSED]
        with mock.patch.object(compress, "validate", wraps=compress.validate) as validate:
            self.assertTrue(compress.compress_file(self.source))
            self.assertEqual(validate.call_args_list, [mock.call(self.backup, self.source)] * 2)
        self.assertEqual(self.backend.call_count, 2)
        self.assertEqual(
            self.backend.call_args_list[0],
            mock.call(compress.build_compress_prompt(ORIGINAL)),
        )
        fix_prompt = self.backend.call_args_list[1].args[0]
        self.assertIn("URL mismatch:", fix_prompt)
        self.assertIn("ONLY fix the listed errors", fix_prompt)
        self.assertIn(ORIGINAL, fix_prompt)
        self.assertIn(INVALID, fix_prompt)
        self.assertEqual(self.source.read_text(), COMPRESSED)
        self.assertEqual(self.backup.read_text(), ORIGINAL)

    def test_exhausted_validation_retries_restore_source_and_remove_backup(self):
        self.backend.return_value = INVALID
        write_text = Path.write_text
        with (
            mock.patch.object(Path, "write_text", autospec=True, side_effect=write_text) as write,
            mock.patch.object(compress, "validate", wraps=compress.validate) as validate,
        ):
            self.assertFalse(compress.compress_file(self.source))
            self.assertEqual(validate.call_count, 2)
            self.assertEqual(
                write.call_args_list,
                [
                    mock.call(self.backup, ORIGINAL),
                    mock.call(self.source, INVALID),
                    mock.call(self.source, INVALID),
                    mock.call(self.source, ORIGINAL),
                ],
            )
        self.assertEqual(self.backend.call_count, 2)
        self.assertEqual(self.source.read_text(), ORIGINAL)
        self.assertFalse(self.backup.exists())


if __name__ == "__main__":
    unittest.main()
