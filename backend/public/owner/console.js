"use strict";
const $ = id => document.getElementById(id);
const state = { email: "", tab: "opinions", opinions: [], bugs: [], usage: [], usageDetails: null };
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
  state.opinions = []; state.bugs = []; state.usage = []; state.usageDetails = null;
  $("list").replaceChildren();
}
async function showDesk() {
  show($("login"), false); show($("desk"), true);
  await reload();
}
async function reload() {
  status("desk-status", "Loading private reports and usage…");
  try {
    const [opinions, bugs, usage] = await Promise.all([api("opinions"), api("bug-reports"), api(`ai-usage?days=${$("usage-days").value}`)]);
    state.opinions = opinions.feedback || [];
    state.bugs = bugs.reports || [];
    state.usage = usage.daily || []; state.usageDetails = null;
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
function renderUsage(list) {
  if (!state.usage.length) {
    list.append(element("div", "empty", "No recorded AI calls in this period. Historical calls before tracking was deployed cannot be recovered."));
    return;
  }
  for (const row of state.usage) {
    const card = element("article", "entry");
    const head = element("div", "entry-head");
    head.append(element("strong", "", row.email || (row.userId ? "Member" : "System / shared")));
    head.append(element("time", "", `${row.day} · ${row.calls} call${row.calls === 1 ? "" : "s"}`));
    card.append(head);
    if (row.name) card.append(element("p", "meta", row.name));
    card.append(element("p", "message", `${Number(row.inputTokens).toLocaleString()} input · ${Number(row.outputTokens).toLocaleString()} output tokens`));
    if (row.unknownCalls) card.append(element("p", "meta", `${row.unknownCalls} call${row.unknownCalls === 1 ? "" : "s"} lacked provider token counts, so totals are incomplete.`));
    const button = element("button", "button-outline", "View calls");
    button.type = "button";
    button.addEventListener("click", async () => {
      button.disabled = true;
      try {
        const result = await api(`ai-usage/calls?date=${encodeURIComponent(row.day)}&userId=${encodeURIComponent(row.userId || "system")}`);
        const details = element("div", "usage-calls");
        if (result.calls.length === 200) details.append(element("p", "meta", "Showing the latest 200 calls for this day."));
        for (const call of result.calls) {
          const time = new Date(call.completedAt).toLocaleTimeString("en-NG", { timeZone: "Africa/Lagos", hour: "2-digit", minute: "2-digit" });
          details.append(element("p", "meta", `${time} · ${call.feature} · ${call.provider} / ${call.model} · ${call.inputTokens ?? "?"} in / ${call.outputTokens ?? "?"} out`));
        }
        if (!result.calls.length) details.append(element("p", "meta", "No calls found."));
        const previous = card.querySelector(".usage-calls"); if (previous) previous.remove();
        card.append(details);
      } catch (error) { status("desk-status", error.message, true); }
      finally { button.disabled = false; }
    });
    card.append(button); list.append(card);
  }
}
function render() {
  const list = $("list"); list.replaceChildren();
  show($("usage-controls"), state.tab === "usage");
  show($("report-filter"), state.tab !== "usage");
  show($("report-footer"), state.tab !== "usage");
  if (state.tab === "usage") { renderUsage(list); return; }
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
$("usage-days").addEventListener("change", reload);
for (const [name, id] of [["opinions", "tab-opinions"], ["bugs", "tab-bugs"], ["usage", "tab-usage"]]) {
  $(id).addEventListener("click", () => {
    state.tab = name;
    for (const [n, tabId] of [["opinions", "tab-opinions"], ["bugs", "tab-bugs"], ["usage", "tab-usage"]]) {
      $(tabId).classList.toggle("selected", n === name);
      $(tabId).setAttribute("aria-selected", String(n === name));
    }
    render();
  });
}
$("close-media").addEventListener("click", closeMedia);
$("media-dialog").addEventListener("close", () => $("media-stage").replaceChildren());
api("me").then(result => { state.email = result.email; showDesk(); }).catch(() => showLogin());
