// ============================================================================
// 檔案名稱: code.js
// 所屬模組: Antigravity Universal Figma Bridge Plugin
// 責任描述: 常駐於 Figma 畫布沙盒中，接收來自本機 MCP 的繪圖與查詢指令，
//           提供全自動無縫畫布操作與雙向閉環能力。
// ============================================================================

figma.showUI(__html__, { width: 320, height: 110 });

// 輔助函式：載入常用字型
async function loadFonts() {
  try {
    await figma.loadFontAsync({ family: "Inter", style: "Regular" });
    await figma.loadFontAsync({ family: "Inter", style: "Bold" });
    await figma.loadFontAsync({ family: "Inter", style: "Medium" });
  } catch (e) {
    console.warn("Inter font failed", e);
  }
}

// 輔助函式：文字節點建構
function createText(text, size = 14, isBold = false, color = { r: 0.973, g: 0.980, b: 0.988 }) {
  const t = figma.createText();
  t.fontName = { family: "Inter", style: isBold ? "Bold" : "Regular" };
  t.characters = text;
  t.fontSize = size;
  t.fills = [{ type: 'SOLID', color: color }];
  return t;
}

function setSolidFill(node, color, opacity = 1) {
  node.fills = [{ type: 'SOLID', color: color, opacity: opacity }];
}

function setSolidStroke(node, color, weight = 1) {
  node.strokes = [{ type: 'SOLID', color: color }];
  node.strokeWeight = weight;
}

