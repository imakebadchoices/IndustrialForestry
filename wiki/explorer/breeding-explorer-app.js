/* Breeding Tree Explorer
   Renders Mermaid flowcharts from mutation data, with pan/zoom and clickable
   species details. Self-contained: DATA is inlined by the wiki build. */

/* Mermaid's default guards (500 edges, 50k chars) reject the larger trees — the
   full bee graph alone is ~290 mutations. Raise them so every view renders. */
mermaid.initialize({
  startOnLoad: false,
  securityLevel: 'loose',
  maxEdges: 5000,
  maxTextSize: 500000,
  flowchart: { curve: 'basis', nodeSpacing: 30, rankSpacing: 55 },
});

const CATEGORIES = ['bees', 'trees'];
const MIN_SCALE = 0.02, MAX_SCALE = 10;   // zoom bounds (manual)
const FIT_MAX_SCALE = 1.5;                // don't over-enlarge tiny graphs on fit
const RECIPE_CAP = 4;                     // lineage: recipes explored per species

const state = { cat: 'bees', focusMode: 'all', focusValue: '', resourceBee: '' };
let scale = 1, tx = 0, ty = 0;
let renderToken = 0;                       // guards against out-of-order async renders

const $ = s => document.querySelector(s);
const canvas = $('#canvas'), viewport = $('#viewport');

/* ---- data helpers ---- */
const data = () => DATA[state.cat];
const nid = x => 'n_' + x.replace(/[^A-Za-z0-9]/g, '_');
const nodeById = id => data().nodes.find(n => n.id === id);
const label = id => { const n = nodeById(id); return n ? n.label : id.replace(/_/g, ' '); };

/* ---- mutation selection ---- */

/* Which mutations to draw for the current focus filter. */
function selectedMuts() {
  const muts = data().muts;
  switch (state.focusMode) {
    case 'genus': {
      // a mutation belongs to the genus if its result does (parents may be outside)
      const inGenus = id => { const n = nodeById(id); return n && n.genus === state.focusValue; };
      return muts.filter(m => inGenus(m.r));
    }
    case 'lineage':
      return state.focusValue ? lineageMuts(state.focusValue) : [];
    case 'resource':
      // the side panel lists producers; the graph shows the chosen one's tree
      return state.resourceBee ? lineageMuts(state.resourceBee) : [];
    default:
      return muts; // 'all'
  }
}

/* Full breeding neighbourhood of a species: ancestors (how to breed it) and
   descendants (what it leads to). Shared by 'lineage' and 'resource' modes. */
function lineageMuts(target) {
  const muts = data().muts;
  const byResult = {}, byParent = {};
  muts.forEach(m => {
    (byResult[m.r] = byResult[m.r] || []).push(m);
    (byParent[m.a] = byParent[m.a] || []).push(m);
    (byParent[m.b] = byParent[m.b] || []).push(m);
  });
  const keep = new Set();
  (function up(id, seen) {
    (byResult[id] || []).slice(0, RECIPE_CAP).forEach(m => {
      keep.add(m);
      [m.a, m.b].forEach(p => { if (!seen.has(p)) { seen.add(p); up(p, seen); } });
    });
  })(target, new Set([target]));
  (function down(id, seen) {
    (byParent[id] || []).forEach(m => {
      keep.add(m);
      if (!seen.has(m.r)) { seen.add(m.r); down(m.r, seen); }
    });
  })(target, new Set([target]));
  return muts.filter(m => keep.has(m));
}

/* ---- Mermaid source ---- */

/* Each mutation renders as "parentA --- junction, parentB --- junction,
   junction ==>|chance| result" so that "A + B = C" reads clearly. */
