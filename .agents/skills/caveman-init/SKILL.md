---
name: caveman-init
description: Drop the always-on caveman activation rule into the current repo for every IDE agent
---
Prerequisite: `src/tools/caveman-init.js` must exist in the target repository; it is not bundled here. If it is absent, report that this skill is unavailable and stop. Do not fetch, install, or create the companion runtime automatically.

When the prerequisite is met, run `node src/tools/caveman-init.js {{args}}` in the current repo and report the result. Use --dry-run first if the user did not pass --force, so we never silently overwrite an existing rule file.
