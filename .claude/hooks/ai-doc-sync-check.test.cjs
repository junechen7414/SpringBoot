const assert = require('node:assert/strict');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { test } = require('node:test');

const hook = path.join(__dirname, 'ai-doc-sync-check.js');

function invoke(input) {
  const result = spawnSync(process.execPath, [hook], {
    input,
    encoding: 'utf8',
    timeout: 5000,
  });
  assert.ifError(result.error);
  assert.equal(result.status, 0);
  assert.equal(result.stderr, '');
  return result.stdout;
}

const watchedPaths = [
  'docs/agents/11-ai-tools-overview.md',
  'AGENTS.md',
  'CLAUDE.md',
  'CLAUDE.local.md',
  '.claude/CLAUDE.md',
  '.claude/CLAUDE.local.md',
  '.github/instructions/Global.instructions.md',
  '.claude/rules/security.md',
  '.claude/skills/example/SKILL.md',
  '.bob/skills/example/SKILL.md',
  '.agents/skills/example/SKILL.md',
  '.mcp.json',
  '.bob/mcp.json',
  '.agents/mcp_config.json',
  '.vscode/mcp.json',
  'skills-lock.json',
  '.bob/custom_modes.yaml',
  '.claude/agents/ai-doc-sync.md',
];

for (const relativePath of watchedPaths) {
  test(`reminds for ${relativePath}`, () => {
    const filePath = `C:/synthetic repo/${relativePath}`;
    const output = JSON.parse(invoke(JSON.stringify({
      tool_name: 'Edit',
      tool_input: { file_path: filePath },
    })));
    assert.equal(output.hookSpecificOutput.hookEventName, 'PostToolUse');
    assert.ok(output.hookSpecificOutput.additionalContext.includes(filePath));
    assert.ok(output.hookSpecificOutput.additionalContext.includes('dry-run'));
    assert.ok(output.hookSpecificOutput.additionalContext.includes('agy'));
    assert.equal(output.suppressOutput, true);
    assert.equal(output.continue, undefined);
    assert.equal(output.decision, undefined);
  });
}

test('normalizes Windows separators and accepts path fallback', () => {
  const output = JSON.parse(invoke(JSON.stringify({
    tool_name: 'Write',
    tool_input: { path: 'C:\\synthetic repo\\.agents\\mcp_config.json' },
  })));
  assert.ok(output.hookSpecificOutput.additionalContext.includes(
    'C:/synthetic repo/.agents/mcp_config.json',
  ));
});

test('ignores unrelated files and near matches', () => {
  for (const filePath of [
    'src/main/java/Example.java',
    'docs/general-guide.md',
    'not-AGENTS.md',
    '.vscode/mcp.json.bak',
    '.agents-other/skills/example/SKILL.md',
  ]) {
    assert.equal(invoke(JSON.stringify({ tool_input: { file_path: filePath } })), '');
  }
});

test('empty, malformed and missing-path payloads fail open silently', () => {
  for (const input of ['', ' ', '{', 'null', '[]', '{}', '{"tool_input":{}}']) {
    assert.equal(invoke(input), '');
  }
});
