const DEFAULT_DATA_URL = "data/mock/mining-character.json";
const SUPPORTED_MAJOR = 1;

function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
  }[c]));
}

// Inline comic-style SVG art, keyed by stable data keys (gear slot, skill key,
// structure key). Dependency-free and developer-authored: these strings are
// trusted constants, so they are injected raw. The mock's emoji `icon` fields
// stay as an honest fallback whenever a key is unmapped (see iconMarkup).
const ART = {
  portrait: `<svg viewBox="0 0 32 32" aria-hidden="true"><circle cx="16" cy="18" r="10" fill="#f0d2a0" stroke="#2b1d0a" stroke-width="1.8"/><path d="M5 17a11 10 0 0 1 22 0z" fill="#c8451f" stroke="#2b1d0a" stroke-width="1.8"/><rect x="4" y="16" width="24" height="3" rx="1.5" fill="#2b1d0a"/><circle cx="16" cy="11" r="2.6" fill="#e0a92e" stroke="#2b1d0a" stroke-width="1.4"/><circle cx="12" cy="20" r="1.2" fill="#2b1d0a"/><circle cx="20" cy="20" r="1.2" fill="#2b1d0a"/><path d="M12 24q4 3 8 0" fill="none" stroke="#2b1d0a" stroke-width="1.4" stroke-linecap="round"/></svg>`,
  miner: `<svg viewBox="0 0 64 104" aria-hidden="true"><path d="M26 66h5v28h-6z" fill="#4f6b8a" stroke="#2b1d0a" stroke-width="2" stroke-linejoin="round"/><path d="M33 66h5l1 28h-6z" fill="#4f6b8a" stroke="#2b1d0a" stroke-width="2" stroke-linejoin="round"/><path d="M22 92h10v6H21z" fill="#6b4a2b" stroke="#2b1d0a" stroke-width="2" stroke-linejoin="round"/><path d="M32 92h10l2 6H32z" fill="#6b4a2b" stroke="#2b1d0a" stroke-width="2" stroke-linejoin="round"/><path d="M22 40l10-4 10 4 2 26H20z" fill="#e0a92e" stroke="#2b1d0a" stroke-width="2" stroke-linejoin="round"/><path d="M32 37v29" stroke="#2b1d0a" stroke-width="1.6"/><rect x="24" y="55" width="16" height="3.5" fill="#c8451f"/><path d="M22 42l-7 14 4 2 7-13z" fill="#e0a92e" stroke="#2b1d0a" stroke-width="2" stroke-linejoin="round"/><path d="M42 42l9 10-3 3-10-9z" fill="#e0a92e" stroke="#2b1d0a" stroke-width="2" stroke-linejoin="round"/><path d="M41 58L55 20" stroke="#8a5a2b" stroke-width="3" stroke-linecap="round"/><path d="M47 22c4-4 9-5 13-4-3 3-5 7-4 12" fill="none" stroke="#9a9a9a" stroke-width="4" stroke-linecap="round"/><circle cx="32" cy="24" r="11" fill="#f0d2a0" stroke="#2b1d0a" stroke-width="2"/><path d="M20 22a12 11 0 0 1 24 0z" fill="#c8451f" stroke="#2b1d0a" stroke-width="2"/><rect x="18" y="21" width="28" height="3.5" rx="1.5" fill="#2b1d0a"/><circle cx="32" cy="15" r="3" fill="#e0a92e" stroke="#2b1d0a" stroke-width="1.6"/><circle cx="28" cy="26" r="1.3" fill="#2b1d0a"/><circle cx="36" cy="26" r="1.3" fill="#2b1d0a"/><path d="M27 33q5 3 10 0" fill="#8a5a2b"/></svg>`,
  gear: {
    head: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M6 20a10 9 0 0 1 20 0z" fill="#c8451f" stroke="#2b1d0a" stroke-width="1.6"/><rect x="4" y="20" width="24" height="3" rx="1.5" fill="#2b1d0a"/><circle cx="16" cy="14" r="3.4" fill="#e0a92e" stroke="#2b1d0a" stroke-width="1.4"/><path d="M16 10.6l-2-4M16 10.6l2-4M12.6 14h-4M23.4 14h-4" stroke="#e0a92e" stroke-width="1.2" stroke-linecap="round"/></svg>`,
    chest: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M10 6l6 3 6-3 4 4-3 4v11H9V14L6 10z" fill="#e0a92e" stroke="#2b1d0a" stroke-width="1.6" stroke-linejoin="round"/><path d="M16 9v20" stroke="#2b1d0a" stroke-width="1.4"/><rect x="11" y="19" width="10" height="2.4" fill="#c8451f"/></svg>`,
    hands: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M11 15V9a2 2 0 0 1 4 0v4m0-1V7a2 2 0 0 1 4 0v6m0-2V9a2 2 0 0 1 4 0v9a7 7 0 0 1-7 7h-2a6 6 0 0 1-6-6v-3a2 2 0 0 1 3-1.7z" fill="#8a5a2b" stroke="#2b1d0a" stroke-width="1.5" stroke-linejoin="round"/></svg>`,
    legs: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M9 6h14v6l-2 14h-4l-1-11-1 11H11L9 12z" fill="#4f6b8a" stroke="#2b1d0a" stroke-width="1.6" stroke-linejoin="round"/><circle cx="13" cy="17" r="2" fill="#2b1d0a"/><circle cx="19" cy="17" r="2" fill="#2b1d0a"/></svg>`,
    feet: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M11 5h5v13l7 3v3H8v-3l3-2z" fill="#6b4a2b" stroke="#2b1d0a" stroke-width="1.6" stroke-linejoin="round"/><rect x="8" y="24" width="15" height="3" rx="1" fill="#2b1d0a"/><path d="M16 18a4 4 0 0 0 5 2" fill="none" stroke="#9a9a9a" stroke-width="2"/></svg>`,
    main_hand: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M5 9c8-3 17-3 22 0" fill="none" stroke="#9a9a9a" stroke-width="3.4" stroke-linecap="round"/><rect x="14.6" y="8" width="2.8" height="19" rx="1.2" fill="#8a5a2b" stroke="#2b1d0a" stroke-width="1.2"/></svg>`,
    off_hand: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M16 4v3M12 7h8" stroke="#2b1d0a" stroke-width="1.6" stroke-linecap="round"/><rect x="11" y="10" width="10" height="14" rx="2" fill="#e0a92e" stroke="#2b1d0a" stroke-width="1.6"/><path d="M16 13c2 2 2 4 0 6-2-2-2-4 0-6z" fill="#c8451f"/><path d="M11 24h10" stroke="#2b1d0a" stroke-width="1.6"/></svg>`,
    trinket: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M8 7a8 8 0 0 1 16 0" fill="none" stroke="#e0a92e" stroke-width="1.6"/><path d="M16 12l5 5-5 8-5-8z" fill="#8a3fb0" stroke="#2b1d0a" stroke-width="1.6" stroke-linejoin="round"/><path d="M11 17h10M16 12v13" stroke="#2b1d0a" stroke-width="1"/></svg>`
  },
  skill: {
    prospecting: `<svg viewBox="0 0 32 32" aria-hidden="true"><circle cx="16" cy="16" r="11" fill="#f3e2b8" stroke="#2b1d0a" stroke-width="1.8"/><path d="M16 8l3 8-3 8-3-8z" fill="#c8451f" stroke="#2b1d0a" stroke-width="1" stroke-linejoin="round"/><path d="M16 24l-3-8 3-8" fill="#fff8e6"/><circle cx="16" cy="16" r="1.6" fill="#2b1d0a"/></svg>`,
    tunneling: `<svg viewBox="0 0 32 32" aria-hidden="true"><rect x="4" y="6" width="24" height="20" rx="1" fill="#8a5a2b" stroke="#2b1d0a" stroke-width="1.6"/><path d="M10 26v-8a6 6 0 0 1 12 0v8z" fill="#2b1d0a"/><path d="M8 11h4M20 11h4M8 15h3M21 15h3" stroke="#2b1d0a" stroke-width="1.2" stroke-linecap="round"/></svg>`,
    smelting: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M16 4c1 4 5 5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 1 3 3 2 3 0 0-3-1-4 0-6z" fill="#c8451f" stroke="#2b1d0a" stroke-width="1.4" stroke-linejoin="round"/><path d="M16 12c1 2 2 3 2 5a2 2 0 0 1-4 0c0-1 1-2 2-5z" fill="#e0a92e"/><rect x="8" y="24" width="16" height="4" rx="1" fill="#2b1d0a"/></svg>`,
    gemcutting: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M10 8h12l5 6-11 12L5 14z" fill="#2f6fb0" stroke="#2b1d0a" stroke-width="1.6" stroke-linejoin="round"/><path d="M10 8l-5 6 11 12z" fill="#4f8fd0"/><path d="M5 14h22M10 8l6 6 6-6M16 14v12" fill="none" stroke="#2b1d0a" stroke-width="1"/></svg>`
  },
  structure: {
    shaft: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M4 26V16l12-8 12 8v10z" fill="#8a5a2b" stroke="#2b1d0a" stroke-width="1.6" stroke-linejoin="round"/><rect x="6" y="12" width="20" height="2" fill="#6b4a2b"/><path d="M11 26v-6a5 5 0 0 1 10 0v6z" fill="#2b1d0a"/></svg>`,
    smelter: `<svg viewBox="0 0 32 32" aria-hidden="true"><rect x="6" y="15" width="20" height="11" fill="#9a9a9a" stroke="#2b1d0a" stroke-width="1.6"/><rect x="9" y="7" width="4" height="8" fill="#6b4a2b" stroke="#2b1d0a" stroke-width="1.4"/><path d="M16 26v-5l4 2.5zM21 26v-5l4 2.5z" fill="#e0a92e" stroke="#2b1d0a" stroke-width="1"/><circle cx="11" cy="5" r="1.6" fill="#9a9a9a" opacity=".6"/></svg>`,
    vault: `<svg viewBox="0 0 32 32" aria-hidden="true"><rect x="5" y="7" width="22" height="18" rx="2" fill="#6b4a2b" stroke="#2b1d0a" stroke-width="1.6"/><rect x="9" y="11" width="14" height="10" rx="1" fill="#8a5a2b" stroke="#2b1d0a" stroke-width="1.4"/><circle cx="16" cy="16" r="2.4" fill="#e0a92e" stroke="#2b1d0a" stroke-width="1.2"/><path d="M16 16v3" stroke="#2b1d0a" stroke-width="1.4"/></svg>`,
    cartline: `<svg viewBox="0 0 32 32" aria-hidden="true"><path d="M6 12h18l-2 8H8z" fill="#6b4a2b" stroke="#2b1d0a" stroke-width="1.6" stroke-linejoin="round"/><path d="M11 12l1-4h6l1 4" fill="#9a9a9a" stroke="#2b1d0a" stroke-width="1.2" stroke-linejoin="round"/><circle cx="11" cy="24" r="2.6" fill="#2b1d0a"/><circle cx="20" cy="24" r="2.6" fill="#2b1d0a"/><path d="M3 27h26" stroke="#2b1d0a" stroke-width="1.6" stroke-linecap="round"/></svg>`
  }
};

