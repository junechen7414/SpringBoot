#!/usr/bin/env node
/**
 * PostToolUse hook：偵測剛被 Edit/Write/MultiEdit 的檔案是否屬於「跨 AI 工具同步面」。
 * 若是，向主代理注入一段 system reminder，建議委派給 ai-doc-sync subagent 檢查同步。
 *
 * 輸入：stdin 收到 Claude Code 的 hook payload JSON（含 tool_name / tool_input.file_path）。
 * 輸出：stdout 印出 JSON，含 hookSpecificOutput.additionalContext；無關檔案則不輸出（靜默）。
 *
 * 設計原則：永不阻擋（不設 continue:false）、出錯也安靜退出，避免干擾正常編輯流程。
 */

// 命中即視為「同步相關」的路徑樣式（以 / 正規化後比對）
const PATTERNS = [
  /(^|\/)docs\/agents\//,            // 文件真相源
  /(^|\/)CLAUDE\.md$/,               // Claude 快速摘要
  /(^|\/)AGENTS\.md$/,               // Claude agents 匯入入口
  /(^|\/)\.github\/instructions\//,  // Cline / Copilot 指令
  /(^|\/)\.claude\/skills\//,        // Claude skills
  /(^|\/)\.bob\/skills\//,           // Bob skills（鏡像）
  /(^|\/)\.bob\/mcp\.json$/,         // Bob MCP 設定
  /(^|\/)\.mcp\.json$/,              // Claude 專案 MCP 設定
  /(^|\/)\.bob\/custom_modes\.yaml$/,// Bob custom modes
  /(^|\/)\.claude\/agents\//,        // Claude subagents
];

function readStdin() {
  return new Promise((resolve) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (c) => (data += c));
    process.stdin.on('end', () => resolve(data));
    // 若沒有 stdin（例如手動測試空輸入），給個保險
    setTimeout(() => resolve(data), 2000).unref?.();
  });
}

(async () => {
  try {
    const raw = await readStdin();
    if (!raw.trim()) return process.exit(0);

    const payload = JSON.parse(raw);
    const input = payload.tool_input || {};
    // Edit/Write/MultiEdit 都用 file_path；保險也看 path
    const filePath = input.file_path || input.path || '';
    if (!filePath) return process.exit(0);

    const norm = String(filePath).replace(/\\/g, '/');
    const hit = PATTERNS.some((re) => re.test(norm));
    if (!hit) return process.exit(0);

    const reminder =
      `偵測到同步相關檔案被修改：${norm}\n` +
      `這份檔案屬於跨 AI 工具（Claude / Bob / Cline·Copilot）的同步面。` +
      `請考慮委派給 ai-doc-sync subagent 檢查其他工具的對應文件/設定是否需要同步` +
      `（預設 dry-run 只報告差異）。若你本身就是 ai-doc-sync，請忽略此提醒以免遞迴。`;

    process.stdout.write(
      JSON.stringify({
        hookSpecificOutput: {
          hookEventName: 'PostToolUse',
          additionalContext: reminder,
        },
        suppressOutput: true,
      })
    );
    process.exit(0);
  } catch (_e) {
    // 任何解析錯誤都安靜略過，絕不阻擋編輯
    process.exit(0);
  }
})();
