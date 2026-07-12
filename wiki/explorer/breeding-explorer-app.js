/* Breeding Tree Explorer — builds Mermaid flowcharts from mutation data,
   with pan/zoom and clickable species details. Self-contained. */
mermaid.initialize({ startOnLoad: false, securityLevel: 'loose', flowchart: { curve: 'basis', nodeSpacing: 30, rankSpacing: 55 } });

let cat = 'bees';
let focusMode = 'all';
let focusValue = '';
let resourceBee = '';  // when in resource mode, the chosen bee whose tree we show
let scale = 1, tx = 0, ty = 0;

const $ = s => document.querySelector(s);
const canvas = $('#canvas'), viewport = $('#viewport');

function nid(x){ return 'n_' + x.replace(/[^A-Za-z0-9]/g,'_'); }
function nodeById(id){ return DATA[cat].nodes.find(n => n.id === id); }
function label(id){ const n = nodeById(id); return n ? n.label : id.replace(/_/g,' '); }

/* Decide which mutations to include based on the current focus filter. */
function selectedMuts(){
  const muts = DATA[cat].muts;
  if (focusMode === 'all') return muts;
  if (focusMode === 'genus'){
    const inGenus = id => { const n = nodeById(id); return n && n.genus === focusValue; };
    // include a mutation if its result is in the genus (parents may be outside)
    return muts.filter(m => inGenus(m.r));
  }
  if (focusMode === 'lineage'){
    if (!focusValue) return [];
    return lineageMuts(focusValue);
  }
  if (focusMode === 'resource'){
    // focusValue is a resource name; if a bee has been chosen show its lineage,
    // otherwise show nothing in the graph (the side panel lists the bees).
    if (!resourceBee) return [];
    return lineageMuts(resourceBee);
  }
  return muts;
}

/* Shared lineage computation used by both 'lineage' and 'resource' modes. */
function lineageMuts(target){
  const muts = DATA[cat].muts;
  const byResult = {}, byParent = {};
  muts.forEach(m => { (byResult[m.r]=byResult[m.r]||[]).push(m);
    (byParent[m.a]=byParent[m.a]||[]).push(m); (byParent[m.b]=byParent[m.b]||[]).push(m); });
  const keepMut = new Set();
  const RECIPE_CAP = 4;
  (function up(id, seen){
    const recipes = (byResult[id]||[]).slice(0, RECIPE_CAP);
    recipes.forEach(m => { keepMut.add(m);
      [m.a, m.b].forEach(p => { if(!seen.has(p)){ seen.add(p); up(p, seen); } }); });
  })(target, new Set([target]));
  (function down(id, seen){
    (byParent[id]||[]).forEach(m => { keepMut.add(m);
      if(!seen.has(m.r)){ seen.add(m.r); down(m.r, seen); } });
  })(target, new Set([target]));
  return muts.filter(m => keepMut.has(m));
}

/* Build Mermaid flowchart source. Each mutation becomes:
   parentA --> junction, parentB --> junction, junction -->|chance| result.
   The junction is a tiny circle so "A + B = C" reads clearly. */
