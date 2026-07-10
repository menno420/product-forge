const DATA_URL = "data/mock/mining-character.json";
const SUPPORTED_MAJOR = 1;

function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
  }[c]));
}

function errorPanel(title, detail) {
  return '<div class="panel error"><h2>' + esc(title) + '</h2><p>' + esc(detail) + '</p></div>';
}

function gearChip(slotLabel, item) {
  if (!item) {
    return '<div class="gear-chip empty"><span class="slot">' + esc(slotLabel) + '</span>'
      + '<span class="icon">&middot;</span><span class="name">&mdash; empty &mdash;</span></div>';
  }
  return '<div class="gear-chip rarity-' + esc(item.rarity) + '">'
    + '<span class="slot">' + esc(slotLabel) + '</span>'
    + '<span class="icon">' + esc(item.icon || "?") + '</span>'
    + '<span class="name">' + esc(item.name) + '</span>'
    + (item.power != null ? '<span class="power">&#9889;' + esc(item.power) + '</span>' : '')
    + '</div>';
}

function paperDoll(gear) {
  const slot = (key, label) => gearChip(label, gear[key]);
  return '<section class="panel doll">'
    + '<h2>Gear</h2>'
    + '<div class="doll-grid">'
    +   '<div class="col left">' + slot("head", "Head") + slot("hands", "Hands") + slot("main_hand", "Main Hand") + slot("trinket", "Trinket") + '</div>'
    +   '<div class="col mid"><div class="silhouette">&#129485;</div></div>'
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
      + '<div class="skill-head"><span class="skill-name">' + esc(s.icon || "") + ' ' + esc(s.label) + '</span>'
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
    + '<span class="struct-icon">' + esc(s.icon || "?") + '</span>'
    + '<span class="struct-name">' + esc(s.label) + '</span>'
    + '<span class="struct-tier">T' + esc(s.tier) + '</span>'
    + '<span class="struct-status">' + esc(s.status) + '</span></div>').join("");
  return '<section class="panel structures"><h2>Structures</h2>' + rows + '</section>';
}

function headerPanel(c) {
  return '<section class="panel hero">'
    + '<div class="portrait">' + esc(c.portrait || "?") + '</div>'
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

async function main() {
  const app = document.getElementById("app");
  let state;
  try {
    const res = await fetch(DATA_URL, { cache: "no-store" });
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

document.addEventListener("DOMContentLoaded", main);
