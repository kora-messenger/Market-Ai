"use strict";
const $ = id => document.getElementById(id);
const state = { email: "", tab: "opinions", opinions: [], bugs: [] };
const show = (node, visible) => { node.hidden = !visible; };
function status(id, message, error = false) {
  const node = $(id);
  node.textContent = message;
  node.classList.toggle("error", error);
}
async function api(path, options = {}) {
  const response = await fetch(`/api/owner/${path}`, { credentials: "same-origin", cache: "no-store", ...options });
  let body = {};
  try { body = await response.json(); } catch (_ignored) {}
  if (!response.ok) {
    if (response.status === 401 && path !== "verify-code" && path !== "request-code") showLogin();
    throw new Error(body.error || `Request failed (${response.status})`);
  }
  return body;
}
function showLogin() {
  show($("login"), true); show($("desk"), false);
  state.opinions = []; state.bugs = [];
  $("list").replaceChildren();
}
async function showDesk() {
  show($("login"), false); show($("desk"), true);
  await reload();
}
async function reload() {
  status("desk-status", "Loading private reports…");
  try {
    const [opinions, bugs] = await Promise.all([api("opinions"), api("bug-reports")]);
    state.opinions = opinions.feedback || [];
    state.bugs = bugs.reports || [];
    $("opinion-count").textContent = String(state.opinions.length);
    $("bug-count").textContent = String(state.bugs.length);
    status("desk-status", ""); render();
  } catch (error) { status("desk-status", error.message, true); }
}
function element(tag, className, text) {
  const el = document.createElement(tag);
  if (className) el.className = className;
  if (text !== undefined) el.textContent = String(text);
  return el;
}
function openMedia(url, kind) {
  const stage = $("media-stage"); stage.replaceChildren();
  const media = element(kind === "video" ? "video" : "img");
  media.src = url;
  media.referrerPolicy = "no-referrer";
  if (kind === "video") { media.controls = true; media.autoplay = false; media.playsInline = true; }
  else media.alt = "Private report attachment";
  stage.append(media);
  $("media-caption").textContent = kind === "video" ? "Screen recording" : "Screenshot";
  $("media-dialog").showModal();
}
function closeMedia() {
  $("media-dialog").close();
  $("media-stage").replaceChildren(); // stops playback and removes signed links
}
function render() {
  const list = $("list"); list.replaceChildren();
  const query = $("search").value.toLowerCase().trim();
  const isBug = state.tab === "bugs";
  const items = (isBug ? state.bugs : state.opinions).filter(item =>
    [item.email, item.message, item.description, item.deviceModel, item.appVersion]
      .some(value => String(value || "").toLowerCase().includes(query))
  );
  if (!items.length) {
    list.append(element("div", "empty", query ? "No matching reports." : `No ${isBug ? "bug reports" : "opinions"} yet.`));
    return;
  }
  for (const item of items) {
    const card = element("article", "entry");
    const head = element("div", "entry-head");
    head.append(element("strong", "", item.email || "Member"));
    const time = element("time", "", item.createdAt ? new Date(item.createdAt).toLocaleString() : "");
    if (item.createdAt) time.dateTime = item.createdAt;
    head.append(time); card.append(head);
    card.append(element("p", "message", isBug ? item.description : item.message));
    if (isBug) {
      const details = [item.appVersion && `App ${item.appVersion}`, item.deviceModel,
        item.androidVersion && `Android ${item.androidVersion}`].filter(Boolean);
      if (details.length) card.append(element("p", "meta", details.join(" · ")));
    }
    const media = element("div", "media-grid");
    const attachments = isBug ? (item.attachments || []) : (item.images || []).map(x => ({ kind: "image", ...x }));
    for (const attachment of attachments) {
      const url = attachment.url || attachment.dataUrl;
      if (!url || !/^(https:\/\/|data:image\/|data:video\/)/.test(url)) continue;
      const video = attachment.kind === "video";
      const button = element("button", video ? "video-button" : "");
      button.type = "button";
      if (video) button.textContent = "Play screen recording";
      else {
        const img = element("img"); img.src = url; img.alt = "Open screenshot";
        img.loading = "lazy"; img.referrerPolicy = "no-referrer";
        button.append(img);
      }
      button.addEventListener("click", () => openMedia(url, video ? "video" : "image"));
      media.append(button);
    }
    if (media.childElementCount) card.append(media);
    list.append(card);
  }
}
$("email-form").addEventListener("submit", async event => {
  event.preventDefault();
  const button = $("email-form").querySelector("button[type=submit]");
  button.disabled = true; state.email = $("email").value.trim().toLowerCase();
  status("login-status", "Sending code…");
  try {
    const result = await api("request-code", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: state.email }) });
    status("login-status", result.message || "Check your inbox.");
    show($("email-form"), false); show($("code-form"), true); $("code").focus();
  } catch (error) { status("login-status", error.message, true); }
  finally { button.disabled = false; }
});
$("code-form").addEventListener("submit", async event => {
  event.preventDefault();
  const button = $("code-form").querySelector("button[type=submit]"); button.disabled = true;
  status("login-status", "Verifying…");
  try {
    await api("verify-code", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: state.email, code: $("code").value.trim() }) });
    $("code").value = ""; status("login-status", ""); await showDesk();
  } catch (error) { status("login-status", error.message, true); }
  finally { button.disabled = false; }
});
$("change-email").addEventListener("click", () => {
  show($("code-form"), false); show($("email-form"), true); status("login-status", "");
});
$("logout").addEventListener("click", async () => {
  try { await api("logout", { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" }); }
  finally { closeMediaIfOpen(); showLogin(); }
});
function closeMediaIfOpen() { if ($("media-dialog").open) closeMedia(); }
$("refresh").addEventListener("click", reload);
$("search").addEventListener("input", render);
for (const [name, id] of [["opinions", "tab-opinions"], ["bugs", "tab-bugs"]]) {
  $(id).addEventListener("click", () => {
    state.tab = name;
    for (const [n, tabId] of [["opinions", "tab-opinions"], ["bugs", "tab-bugs"]]) {
      $(tabId).classList.toggle("selected", n === name);
      $(tabId).setAttribute("aria-selected", String(n === name));
    }
    render();
  });
}
$("close-media").addEventListener("click", closeMedia);
$("media-dialog").addEventListener("close", () => $("media-stage").replaceChildren());
api("me").then(result => { state.email = result.email; showDesk(); }).catch(() => showLogin());