function buildMermaid(muts){
  const lines = ['flowchart LR'];
  const declared = new Set();
  const decl = id => {
    if (declared.has(id)) return;
    declared.add(id);
    const safe = label(id).replace(/"/g,'');
    lines.push(`  ${nid(id)}["${safe}"]`);
  };
  let j = 0;
  const usedNodes = new Set();
  muts.forEach(m => {
    decl(m.a); decl(m.b); decl(m.r);
    usedNodes.add(m.a); usedNodes.add(m.b); usedNodes.add(m.r);
    const jn = `j${j++}`;
    lines.push(`  ${jn}(( ))`);
    lines.push(`  ${nid(m.a)} --- ${jn}`);
    lines.push(`  ${nid(m.b)} --- ${jn}`);
    lines.push(`  ${jn} ==>|${m.c}%| ${nid(m.r)}`);
    lines.push(`  class ${jn} junction`);
  });
  // color base species (no incoming mutation) vs bred species
  const results = new Set(muts.map(m => m.r));
  usedNodes.forEach(id => {
    if (!results.has(id)) lines.push(`  class ${nid(id)} base`);
    else lines.push(`  class ${nid(id)} bred`);
  });
  // clickable
  usedNodes.forEach(id => lines.push(`  click ${nid(id)} nodeClick`));
  lines.push('classDef base fill:#e4ecdd,stroke:#008800,stroke-width:2px,color:#333;');
  lines.push('classDef bred fill:#fff,stroke:#4881cf,color:#333;');
  lines.push('classDef junction fill:#8cacbb,stroke:#6d8b99,color:#8cacbb,width:8px,height:8px;');
  return lines.join('\n');
}

async function render(){
  const muts = selectedMuts();
  $('#status').textContent = `${cat} · ${muts.length} mutations`;
  if (!muts.length){
    canvas.innerHTML = '<div style="padding:2em;color:#889">No mutations to show for this selection.</div>';
    return;
  }
  const src = buildMermaid(muts);
  try {
    const { svg } = await mermaid.render('graph_'+Date.now(), src);
    canvas.innerHTML = svg;
    bindNodeClicks();
    fitToView();
  } catch(e){
    canvas.innerHTML = '<div style="padding:2em;color:#a00">Render error: '+e.message+'</div>';
    console.error(e, src);
  }
}

/* window-scoped so Mermaid's click directive can reach it.
   Mermaid may pass the node id (n_XXX) as the argument. */
window.nodeClick = function(nodeId){
  const real = DATA[cat].nodes.find(n => nid(n.id) === nodeId);
  if (real) showDetail(real.id);
};

/* Fallback: bind clicks directly on rendered node groups, matching by the
   sanitized id that Mermaid writes into each node's DOM id. */
function bindNodeClicks(){
  canvas.querySelectorAll('svg .node').forEach(g => {
    g.style.cursor = 'pointer';
    g.addEventListener('click', ev => {
      ev.stopPropagation();
      // Mermaid node DOM id looks like "flowchart-n_FOREST-3"; extract n_XXX
      const domId = g.id || '';
      const m = domId.match(/(n_[A-Za-z0-9_]+?)(-\d+)?$/);
      let real = null;
      if (m) real = DATA[cat].nodes.find(n => nid(n.id) === m[1]);
      if (!real){
        // fall back to matching by visible label text
        const txt = (g.textContent||'').trim();
        real = DATA[cat].nodes.find(n => n.label === txt);
      }
      if (real) showDetail(real.id);
    });
  });
}

function showDetail(id){
  const n = nodeById(id);
  if(!n) return;
  $('#dTitle').textContent = n.label;
  const muts = DATA[cat].muts;
  const recipes = muts.filter(m => m.r === id);
  const usedIn = muts.filter(m => m.a === id || m.b === id);
  let h = '';
  if (n.genus) h += `<div class="row"><span class="k">Genus:</span> ${n.genus}</div>`;
  if (n.climate && n.climate!=='any') h += `<div class="row"><span class="k">Climate:</span> ${n.climate}</div>`;
  if (n.products) h += `<div class="row"><span class="k">Products:</span> ${n.products}</div>`;
  if (n.wood) h += `<div class="row"><span class="k">Wood:</span> ${n.wood}</div>`;
  // resources this species yields (bees only, via product/centrifuge index)
  const bp = (DATA[cat].beeProducts||{})[id];
  if (bp && bp.resources && bp.resources.length){
    h += '<div class="sec"><b>Yields</b>';
    h += `<div class="mut">${bp.resources.join(', ')}</div></div>`;
  }
  if (recipes.length){
    h += '<div class="sec"><b>Bred from</b>';
    recipes.forEach(m => h += `<div class="mut"><a data-id="${m.a}">${label(m.a)}</a> + <a data-id="${m.b}">${label(m.b)}</a> → ${m.c}%</div>`);
    h += '</div>';
  } else {
    h += '<div class="sec"><b>Base species</b><div class="mut">Found in the wild — not bred from other species.</div></div>';
  }
  if (usedIn.length){
    h += '<div class="sec"><b>Used to breed</b>';
    usedIn.forEach(m => { const other = m.a===id ? m.b : m.a;
      h += `<div class="mut">+ <a data-id="${other}">${label(other)}</a> → <a data-id="${m.r}">${label(m.r)}</a> (${m.c}%)</div>`; });
    h += '</div>';
  }
  $('#dBody').innerHTML = h;
  $('#dBody').querySelectorAll('a[data-id]').forEach(a =>
    a.addEventListener('click', () => showDetail(a.dataset.id)));
  $('#detail').classList.add('show');
}

/* ---- pan / zoom ---- */
function applyTransform(){ canvas.style.transform = `translate(${tx}px,${ty}px) scale(${scale})`; }
function fitToView(){
  const svg = canvas.querySelector('svg'); if(!svg) return;
  const vb = svg.getBBox ? null : null;
  const w = svg.getBoundingClientRect().width / scale || svg.width?.baseVal?.value || 1000;
  const vpW = viewport.clientWidth, vpH = viewport.clientHeight;
  const gW = svg.getBoundingClientRect().width/scale, gH = svg.getBoundingClientRect().height/scale;
  const s = Math.min(vpW/gW, vpH/gH) * 0.92;
  scale = Math.max(0.15, Math.min(s, 1.5));
  tx = (vpW - gW*scale)/2; ty = (vpH - gH*scale)/2;
  applyTransform();
}
let dragging=false, sx=0, sy=0;
viewport.addEventListener('mousedown', e => { dragging=true; sx=e.clientX-tx; sy=e.clientY-ty; viewport.classList.add('dragging'); });
window.addEventListener('mousemove', e => { if(!dragging) return; tx=e.clientX-sx; ty=e.clientY-sy; applyTransform(); });
window.addEventListener('mouseup', () => { dragging=false; viewport.classList.remove('dragging'); });
viewport.addEventListener('wheel', e => {
  e.preventDefault();
  const factor = e.deltaY<0 ? 1.12 : 1/1.12;
  const rect = viewport.getBoundingClientRect();
  const mx = e.clientX-rect.left, my = e.clientY-rect.top;
  const ns = Math.max(0.1, Math.min(4, scale*factor));
  tx = mx - (mx-tx)*(ns/scale); ty = my - (my-ty)*(ns/scale); scale = ns; applyTransform();
}, {passive:false});

/* ---- touch: one-finger drag to pan, two-finger pinch to zoom ---- */
let touchStart=null, pinchStart=null;
function dist(t1,t2){ return Math.hypot(t1.clientX-t2.clientX, t1.clientY-t2.clientY); }
function mid(t1,t2,rect){ return {x:(t1.clientX+t2.clientX)/2-rect.left, y:(t1.clientY+t2.clientY)/2-rect.top}; }
viewport.addEventListener('touchstart', e => {
  if (e.target.closest('.node')) return; // let node taps through for details
  if (e.touches.length===1){
    touchStart={x:e.touches[0].clientX-tx, y:e.touches[0].clientY-ty};
    pinchStart=null;
  } else if (e.touches.length===2){
    const rect=viewport.getBoundingClientRect();
    pinchStart={d:dist(e.touches[0],e.touches[1]), s:scale,
      c:mid(e.touches[0],e.touches[1],rect), tx, ty};
    touchStart=null;
  }
}, {passive:false});
viewport.addEventListener('touchmove', e => {
  if (e.touches.length===1 && touchStart){
    e.preventDefault();
    tx=e.touches[0].clientX-touchStart.x; ty=e.touches[0].clientY-touchStart.y; applyTransform();
  } else if (e.touches.length===2 && pinchStart){
    e.preventDefault();
    const rect=viewport.getBoundingClientRect();
    const d=dist(e.touches[0],e.touches[1]);
    const ns=Math.max(0.1, Math.min(4, pinchStart.s * (d/pinchStart.d)));
    // zoom around the pinch midpoint
    const c=pinchStart.c;
    tx = c.x - (c.x-pinchStart.tx)*(ns/pinchStart.s);
    ty = c.y - (c.y-pinchStart.ty)*(ns/pinchStart.s);
    scale = ns; applyTransform();
  }
}, {passive:false});
viewport.addEventListener('touchend', e => {
  if (e.touches.length===0){ touchStart=null; pinchStart=null; }
  else if (e.touches.length===1){ // lifted one finger after pinch — resume pan
    touchStart={x:e.touches[0].clientX-tx, y:e.touches[0].clientY-ty}; pinchStart=null;
  }
}, {passive:false});
$('#zoomIn').onclick = () => { scale=Math.min(4,scale*1.2); applyTransform(); };
$('#zoomOut').onclick = () => { scale=Math.max(0.1,scale/1.2); applyTransform(); };
$('#zoomFit').onclick = fitToView;
$('#reset').onclick = () => { $('#detail').classList.remove('show'); fitToView(); };
$('#dClose').onclick = () => $('#detail').classList.remove('show');

/* ---- filter controls ---- */
function populateFocus(){
  const mode = focusMode, sel = $('#focusValue');
  $('#resPanel').classList.remove('show');
  resourceBee = '';
  if (mode === 'all'){ sel.style.display='none'; focusValue=''; return; }
  sel.style.display='';
  let opts = [];
  if (mode === 'genus') opts = DATA[cat].genera;
  else if (mode === 'resource') opts = Object.keys(DATA[cat].resourceIndex || {});
  else opts = DATA[cat].nodes.map(n=>n.id).sort((a,b)=>label(a).localeCompare(label(b)));
  const optText = o => (mode==='genus'||mode==='resource') ? o : label(o);
  sel.innerHTML = opts.map(o => `<option value="${o}">${optText(o)}</option>`).join('');
  focusValue = opts[0] || '';
  if (mode === 'resource') showResourcePanel();
}

/* Resource mode: list the species that yield the chosen resource, each a link
   that loads that species' full breeding tree. */
function showResourcePanel(){
  const idx = DATA[cat].resourceIndex || {};
  const bees = idx[focusValue] || [];
  $('#resTitle').textContent = focusValue;
  let h = `<div class="note">${bees.length} species produce this resource (directly or via centrifuge). Pick one to see how to breed it:</div>`;
  bees.forEach(b => {
    const node = DATA[cat].nodes.find(n => n.label === b || n.id === b.toUpperCase());
    const id = node ? node.id : b.toUpperCase();
    h += `<a class="beelink" data-id="${id}">${b} &rarr;</a>`;
  });
  if (!bees.length) h += '<div class="note">No species produce this in the current data.</div>';
  $('#resBody').innerHTML = h;
  $('#resBody').querySelectorAll('.beelink').forEach(a =>
    a.addEventListener('click', () => { resourceBee = a.dataset.id; render(); showDetail(a.dataset.id); }));
  $('#resPanel').classList.add('show');
}

$('#focusMode').addEventListener('change', e => { focusMode=e.target.value; populateFocus(); render(); });
$('#focusValue').addEventListener('change', e => {
  focusValue=e.target.value;
  if (focusMode==='resource'){ resourceBee=''; showResourcePanel(); }
  render();
});
$('#tabs').addEventListener('click', e => {
  if (e.target.tagName!=='BUTTON') return;
  document.querySelectorAll('#tabs button').forEach(b=>b.classList.remove('active'));
  e.target.classList.add('active');
  cat = e.target.dataset.cat;
  if (cat==='bees'){ focusMode='genus'; $('#focusMode').value='genus'; }
  else { focusMode='all'; $('#focusMode').value='all'; }
  $('#detail').classList.remove('show');
  $('#resPanel').classList.remove('show');
  const hasRes = Object.keys(DATA[cat].resourceIndex||{}).length>0;
  $('#focusMode').querySelector('option[value=resource]').style.display = hasRes ? '' : 'none';
  populateFocus(); render();
});

/* init — honor a #bees / #trees / #butterflies deep link from the wiki pages */
const hashCat = location.hash.replace('#','');
if (['bees','trees','butterflies'].includes(hashCat)) cat = hashCat;
document.querySelectorAll('#tabs button').forEach(b => b.classList.toggle('active', b.dataset.cat === cat));
focusMode = (cat === 'bees') ? 'genus' : 'all';
$('#focusMode').value = focusMode;
$('#focusMode').querySelector('option[value=resource]').style.display =
  Object.keys(DATA[cat].resourceIndex||{}).length ? '' : 'none';
populateFocus();
render();