// 核心任務：繪製 SurveyFlow 5 大頁面 Wireframe
async function buildSurveyFlow() {
  await loadFonts();

  const cBlack = { r: 0.012, g: 0.027, b: 0.071 };      // #030712
  const cCard = { r: 0.043, g: 0.059, b: 0.098 };       // #0b0f19
  const cBorder = { r: 0.118, g: 0.161, b: 0.231 };     // #1e293b
  const cCyan = { r: 0.0, g: 0.941, b: 1.0 };           // #00f0ff (Neon Cyan)
  const cText = { r: 0.973, g: 0.980, b: 0.988 };       // #f8fafc
  const cMuted = { r: 0.580, g: 0.639, b: 0.722 };      // #94a3b8
  const cDarkMuted = { r: 0.2, g: 0.25, b: 0.32 };      // #334155
  const cGreen = { r: 0.133, g: 0.773, b: 0.369 };      // #22c55e
  const cSticky = { r: 0.98, g: 0.95, b: 0.75 };
  const cStickyBorder = { r: 0.9, g: 0.8, b: 0.4 };

  const pages = figma.root.children;
  let page1 = pages[0];
  page1.name = "01-Wireframe & Flow";

  let page2 = pages.find(p => p.name === "02-Tokens & Components");
  if (!page2) {
    page2 = figma.createPage();
    page2.name = "02-Tokens & Components";
  }

  let page3 = pages.find(p => p.name === "03-Prototype (RWD)");
  if (!page3) {
    page3 = figma.createPage();
    page3.name = "03-Prototype (RWD)";
  }

  figma.currentPage = page1;

  let startX = 0;
  const gapX = 1600;

  // 1. 訪客問卷大廳 (1440x900)
  const fLobby = figma.createFrame();
  fLobby.name = "01. 訪客問卷大廳 (Lobby Layout)";
  fLobby.resize(1440, 900);
  fLobby.x = startX;
  fLobby.y = 0;
  setSolidFill(fLobby, cBlack);
  setSolidStroke(fLobby, cBorder, 1);
  fLobby.layoutMode = "VERTICAL";
  fLobby.itemSpacing = 0;

  const topBar = figma.createFrame();
  topBar.resize(1440, 64);
  topBar.layoutMode = "HORIZONTAL";
  topBar.layoutAlign = "STRETCH";
  topBar.primaryAxisAlignItems = "SPACE_BETWEEN";
  topBar.counterAxisAlignItems = "CENTER";
  topBar.paddingLeft = 40;
  topBar.paddingRight = 40;
  setSolidFill(topBar, cCard);
  setSolidStroke(topBar, cBorder, 1);
  topBar.appendChild(createText("SURVEY FLOW | 開放式問卷平台", 16, true, cCyan));
  topBar.appendChild(createText("問卷大廳  |  表單管理  |  數據洞察  |  登入", 14, false, cMuted));
  fLobby.appendChild(topBar);

  const lBody = figma.createFrame();
  lBody.layoutMode = "VERTICAL";
  lBody.layoutAlign = "STRETCH";
  lBody.paddingLeft = 80;
  lBody.paddingRight = 80;
  lBody.paddingTop = 40;
  lBody.itemSpacing = 32;
  lBody.fills = [];
  lBody.appendChild(createText("探索全球進行中的深度問卷", 26, true, cText));
  lBody.appendChild(createText("參與學術研究與社群調查，您的意見將轉化為關鍵決策數據", 14, false, cMuted));

  const sBox = figma.createFrame();
  sBox.resize(1280, 48);
  sBox.layoutMode = "HORIZONTAL";
  sBox.layoutAlign = "STRETCH";
  sBox.counterAxisAlignItems = "CENTER";
  sBox.paddingLeft = 20;
  setSolidFill(sBox, cCard);
  setSolidStroke(sBox, cBorder, 1);
  sBox.cornerRadius = 6;
  sBox.appendChild(createText("🔍 輸入問卷名稱或關鍵字快速檢索 (支援 ⌘K 快捷搜尋)", 13, false, cMuted));
  lBody.appendChild(sBox);

  const grid = figma.createFrame();
  grid.layoutMode = "HORIZONTAL";
  grid.layoutAlign = "STRETCH";
  grid.itemSpacing = 24;
  grid.fills = [];
  for (let i = 1; i <= 3; i++) {
    const card = figma.createFrame();
    card.resize(400, 280);
    card.layoutMode = "VERTICAL";
    card.paddingLeft = 24;
    card.paddingRight = 24;
    card.paddingTop = 24;
    card.paddingBottom = 24;
    card.itemSpacing = 16;
    setSolidFill(card, cCard);
    setSolidStroke(card, cBorder, 1);
    card.cornerRadius = 8;
    card.appendChild(createText(i === 1 ? "2026 生成式 AI 工作流程效能調查" : (i === 2 ? "都市微型移動載具習慣反饋" : "遠距工作者孤獨感關聯分析"), 16, true, cText));
    card.appendChild(createText("探討團隊導入自動化協同後，工作時長與決策信任度的實質影響...", 13, false, cMuted));
    const btn = figma.createFrame();
    btn.layoutMode = "HORIZONTAL";
    btn.layoutAlign = "STRETCH";
    btn.primaryAxisAlignItems = "CENTER";
    btn.paddingTop = 10;
    btn.paddingBottom = 10;
    setSolidFill(btn, cCyan);
    btn.cornerRadius = 6;
    btn.appendChild(createText("開始填答 ➔", 13, true, cBlack));
    card.appendChild(btn);
    grid.appendChild(card);
  }
  lBody.appendChild(grid);
  fLobby.appendChild(lBody);

  // 2. 問卷作答器 (1440x900)
  startX += gapX;
  const fRunner = figma.createFrame();
  fRunner.name = "02. 問卷作答器 (Survey Runner Layout)";
  fRunner.resize(1440, 900);
  fRunner.x = startX;
  fRunner.y = 0;
  setSolidFill(fRunner, cBlack);
  setSolidStroke(fRunner, cBorder, 1);
  fRunner.layoutMode = "VERTICAL";
  fRunner.counterAxisAlignItems = "CENTER";
  fRunner.itemSpacing = 40;

  const rTop = figma.createFrame();
  rTop.resize(1440, 64);
  rTop.layoutMode = "HORIZONTAL";
  rTop.layoutAlign = "STRETCH";
  rTop.primaryAxisAlignItems = "SPACE_BETWEEN";
  rTop.counterAxisAlignItems = "CENTER";
  rTop.paddingLeft = 40;
  rTop.paddingRight = 40;
  setSolidFill(rTop, cCard);
  setSolidStroke(rTop, cBorder, 1);
  rTop.appendChild(createText("SURVEY: 2026 生成式 AI 工作流程調查", 14, true, cText));
  rTop.appendChild(createText("進度: 第 3 / 8 題 (38%)", 13, true, cCyan));
  fRunner.appendChild(rTop);

  const rBox = figma.createFrame();
  rBox.resize(820, 680);
  rBox.layoutMode = "VERTICAL";
  rBox.itemSpacing = 28;
  rBox.fills = [];

  const qc = figma.createFrame();
  qc.layoutMode = "VERTICAL";
  qc.layoutAlign = "STRETCH";
  qc.paddingLeft = 32;
  qc.paddingRight = 32;
  qc.paddingTop = 28;
  qc.paddingBottom = 28;
  qc.itemSpacing = 16;
  setSolidFill(qc, cCard);
  setSolidStroke(qc, cCyan, 1.5);
  qc.cornerRadius = 8;
  qc.appendChild(createText("Q3. 您每週使用 AI 輔助撰寫代碼或文件的頻率為何？ *", 16, true, cText));
  ["每日持續使用 (超過 4 小時)", "經常使用 (每週 3-5 次)", "偶爾使用", "極少使用"].forEach((opt, idx) => {
    const o = figma.createFrame();
    o.layoutMode = "HORIZONTAL";
    o.layoutAlign = "STRETCH";
    o.paddingLeft = 16;
    o.paddingTop = 10;
    o.paddingBottom = 10;
    o.cornerRadius = 6;
    setSolidFill(o, idx === 0 ? cDarkMuted : cBlack);
    setSolidStroke(o, idx === 0 ? cCyan : cBorder, 1);
    o.appendChild(createText(`${idx === 0 ? "🔘" : "⚪"}  ${opt}`, 13, idx === 0, idx === 0 ? cCyan : cMuted));
    qc.appendChild(o);
  });
  rBox.appendChild(qc);

  const actRow = figma.createFrame();
  actRow.layoutMode = "HORIZONTAL";
  actRow.layoutAlign = "STRETCH";
  actRow.primaryAxisAlignItems = "SPACE_BETWEEN";
  actRow.fills = [];
  const prev = figma.createFrame();
  prev.paddingLeft = 20; prev.paddingRight = 20; prev.paddingTop = 10; prev.paddingBottom = 10;
  setSolidFill(prev, cCard); prev.cornerRadius = 6; prev.appendChild(createText("⬅ 上一題", 13, false, cMuted));
  actRow.appendChild(prev);
  const next = figma.createFrame();
  next.paddingLeft = 24; next.paddingRight = 24; next.paddingTop = 10; next.paddingBottom = 10;
  setSolidFill(next, cCyan); next.cornerRadius = 6; next.appendChild(createText("下一題 ➔", 13, true, cBlack));
  actRow.appendChild(next);
  rBox.appendChild(actRow);
  fRunner.appendChild(rBox);

  // 3. 提交完成感謝頁 (1440x900)
  startX += gapX;
  const fSuccess = figma.createFrame();
  fSuccess.name = "03. 提交完成感謝頁 (Submission Success)";
  fSuccess.resize(1440, 900);
  fSuccess.x = startX;
  fSuccess.y = 0;
  setSolidFill(fSuccess, cBlack);
  setSolidStroke(fSuccess, cBorder, 1);
  fSuccess.layoutMode = "VERTICAL";
  fSuccess.primaryAxisAlignItems = "CENTER";
  fSuccess.counterAxisAlignItems = "CENTER";

  const vCard = figma.createFrame();
  vCard.resize(560, 480);
  vCard.layoutMode = "VERTICAL";
  vCard.paddingLeft = 40; vCard.paddingRight = 40; vCard.paddingTop = 40; vCard.paddingBottom = 40;
  vCard.counterAxisAlignItems = "CENTER";
  vCard.itemSpacing = 20;
  setSolidFill(vCard, cCard);
  setSolidStroke(vCard, cBorder, 1.5);
  vCard.cornerRadius = 12;
  vCard.appendChild(createText("✓", 36, true, cGreen));
  vCard.appendChild(createText("感謝您的寶貴反饋！", 22, true, cText));
  vCard.appendChild(createText("您的答覆已加密記錄至分析節點，資料已匿名化處理。", 13, false, cMuted));

  const vb = figma.createFrame();
  vb.layoutMode = "VERTICAL";
  vb.layoutAlign = "STRETCH";
  vb.paddingLeft = 16; vb.paddingTop = 12; vb.paddingBottom = 12;
  setSolidFill(vb, cBlack);
  setSolidStroke(vb, cBorder, 1);
  vb.cornerRadius = 6;
  vb.appendChild(createText("填答流水號: SF-2026-981244", 13, true, cCyan));
  vb.appendChild(createText("時間: 2026-10-06 14:50 CST", 12, false, cMuted));
  vCard.appendChild(vb);
  fSuccess.appendChild(vCard);

  // 4. 問卷管理控制台 (1440x900)
  startX += gapX;
  const fAdmin = figma.createFrame();
  fAdmin.name = "04. 問卷管理控制台 (Management Console)";
  fAdmin.resize(1440, 900);
  fAdmin.x = startX;
  fAdmin.y = 0;
  setSolidFill(fAdmin, cBlack);
  setSolidStroke(fAdmin, cBorder, 1);
  fAdmin.layoutMode = "HORIZONTAL";
  fAdmin.itemSpacing = 0;

  const aSide = figma.createFrame();
  aSide.resize(240, 900);
  aSide.layoutMode = "VERTICAL";
  aSide.paddingLeft = 24; aSide.paddingTop = 32; aSide.itemSpacing = 24;
  setSolidFill(aSide, cCard);
  setSolidStroke(aSide, cBorder, 1);
  aSide.appendChild(createText("SURVEY FLOW", 16, true, cCyan));
  aSide.appendChild(createText("📊 數據總覽\n\n📋 問卷管理清單\n\n📝 草稿箱 (2)\n\n⚙️ 系統設定", 13, false, cMuted));
  fAdmin.appendChild(aSide);

  const aMain = figma.createFrame();
  aMain.layoutMode = "VERTICAL";
  aMain.layoutAlign = "STRETCH";
  aMain.paddingLeft = 40; aMain.paddingRight = 40; aMain.paddingTop = 32; aMain.itemSpacing = 24;
  aMain.fills = [];
  aMain.appendChild(createText("問卷專案總覽", 22, true, cText));

  const aTable = figma.createFrame();
  aTable.layoutMode = "VERTICAL";
  aTable.layoutAlign = "STRETCH";
  setSolidFill(aTable, cCard);
  setSolidStroke(aTable, cBorder, 1);
  aTable.cornerRadius = 8;
  for (let r = 1; r <= 5; r++) {
    const tr = figma.createFrame();
    tr.layoutMode = "HORIZONTAL";
    tr.layoutAlign = "STRETCH";
    tr.paddingLeft = 20; tr.paddingTop = 16; tr.paddingBottom = 16;
    tr.itemSpacing = 24;
    setSolidStroke(tr, cBorder, 0.5);
    tr.fills = [];
    tr.appendChild(createText(`問卷專案 #${r}: 2026 數位工作型態調查`, 13, false, cText));
    tr.appendChild(createText(r % 2 === 0 ? "🟢 進行中" : "⚪ 已結案", 12, false, r % 2 === 0 ? cGreen : cMuted));
    tr.appendChild(createText(`${r * 300 + 40} 份`, 13, false, cMuted));
    tr.appendChild(createText("查看分析 | 編輯 | 匯出", 12, true, cCyan));
    aTable.appendChild(tr);
  }
  aMain.appendChild(aTable);
  fAdmin.appendChild(aMain);

  // 5. 數據分析儀表板 (1440x900)
  startX += gapX;
  const fAnalytics = figma.createFrame();
  fAnalytics.name = "05. 數據分析儀表板 (Analytics Dashboard)";
  fAnalytics.resize(1440, 900);
  fAnalytics.x = startX;
  fAnalytics.y = 0;
  setSolidFill(fAnalytics, cBlack);
  setSolidStroke(fAnalytics, cBorder, 1);
  fAnalytics.layoutMode = "VERTICAL";
  fAnalytics.paddingLeft = 40; fAnalytics.paddingRight = 40; fAnalytics.paddingTop = 32; fAnalytics.itemSpacing = 24;

  const aH = figma.createFrame();
  aH.layoutMode = "HORIZONTAL";
  aH.layoutAlign = "STRETCH";
  aH.primaryAxisAlignItems = "SPACE_BETWEEN";
  aH.fills = [];
  aH.appendChild(createText("數據洞察儀表板: 2026 生成式 AI 調查", 20, true, cText));
  aH.appendChild(createText("📥 匯出 Excel (XLSX)  |  📊 統計語法 (SPSS .SPS)", 12, true, cCyan));
  fAnalytics.appendChild(aH);

  const kpis = [
    { title: "總填答數", val: "1,280" },
    { title: "平均時長", val: "4m 12s" },
    { title: "完成率", val: "87.5%" },
    { title: "NPS 推薦", val: "+42" },
    { title: "今日新增", val: "+38" }
  ];
  const kRow = figma.createFrame();
  kRow.layoutMode = "HORIZONTAL";
  kRow.layoutAlign = "STRETCH";
  kRow.itemSpacing = 16;
  kRow.fills = [];
  kpis.forEach(k => {
    const kc = figma.createFrame();
    kc.layoutMode = "VERTICAL";
    kc.layoutAlign = "STRETCH";
    kc.paddingLeft = 20; kc.paddingTop = 16; kc.paddingBottom = 16; kc.itemSpacing = 8;
    setSolidFill(kc, cCard); setSolidStroke(kc, cBorder, 1); kc.cornerRadius = 8;
    kc.appendChild(createText(k.title, 12, false, cMuted));
    kc.appendChild(createText(k.val, 24, true, cCyan));
    kRow.appendChild(kc);
  });
  fAnalytics.appendChild(kRow);
  fAnalytics.appendChild(createText("時序填答量長條圖 (過去 30 天數據走勢) & 受訪者年齡層結構剖析", 14, true, cText));

  // UX 便利貼
  const stickies = [
    { title: "📌 UX 決策 1: 漸進式單題揭露", desc: "避免傳統冗長長表單所致之問卷疲乏，維持認知專注度，將完成率提升至 85% 以上。" },
    { title: "📌 UX 決策 2: 一級全頁分析視圖", desc: "分析非次級附屬彈窗，提供專用全寬視圖，讓使用者在單一螢幕掌握 5 大 KPI 與分佈結構。" },
    { title: "📌 UX 決策 3: Excel 與 SPSS 實體分流", desc: "大眾管理者點擊直出 XLSX；學術與資料分析師點擊獲取 .SPS 語法檔，各取所需。" }
  ];
  stickies.forEach((s, idx) => {
    const st = figma.createFrame();
    st.resize(320, 140);
    st.x = idx * 360;
    st.y = 960;
    st.layoutMode = "VERTICAL";
    st.paddingLeft = 20; st.paddingTop = 20; st.itemSpacing = 8;
    setSolidFill(st, cSticky); setSolidStroke(st, cStickyBorder, 1.5); st.cornerRadius = 8;
    st.appendChild(createText(s.title, 13, true, { r: 0.2, g: 0.15, b: 0.05 }));
    st.appendChild(createText(s.desc, 12, false, { r: 0.35, g: 0.3, b: 0.15 }));
    page1.appendChild(st);
  });

  figma.viewport.scrollAndZoomIntoView([fLobby, fRunner, fSuccess, fAdmin, fAnalytics]);
  figma.notify("🎉 SurveyFlow 5 大頁面 Wireframe 繪製完成！", { timeout: 3000 });
}

// 監聽來自 MCP 伺服器的指令
figma.ui.onmessage = async (msg) => {
  const { id, action, payload } = msg;

  try {
    if (action === "BUILD_SURVEYFLOW") {
      await buildSurveyFlow();
      figma.ui.postMessage({ id, result: { message: "5 大核心頁面 Wireframe 與三大 Page 已成功建立於 Figma 畫布！" } });
    } else if (action === "GET_STRUCTURE") {
      const pages = figma.root.children.map(p => ({
        name: p.name,
        frames: p.children.map(c => ({ name: c.name, type: c.type, id: c.id }))
      }));
      figma.ui.postMessage({ id, result: { pages } });
    } else if (action === "EXECUTE_SCRIPT") {
      const AsyncFunction = Object.getPrototypeOf(async function(){}).constructor;
      const evalFunc = new AsyncFunction("figma", payload.code);
      const res = await evalFunc(figma);
      figma.ui.postMessage({ id, result: { output: res } });
    } else {
      figma.ui.postMessage({ id, error: `未知的指令動作: ${action}` });
    }
  } catch (err) {
    console.error("執行指令失敗:", err);
    figma.ui.postMessage({ id, error: err.message || String(err) });
  }
};