function buildMermaid(muts) {
  const lines = ['flowchart LR'];
  const declared = new Set();
  const used = new Set();
  const decl = id => {
    if (declared.has(id)) return;
    declared.add(id);
    lines.push(`  ${nid(id)}["${label(id).replace(/"/g, '')}"]`);
  };
  muts.forEach((m, i) => {
    [m.a, m.b, m.r].forEach(id => { decl(id); used.add(id); });
    const jn = `j${i}`;
    lines.push(`  ${jn}(( ))`);
    lines.push(`  ${nid(m.a)} --- ${jn}`);
    lines.push(`  ${nid(m.b)} --- ${jn}`);
    lines.push(`  ${jn} ==>|${m.c}%| ${nid(m.r)}`);
    lines.push(`  class ${jn} junction`);
  });
  // colour base species (never a mutation result) apart from bred ones
  const results = new Set(muts.map(m => m.r));
  used.forEach(id => lines.push(`  class ${nid(id)} ${results.has(id) ? 'bred' : 'base'}`));
  used.forEach(id => lines.push(`  click ${nid(id)} nodeClick`));
  lines.push('classDef base fill:#e4ecdd,stroke:#008800,stroke-width:2px,color:#333;');
  lines.push('classDef bred fill:#fff,stroke:#4881cf,color:#333;');
  lines.push('classDef junction fill:#8cacbb,stroke:#6d8b99,color:#8cacbb,width:8px,height:8px;');
  return lines.join('\n');
}

function showMessage(html) { canvas.innerHTML = `<div class="msg">${html}</div>`; }

async function render() {
  const muts = selectedMuts();
  const token = ++renderToken;
  $('#status').textContent = `${state.cat} · ${muts.length} mutations`;
  if (!muts.length) { showMessage('No mutations to show for this selection.'); return; }

  // Large graphs lay out synchronously and can take a moment — paint a
  // placeholder first so the viewer never looks frozen.
  if (muts.length > 120) {
    showMessage(`Rendering ${muts.length} mutations…`);
    await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
    if (token !== renderToken) return;
  }

  try {
    const { svg } = await mermaid.render('graph_' + token, buildMermaid(muts));
    if (token !== renderToken) return;   // a newer render superseded this one
    canvas.innerHTML = svg;
    bindNodeClicks();
    fitToView();
  } catch (e) {
    if (token !== renderToken) return;
    showMessage('Could not render this view: ' + e.message);
    console.error(e);
  }
}

/* ---- node interaction ---- */

/* window-scoped so Mermaid's `click` directive can reach it. */
window.nodeClick = function (nodeId) {
  const real = data().nodes.find(n => nid(n.id) === nodeId);
  if (real) showDetail(real.id);
};

/* Fallback binding: match the sanitized id Mermaid writes into each node's DOM id. */
function bindNodeClicks() {
  canvas.querySelectorAll('svg .node').forEach(g => {
    g.style.cursor = 'pointer';
    g.addEventListener('click', ev => {
      ev.stopPropagation();
      const m = (g.id || '').match(/(n_[A-Za-z0-9_]+?)(-\d+)?$/);
      let real = m && data().nodes.find(n => nid(n.id) === m[1]);
      if (!real) {
        const txt = (g.textContent || '').trim();
        real = data().nodes.find(n => n.label === txt);
      }
      if (real) showDetail(real.id);
    });
  });
}

function showDetail(id) {
  const n = nodeById(id);
  if (!n) return;
  $('#dTitle').textContent = n.label;
  const muts = data().muts;
  const recipes = muts.filter(m => m.r === id);
  const usedIn = muts.filter(m => m.a === id || m.b === id);
  let h = '';
  if (n.genus) h += `<div class="row"><span class="k">Genus:</span> ${n.genus}</div>`;
  if (n.climate && n.climate !== 'any') h += `<div class="row"><span class="k">Climate:</span> ${n.climate}</div>`;
  if (n.products) h += `<div class="row"><span class="k">Products:</span> ${n.products}</div>`;
  if (n.wood) h += `<div class="row"><span class="k">Wood:</span> ${n.wood}</div>`;
  const bp = (data().beeProducts || {})[id];
  if (bp && bp.resources && bp.resources.length) {
    h += `<div class="sec"><b>Yields</b><div class="mut">${bp.resources.join(', ')}</div></div>`;
  }
  if (recipes.length) {
    h += '<div class="sec"><b>Bred from</b>';
    recipes.forEach(m => h += `<div class="mut"><a data-id="${m.a}">${label(m.a)}</a> + <a data-id="${m.b}">${label(m.b)}</a> → ${m.c}%</div>`);
    h += '</div>';
  } else {
    h += '<div class="sec"><b>Base species</b><div class="mut">Found in the wild — not bred from other species.</div></div>';
  }
  if (usedIn.length) {
    h += '<div class="sec"><b>Used to breed</b>';
    usedIn.forEach(m => {
      const other = m.a === id ? m.b : m.a;
      h += `<div class="mut">+ <a data-id="${other}">${label(other)}</a> → <a data-id="${m.r}">${label(m.r)}</a> (${m.c}%)</div>`;
    });
    h += '</div>';
  }
  $('#dBody').innerHTML = h;
  $('#dBody').querySelectorAll('a[data-id]').forEach(a =>
    a.addEventListener('click', () => showDetail(a.dataset.id)));
  $('#detail').classList.add('show');
}