function iconMarkup(svg, emoji) {
  // svg: trusted developer-authored constant, injected raw.
  // emoji: comes from data, so it is HTML-escaped.
  return svg ? svg : esc(emoji == null ? "?" : emoji);
}

function errorPanel(title, detail) {
  return '<div class="panel error"><h2>' + esc(title) + '</h2><p>' + esc(detail) + '</p></div>';
}

function gearChip(slotKey, slotLabel, item) {
  if (!item) {
    return '<div class="gear-chip empty"><span class="slot">' + esc(slotLabel) + '</span>'
      + '<span class="icon">&middot;</span><span class="name">&mdash; empty &mdash;</span></div>';
  }
  const tip = esc(item.name) + ' &middot; ' + esc(item.rarity)
    + (item.power != null ? ' &middot; &#9889;' + esc(item.power) : '');
  return '<div class="gear-chip rarity-' + esc(item.rarity) + '" tabindex="0">'
    + '<span class="slot">' + esc(slotLabel) + '</span>'
    + '<span class="icon">' + iconMarkup(ART.gear[slotKey], item.icon) + '</span>'
    + '<span class="name">' + esc(item.name) + '</span>'
    + (item.power != null ? '<span class="power">&#9889;' + esc(item.power) + '</span>' : '')
    + '<span class="chip-tip">' + tip + '</span>'
    + '</div>';
}

