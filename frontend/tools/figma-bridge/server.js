#!/usr/bin/env node

/**
 * ============================================================================
 * 檔案名稱: server.js
 * 所屬模組: Figma MCP 自動化橋接伺服器 (Figma Bridge MCP Server)
 * 責任描述: 提供標準 MCP Stdio 介面，並於背後啟動 WebSocket 伺服器 (ws://127.0.0.1:8765)，
 *           無縫轉發 AI Agent 的繪圖與查詢指令至 Figma 桌面版，實現零手動、全自動雙向閉環。
 * 架構分層: Tool Layer / Infrastructure Layer
 * ============================================================================
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import { WebSocketServer } from "ws";

const WS_PORT = 8765;
let activeSocket = null;
const pendingRequests = new Map();
let reqCounter = 0;

// 自動釋放埠號防護：若有殘留的孤兒進程佔用 8765，先行強制清理
import { execSync } from "child_process";
try {
  const pids = execSync(`lsof -ti :${WS_PORT}`, { encoding: "utf-8" })
    .trim()
    .split("\n")
    .map((p) => p.trim())
    .filter((p) => p && p !== String(process.pid));
  if (pids.length > 0) {
    execSync(`kill -9 ${pids.join(" ")} 2>/dev/null || true`);
  }
} catch (e) {
  // 無佔用時 lsof 會返回非 0，屬正常行為
}

import http from "http";

// 1. 啟動 HTTP 伺服器並託管 WebSocketServer，確保 EADDRINUSE 絕不拋出未捕獲例外
const httpServer = http.createServer();
const wss = new WebSocketServer({ server: httpServer });

httpServer.on("error", (err) => {
  if (err.code === "EADDRINUSE") {
    console.error(`[Figma MCP] 警告: 埠號 ${WS_PORT} 目前已被既有進程佔用。`);
  } else {
    console.error(`[Figma MCP] 伺服器錯誤:`, err.message);
  }
});

httpServer.listen(WS_PORT, () => {
  console.error(`[Figma MCP] WebSocket 伺服器成功啟動於埠號: ${WS_PORT}`);
});

wss.on("connection", (ws) => {
  activeSocket = ws;
  console.error(`[Figma MCP] Figma 畫布外掛已成功連線至本機 WebSocket (埠號: ${WS_PORT})`);

  ws.on("message", (data) => {
    try {
      const msg = JSON.parse(data.toString());
      if (msg.id && pendingRequests.has(msg.id)) {
        const { resolve, reject } = pendingRequests.get(msg.id);
        pendingRequests.delete(msg.id);
        if (msg.error) {
          reject(new Error(msg.error));
        } else {
          resolve(msg.result);
        }
      }
    } catch (e) {
      console.error("[Figma MCP] 解析外掛訊息異常:", e);
    }
  });

  ws.on("close", () => {
    if (activeSocket === ws) activeSocket = null;
    console.error("[Figma MCP] Figma 畫布外掛已中斷連線");
  });
});

function sendToFigma(action, payload = {}) {
  return new Promise((resolve, reject) => {
    if (!activeSocket || activeSocket.readyState !== 1) {
      return reject(
        new Error(
          "【未連線】尚未偵測到 Figma 外掛連線！請在 Figma 桌面版開啟「Antigravity Bridge」外掛保持連線。"
        )
      );
    }

    const id = ++reqCounter;
    pendingRequests.set(id, { resolve, reject });

    // 逾時防護 (60 秒)
    setTimeout(() => {
      if (pendingRequests.has(id)) {
        pendingRequests.delete(id);
        reject(new Error("【操作逾時】Figma 畫布執行超過 60 秒未回應。"));
      }
    }, 60000);

    activeSocket.send(JSON.stringify({ id, action, payload }));
  });
}

// 2. 初始化 MCP 伺服器
const server = new Server(
  {
    name: "antigravity-figma-bridge",
    version: "1.0.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

// 註冊可用工具清單
server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: "check_figma_connection",
        description: "檢查 Figma 桌面版外掛是否已正常連線至本機橋接伺服器",
        inputSchema: {
          type: "object",
          properties: {},
        },
      },
      {
        name: "build_surveyflow_wireframes",
        description:
          "在當前開啟的 Figma 畫布全自動建立 SurveyFlow 三大標準 Page 與 5 大核心頁面 Wireframe (1440x900) 及 UX 決策註解",
        inputSchema: {
          type: "object",
          properties: {},
        },
      },
      {
        name: "execute_canvas_script",
        description:
          "在 Figma 畫布環境執行自定義 JavaScript 腳本以動態增刪改查元件、頁面、Auto Layout 或圖層節點",
        inputSchema: {
          type: "object",
          properties: {
            code: {
              type: "string",
              description: "要在 Figma 畫布沙盒中執行的原生 JavaScript 程式碼",
            },
            filePath: {
              type: "string",
              description: "本機腳本檔案之絕對路徑 (若提供將直接讀取其內容並於 Figma 執行)",
            },
          },
        },
      },
      {
        name: "get_canvas_structure",
        description: "取得目前 Figma 檔案的所有 Page 名稱與最頂層 Frame 列表結構",
        inputSchema: {
          type: "object",
          properties: {},
        },
      },
    ],
  };
});

// 處理工具調用
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  try {
    if (name === "check_figma_connection") {
      const isConnected = !!(activeSocket && activeSocket.readyState === 1);
      return {
        content: [
          {
            type: "text",
            text: isConnected
              ? "🟢 Figma 桌面外掛已成功連線！可隨時執行自動化畫布繪製與更新。"
              : "🔴 Figma 桌面外掛目前未連線。請於 Figma 桌面版開啟「Antigravity Bridge」外掛。",
          },
        ],
      };
    }

    if (name === "build_surveyflow_wireframes") {
      const result = await sendToFigma("BUILD_SURVEYFLOW");
      return {
        content: [
          {
            type: "text",
            text: `🎉 成功！${result.message || "SurveyFlow 5 大頁面 Wireframe 已全量自動建構完畢！"}`,
          },
        ],
      };
    }

    if (name === "execute_canvas_script") {
      let scriptCode = args?.code;
      if (args?.filePath) {
        const fs = await import("fs");
        scriptCode = fs.readFileSync(args.filePath, "utf-8");
      }
      if (!scriptCode) {
        throw new Error("請提供 code 程式碼字串或 filePath 檔案路徑！");
      }
      const result = await sendToFigma("EXECUTE_SCRIPT", { code: scriptCode });
      return {
        content: [
          {
            type: "text",
            text: `✅ 腳本執行完成，回傳結果: ${JSON.stringify(result)}`,
          },
        ],
      };
    }

    if (name === "get_canvas_structure") {
      const result = await sendToFigma("GET_STRUCTURE");
      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(result, null, 2),
          },
        ],
      };
    }

    throw new Error(`未知的工具名稱: ${name}`);
  } catch (error) {
    return {
      isError: true,
      content: [
        {
          type: "text",
          text: `❌ 執行失敗: ${error.message}`,
        },
      ],
    };
  }
});

// 啟動 Stdio 傳輸
async function run() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("[Figma MCP] Antigravity Figma Bridge MCP 伺服器已透過 stdio 啟動運行。");
}

run().catch((err) => {
  console.error("[Figma MCP] 伺服器啟動致命錯誤:", err);
  process.exit(1);
});