/* ---- pan / zoom ---- */
const clampScale = s => Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
function applyTransform() { canvas.style.transform = `translate(${tx}px,${ty}px) scale(${scale})`; }

/* Zoom keeping the point under (px,py) — viewport-relative — fixed. */
function zoomAt(px, py, factor) {
  const ns = clampScale(scale * factor);
  tx = px - (px - tx) * (ns / scale);
  ty = py - (py - ty) * (ns / scale);
  scale = ns;
  applyTransform();
}

function fitToView() {
  const svg = canvas.querySelector('svg');
  if (!svg) return;
  const rect = svg.getBoundingClientRect();
  const gW = rect.width / scale, gH = rect.height / scale;   // natural (unscaled) size
  if (!gW || !gH) return;
  const vpW = viewport.clientWidth, vpH = viewport.clientHeight;
  scale = Math.max(MIN_SCALE, Math.min(vpW / gW, vpH / gH) * 0.92, 0);
  scale = Math.min(scale, FIT_MAX_SCALE);
  tx = (vpW - gW * scale) / 2;
  ty = (vpH - gH * scale) / 2;
  applyTransform();
}

let dragging = false, dsx = 0, dsy = 0;
viewport.addEventListener('mousedown', e => { dragging = true; dsx = e.clientX - tx; dsy = e.clientY - ty; viewport.classList.add('dragging'); });
window.addEventListener('mousemove', e => { if (dragging) { tx = e.clientX - dsx; ty = e.clientY - dsy; applyTransform(); } });
window.addEventListener('mouseup', () => { dragging = false; viewport.classList.remove('dragging'); });
viewport.addEventListener('wheel', e => {
  e.preventDefault();
  const rect = viewport.getBoundingClientRect();
  zoomAt(e.clientX - rect.left, e.clientY - rect.top, e.deltaY < 0 ? 1.12 : 1 / 1.12);
}, { passive: false });

/* ---- touch: one-finger pan, two-finger pinch ---- */
let touchStart = null, pinchStart = null;
const dist = (a, b) => Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY);
const mid = (a, b, rect) => ({ x: (a.clientX + b.clientX) / 2 - rect.left, y: (a.clientY + b.clientY) / 2 - rect.top });
viewport.addEventListener('touchstart', e => {
  if (e.target.closest('.node')) return; // let node taps through for details
  if (e.touches.length === 1) {
    touchStart = { x: e.touches[0].clientX - tx, y: e.touches[0].clientY - ty }; pinchStart = null;
  } else if (e.touches.length === 2) {
    const rect = viewport.getBoundingClientRect();
    pinchStart = { d: dist(e.touches[0], e.touches[1]), s: scale, c: mid(e.touches[0], e.touches[1], rect), tx, ty };
    touchStart = null;
  }
}, { passive: false });
viewport.addEventListener('touchmove', e => {
  if (e.touches.length === 1 && touchStart) {
    e.preventDefault();
    tx = e.touches[0].clientX - touchStart.x; ty = e.touches[0].clientY - touchStart.y; applyTransform();
  } else if (e.touches.length === 2 && pinchStart) {
    e.preventDefault();
    const ns = clampScale(pinchStart.s * (dist(e.touches[0], e.touches[1]) / pinchStart.d));
    const c = pinchStart.c;
    tx = c.x - (c.x - pinchStart.tx) * (ns / pinchStart.s);
    ty = c.y - (c.y - pinchStart.ty) * (ns / pinchStart.s);
    scale = ns; applyTransform();
  }
}, { passive: false });
viewport.addEventListener('touchend', e => {
  if (e.touches.length === 0) { touchStart = null; pinchStart = null; }
  else if (e.touches.length === 1) { touchStart = { x: e.touches[0].clientX - tx, y: e.touches[0].clientY - ty }; pinchStart = null; }
}, { passive: false });