function paperDoll(gear) {
  const slot = (key, label) => gearChip(key, label, gear[key]);
  return '<section class="panel doll">'
    + '<h2>Gear</h2>'
    + '<div class="doll-grid">'
    +   '<div class="col left">' + slot("head", "Head") + slot("hands", "Hands") + slot("main_hand", "Main Hand") + slot("trinket", "Trinket") + '</div>'
    +   '<div class="col mid"><div class="silhouette">' + ART.miner + '</div></div>'
    +   '<div class="col right">' + slot("chest", "Chest") + slot("legs", "Legs") + slot("feet", "Feet") + slot("off_hand", "Off Hand") + '</div>'
    + '</div>'
    + '</section>';
}

function statsPanel(stats) {
  const rows = stats.map(s =>
    '<div class="stat-row" title="' + esc(s.hint || "") + '">'
    + '<span class="stat-label">' + esc(s.label) + '</span>'
    + '<span class="stat-value">' + esc(s.value) + '</span></div>').join("");
  return '<section class="panel stats"><h2>Stats</h2>' + rows + '</section>';
}

function skillsPanel(skills) {
  const rows = skills.map(s => {
    const pct = Math.max(0, Math.min(100, Math.round((s.xp / s.xp_max) * 100)));
    return '<div class="skill-row">'
      + '<div class="skill-head"><span class="skill-name"><span class="skill-icon">'
      + iconMarkup(ART.skill[s.key], s.icon) + '</span>' + esc(s.label) + '</span>'
      + '<span class="skill-lvl">Lv ' + esc(s.level) + '</span></div>'
      + '<div class="bar"><div class="bar-fill" style="width:' + pct + '%"></div>'
      + '<span class="bar-text">' + esc(s.xp) + ' / ' + esc(s.xp_max) + ' xp</span></div>'
      + '</div>';
  }).join("");
  return '<section class="panel skills"><h2>Skills</h2>' + rows + '</section>';
}

