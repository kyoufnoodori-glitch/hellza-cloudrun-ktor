// ====== Config ======
const ASSET_VER = "20260122-2"; // バージョン更新
const API_BASE = "/api";

// ====== DOM ======
const screenPin = document.getElementById("screenPin");
const screenView = document.getElementById("screenView");
const cidText = document.getElementById("cidText");
const pinInput = document.getElementById("pinInput");
const btnPin = document.getElementById("btnPin");
const pinMsg = document.getElementById("pinMsg");
const bgImg = document.getElementById("bgImg");
const ovSvg = document.getElementById("ovSvg");
const svgCid = document.getElementById("svgCid");
const svgPt = document.getElementById("svgPt");
const svgUpd = document.getElementById("svgUpd");
const btnReload = document.getElementById("btnReload");
const viewMsg = document.getElementById("viewMsg");

// ====== 基準座標（画像実寸 2160x1360 を想定して調整）=====
// ★ここが配置のキモです！ズレていたらここをいじってください
const POS = {
  // 右上のID記入欄（開始位置）
  // 予想：xは枠の左端あたり、yは枠の上下中央
  cid: { x: 2000, y: 140 },

  // 真ん中のポイント記入欄（中央位置）
  // 予想：xは枠のど真ん中、yは枠のど真ん中
  pt:  { x: 1080, y: 680 },

  // 更新日時（枠の下あたり）
  upd: { x: 1080, y: 900 }
};

function applySvgViewBox() {
  const w = bgImg.naturalWidth;
  const h = bgImg.naturalHeight;
  if (!w || !h) return;

  ovSvg.setAttribute("viewBox", `0 0 ${w} ${h}`);
  ovSvg.setAttribute("preserveAspectRatio", "none");

  // IDの設定
  svgCid.setAttribute("x", POS.cid.x);
  svgCid.setAttribute("y", POS.cid.y);
  // CSSで text-anchor: start にしたので、x座標から右に文字が書かれます

  // ポイントの設定
  svgPt.setAttribute("x", POS.pt.x);
  svgPt.setAttribute("y", POS.pt.y);
  // CSSで text-anchor: middle にしたので、x座標を中心に文字が広がります

  // 更新日時の設定
  svgUpd.setAttribute("x", POS.upd.x);
  svgUpd.setAttribute("y", POS.upd.y);
  svgUpd.setAttribute("text-anchor", "middle");
}
bgImg.addEventListener("load", applySvgViewBox);

// ====== Helpers ======
function getCidFromPath() {
  const parts = location.pathname.split("/").filter(Boolean);
  const idx = parts.indexOf("c");
  if (idx >= 0 && parts[idx + 1]) return decodeURIComponent(parts[idx + 1]);
  return "";
}

function setPinMessage(text) { pinMsg.textContent = text || ""; }
function setViewMessage(text) { viewMsg.textContent = text || ""; }

function bgByState(state) {
  // ★重要：白い枠が「最初から描いてある」画像を使ってください
  switch (state) {
    case "error":   return `/assets/card_error_2.png?v=${ASSET_VER}`;
    case "success": return `/assets/card_success_3.png?v=${ASSET_VER}`;
    case "minus":   return `/assets/card_minus_2.png?v=${ASSET_VER}`;
    default:        return `/assets/card_normal_2.png?v=${ASSET_VER}`;
  }
}

async function apiGet(url) {
  const r = await fetch(url, { credentials: "include" });
  return { r, json: await safeJson(r) };
}
async function apiPost(url, body) {
  const r = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
    credentials: "include"
  });
  return { r, json: await safeJson(r) };
}
async function safeJson(r) {
  try { return await r.json(); } catch { return null; }
}

function showPin() {
  screenPin.classList.remove("hidden");
  screenView.classList.add("hidden");
}
function showView() {
  screenPin.classList.add("hidden");
  screenView.classList.remove("hidden");
}

function formatUpdated(s) {
  if (!s) return "-";
  return s.replace("T", " ").split(".")[0]; // 秒以下のミリ秒などをカットしてスッキリさせる
}

// ====== Main ======
const cid = getCidFromPath();
cidText.textContent = `CID: ${cid || "-"}`;

// 初期表示
svgCid.textContent = cid || "-"; // "ID:" をつけない
svgPt.textContent = "--";         // "pt" をつけない
svgUpd.textContent = "";

if (!cid) {
  setPinMessage("CIDがURLにありません");
  btnPin.disabled = true;
} else {
  (async () => {
    await loadCardOrAskPin();
  })();
}

btnPin.addEventListener("click", async () => {
  setPinMessage("");
  const pin = (pinInput.value || "").trim();
  if (!pin) { setPinMessage("PINを入力してください"); return; }

  btnPin.disabled = true;
  try {
    const { r, json } = await apiPost(`${API_BASE}/pin`, { cid, pin });
    if (!r.ok) {
      setPinMessage((json && json.message) ? json.message : `NG (${r.status})`);
      return;
    }
    await loadCard();
    showView();
  } finally {
    btnPin.disabled = false;
  }
});

btnReload.addEventListener("click", async () => {
  setViewMessage("");
  btnReload.disabled = true;
  try {
    await loadCardOrAskPin();
  } finally {
    btnReload.disabled = false;
  }
});

async function loadCardOrAskPin() {
  const { r } = await apiGet(`${API_BASE}/card?cid=${encodeURIComponent(cid)}`);
  if (r.status === 401 || r.status === 403) {
    showPin();
    setPinMessage("PINを入力してください");
    return;
  }
  if (!r.ok) {
    showPin();
    setPinMessage(`取得失敗 (${r.status})`);
    return;
  }
  await loadCard();
  showView();
}

async function loadCard() {
  const { r, json } = await apiGet(`${API_BASE}/card?cid=${encodeURIComponent(cid)}`);
  if (!r.ok) {
    setViewMessage(`取得失敗 (${r.status})`);
    return;
  }

  const point = json?.point ?? 0;
  const updatedAt = json?.updatedAt ?? "";
  const state = json?.state ?? "normal";

  bgImg.src = bgByState(state);

  // ★ここ修正：「ID:」や「pt」という文字を入れず、数字だけ渡す
  svgCid.textContent = cid;
  svgPt.textContent = String(point);
  svgUpd.textContent = `最終利用: ${formatUpdated(updatedAt)}`;

  setViewMessage("");
}