const zoomCentre = factor => zoomAt(viewport.clientWidth / 2, viewport.clientHeight / 2, factor);
$('#zoomIn').onclick = () => zoomCentre(1.25);
$('#zoomOut').onclick = () => zoomCentre(1 / 1.25);
$('#zoomFit').onclick = fitToView;
$('#reset').onclick = () => { $('#detail').classList.remove('show'); fitToView(); };
$('#dClose').onclick = () => $('#detail').classList.remove('show');

/* ---- filter controls ---- */
function populateFocus() {
  const sel = $('#focusValue');
  $('#resPanel').classList.remove('show');
  state.resourceBee = '';
  if (state.focusMode === 'all') { sel.style.display = 'none'; state.focusValue = ''; return; }
  sel.style.display = '';
  let opts;
  if (state.focusMode === 'genus') opts = data().genera;
  else if (state.focusMode === 'resource') opts = Object.keys(data().resourceIndex || {});
  else opts = data().nodes.map(n => n.id).sort((a, b) => label(a).localeCompare(label(b)));
  const text = o => (state.focusMode === 'genus' || state.focusMode === 'resource') ? o : label(o);
  sel.innerHTML = opts.map(o => `<option value="${o}">${text(o)}</option>`).join('');
  state.focusValue = opts[0] || '';
  if (state.focusMode === 'resource') showResourcePanel();
}

/* Resource mode: list species yielding the chosen resource; each opens its tree. */
function showResourcePanel() {
  const bees = (data().resourceIndex || {})[state.focusValue] || [];
  $('#resTitle').textContent = state.focusValue;
  let h = `<div class="note">${bees.length} species produce this resource (directly or via centrifuge). Pick one to see how to breed it:</div>`;
  bees.forEach(b => {
    const node = data().nodes.find(n => n.label === b || n.id === b.toUpperCase());
    h += `<a class="beelink" data-id="${node ? node.id : b.toUpperCase()}">${b} &rarr;</a>`;
  });
  if (!bees.length) h += '<div class="note">No species produce this in the current data.</div>';
  $('#resBody').innerHTML = h;
  $('#resBody').querySelectorAll('.beelink').forEach(a =>
    a.addEventListener('click', () => { state.resourceBee = a.dataset.id; render(); showDetail(a.dataset.id); }));
  $('#resPanel').classList.add('show');
}

$('#focusMode').addEventListener('change', e => { state.focusMode = e.target.value; populateFocus(); render(); });
$('#focusValue').addEventListener('change', e => {
  state.focusValue = e.target.value;
  if (state.focusMode === 'resource') { state.resourceBee = ''; showResourcePanel(); }
  render();
});
$('#tabs').addEventListener('click', e => {
  if (e.target.tagName !== 'BUTTON') return;
  document.querySelectorAll('#tabs button').forEach(b => b.classList.remove('active'));
  e.target.classList.add('active');
  selectCategory(e.target.dataset.cat);
  render();
});

/* Switch category and reset the filter to a sensible default for it. */
function selectCategory(cat) {
  state.cat = cat;
  // bees have a large 'all' graph, so default them to per-genus; the smaller
  // tree/butterfly graphs open on 'all'.
  state.focusMode = cat === 'bees' ? 'genus' : 'all';
  $('#focusMode').value = state.focusMode;
  $('#detail').classList.remove('show');
  $('#resPanel').classList.remove('show');
  const resOpt = $('#focusMode').querySelector('option[value=resource]');
  if (resOpt) resOpt.style.display = Object.keys(data().resourceIndex || {}).length ? '' : 'none';
  populateFocus();
}

/* ---- init: honour a #bees / #trees / #butterflies deep link ---- */
const hashCat = location.hash.replace('#', '');
selectCategory(CATEGORIES.includes(hashCat) ? hashCat : 'bees');
document.querySelectorAll('#tabs button').forEach(b => b.classList.toggle('active', b.dataset.cat === state.cat));
render();