function structuresPanel(structures) {
  const rows = structures.map(s =>
    '<div class="struct-row status-' + esc(s.status) + '">'
    + '<span class="struct-icon">' + iconMarkup(ART.structure[s.key], s.icon) + '</span>'
    + '<span class="struct-name">' + esc(s.label) + '</span>'
    + '<span class="struct-tier">T' + esc(s.tier) + '</span>'
    + '<span class="struct-status">' + esc(s.status) + '</span></div>').join("");
  return '<section class="panel structures"><h2>Structures</h2>' + rows + '</section>';
}

function headerPanel(c) {
  return '<section class="panel hero">'
    + '<div class="portrait">' + iconMarkup(ART.portrait, c.portrait) + '</div>'
    + '<div class="hero-text">'
    +   '<h1>' + esc(c.name) + '</h1>'
    +   '<div class="hero-sub">Lv ' + esc(c.level) + ' &middot; ' + esc(c.class) + '</div>'
    +   '<div class="hero-title">&ldquo;' + esc(c.title) + '&rdquo;</div>'
    + '</div></section>';
}

function render(app, state) {
  app.innerHTML =
      headerPanel(state.character)
    + paperDoll(state.gear)
    + statsPanel(state.stats)
    + skillsPanel(state.skills)
    + structuresPanel(state.structures);
}

async function loadAndRender(url) {
  const app = document.getElementById("app");
  app.innerHTML = '<p class="loading">Loading character sheet&hellip;</p>';
  let state;
  try {
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) throw new Error("HTTP " + res.status);
    state = await res.json();
  } catch (err) {
    app.innerHTML = errorPanel("Could not load mock game-state",
      err.message + " — serve this folder over HTTP (./run.sh); opening index.html as a file:// URL will not work.");
    return;
  }
  const major = parseInt(String(state.schema_version).split(".")[0], 10);
  if (state.contract !== "games-web.character-sheet" || major !== SUPPORTED_MAJOR) {
    app.innerHTML = errorPanel("Unsupported contract",
      "Got " + state.contract + " v" + state.schema_version
      + "; renderer speaks games-web.character-sheet v" + SUPPORTED_MAJOR + ".x");
    return;
  }
  render(app, state);
}

function main() {
  const select = document.getElementById("char-select");
  const initial = (select && select.value) || DEFAULT_DATA_URL;
  if (select) {
    select.addEventListener("change", () => loadAndRender(select.value));
  }
  loadAndRender(initial);
}

document.addEventListener("DOMContentLoaded", main);
