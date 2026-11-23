<p align="center">
  <img src="https://em-content.zobj.net/source/apple/391/rock_1faa8.png" width="80" />
</p>

<h1 align="center">caveman-compress</h1>

<p align="center">
  <strong>shrink memory file. save token every session.</strong>
</p>

---

A Claude Code skill that compresses your project memory files (`CLAUDE.md`, todos, preferences) into caveman format — so every session loads fewer tokens automatically.

Claude read `CLAUDE.md` on every session start. If file big, cost big. Caveman make file small. Cost go down forever.

## What It Do

```
/caveman:compress CLAUDE.md
```

```
CLAUDE.md          ← compressed (Claude reads this — fewer tokens every session)
CLAUDE.original.md ← human-readable backup (you edit this)
```

A sibling `.original.md` backup is kept after successful compression. An existing backup makes the script stop rather than overwrite it. Review and manage that backup before another run; failures or interruptions are not guaranteed to restore every file automatically.

## Benchmarks

Real results on real project files:

| File | Original | Compressed | Saved |
|------|----------:|----------:|------:|
| `claude-md-preferences.md` | 706 | 285 | **59.6%** |
| `project-notes.md` | 1145 | 535 | **53.3%** |
| `claude-md-project.md` | 1122 | 687 | **38.8%** |
| `todo-list.md` | 627 | 388 | **38.1%** |
| `mixed-with-code.md` | 888 | 574 | **35.4%** |
| **Average** | **898** | **494** | **45%** |

All validations passed ✅ — headings, code blocks, URLs, file paths preserved exactly.

## Before / After

<table>
<tr>
<td width="50%">

### 📄 Original (706 tokens)

> "I strongly prefer TypeScript with strict mode enabled for all new code. Please don't use `any` type unless there's genuinely no way around it, and if you do, leave a comment explaining the reasoning. I find that taking the time to properly type things catches a lot of bugs before they ever make it to runtime."

</td>
<td width="50%">

### 🪨 Caveman (285 tokens)

> "Prefer TypeScript strict mode always. No `any` unless unavoidable — comment why if used. Proper types catch bugs early."

</td>
</tr>
</table>

**Same instructions. 60% fewer tokens. Every. Single. Session.**

## Security

The local `compress` implementation sends file text to the configured Claude backend and overwrites the selected file after verifying a sibling backup. Sensitive-looking paths are rejected, but this is not a content secret scanner. Review the content and obtain authorization before transmission; do not dismiss scanner findings solely because the subprocess arguments are fixed. See [SECURITY.md](./SECURITY.md) for the data boundary and failure-handling limits.

## Install

Compress is built in with the `caveman` plugin. Install `caveman` once, then use `/caveman:compress`.

If you need local files, the compress skill lives at:

```bash
caveman-compress/
```

**Requires:** Python 3.10+

## Usage

```
/caveman:compress <filepath>
```

Examples:
```
/caveman:compress CLAUDE.md
/caveman:compress docs/preferences.md
/caveman:compress todos.md
```

### What files work

| Type | Compress? |
|------|-----------|
| `.md`, `.txt`, `.rst` | ✅ Yes |
| Extensionless natural language | ✅ Yes |
| `.py`, `.js`, `.ts`, `.json`, `.yaml` | ❌ Skip (code/config) |
| `*.original.md` | ❌ Skip (backup files) |

Sensitive-looking names and locations (`.env`, `.env.*`, credentials, keys, `.ssh`, and similar paths) are rejected before detection or reads. Files above 500,000 bytes are rejected. Empty input, an existing backup, and empty or unchanged initial model replies leave the input untouched. Ordinary filenames can still contain secrets; the path check does not inspect content for them.

This repository uses `AGENTS.md`; the upstream `CLAUDE.md` examples below are not instructions to create a competing entry file here. The local package is under `.agents/skills/compress/`; an upstream `/caveman:compress` plugin installation is a separate entry point whose availability depends on the host.

## How It Work

```
selected file
        ↓
resolve path / reject sensitive-looking paths / check size / detect file type
        ↓
read input / reject empty input or an existing backup
        ↓
Claude compresses       (tokens — one call)
        ↓
reject empty or unchanged initial output
        ↓
write sibling .original.md backup / read it back and compare
        ↓
write candidate to the selected file
        ↓
validate candidate      (no tokens)
        ↓
if invalid: one targeted Claude repair / write repair / validate once more
        ↓
valid: keep candidate + backup
invalid twice: restore original / remove this operation's backup
```

There are at most two validation passes and one targeted repair call, not two repair retries. Only the initial compression and optional repair use the backend. Unexpected exceptions or interruptions after the candidate write can still require manual recovery from the backup; this is not an atomic write transaction.

### Offline regression tests

From the repository root, run synthetic-file tests with the model backend mocked:

```bash
python -I -B -m unittest discover -s .agents/skills/compress/scripts/tests -p 'test_*.py' -v
```

These tests do not invoke the model SDK, CLI, MCP, or network. See [SECURITY.md](./SECURITY.md) for coverage and limits.

## What Is Preserved

Caveman compress natural language. It never touch:

- Code blocks (` ``` ` fenced or indented)
- Inline code (`` `backtick content` ``)
- URLs and links
- File paths (`/src/components/...`)
- Commands (`npm install`, `git commit`)
- Technical terms, library names, API names
- Headings (exact text preserved)
- Tables (structure preserved, cell text compressed)
- Dates, version numbers, numeric values

## Why This Matter

`CLAUDE.md` loads on **every session start**. A 1000-token project memory file costs tokens every single time you open a project. Over 100 sessions that's 100,000 tokens of overhead — just for context you already wrote.

Caveman cut that by ~45% on average. Same instructions. Same accuracy. Less waste.

```
┌────────────────────────────────────────────┐
│  TOKEN SAVINGS PER FILE    █████       45% │
│  SESSIONS THAT BENEFIT     ██████████ 100% │
│  INFORMATION PRESERVED     ██████████ 100% │
│  SETUP TIME                █            1x │
└────────────────────────────────────────────┘
```

## Part of Caveman

This skill is part of the [caveman](https://github.com/JuliusBrussee/caveman) toolkit — making Claude use fewer tokens without losing accuracy.

- **caveman** — make Claude *speak* like caveman (cuts response tokens ~65%)
- **caveman-compress** — make Claude *read* less (cuts context tokens ~45%)
