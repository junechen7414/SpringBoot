---
name: caveman-stats
description: >
  Show real token usage and estimated savings for the current session.
  Reads directly from the Claude Code session log — no AI estimation.
  Triggers on /caveman-stats. Output is injected by the mode-tracker hook;
  the model itself does not compute the numbers.
---

This skill requires the companion `hooks/caveman-stats.js` and `hooks/caveman-mode-tracker.js` runtime and its active hook configuration. These are not bundled in this repository. If that runtime is unavailable, report that statistics are unavailable and stop; do not invent numbers, read session logs manually, or install hooks automatically.

When the companion runtime is active, it intercepts `/caveman-stats` and returns `decision: "block"` with the formatted stats as the reason. The model does not compute the numbers.
