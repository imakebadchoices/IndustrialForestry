#!/usr/bin/env python3
"""
build.py — the single wiki build step.

From the datamap-aware species model (see scripts/species_data.py) plus the prose
markdown, produces one self-contained site under ``--out`` (default ``wiki/site``):

* every markdown page rendered to a standalone HTML file (CSS inlined),
* ``explorer.html`` — the interactive breeding explorer, fully bundled (Mermaid is
  fetched at build time at a pinned version and inlined; no vendored copy in the repo),
* the bees / trees / butterflies pages get an auto-generated full-table appendix and a
  link to the explorer, both derived from the same species model.

Prereq: ``./gradlew runData`` (writes build/wiki-data/species-dump.json). Deps: `markdown`.

    python3 build.py                      # build everything into wiki/site
    python3 build.py --out /tmp/site      # elsewhere
    python3 build.py --skip-explorer      # markdown only (no Mermaid fetch)
"""
import argparse
import html
import json
import re
import shutil
import sys
import urllib.request
from pathlib import Path

import markdown as md

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "scripts"))
import species_data as sd  # noqa: E402

# Pinned Mermaid, fetched at build time from the npm CDN and inlined into explorer.html.
MERMAID_VERSION = "10.9.6"
MERMAID_URL = f"https://cdn.jsdelivr.net/npm/mermaid@{MERMAID_VERSION}/dist/mermaid.min.js"

# Genetics pages that get an explorer link + generated appendix table.
SLUG_CATEGORY = {"apiculture": "bees", "arboriculture": "trees", "lepidopterology": "butterflies"}
CATEGORY_SINGULAR = {"bees": "Bee", "trees": "Tree", "butterflies": "Butterfly"}
# The generated species/mutation tables live on their own page per category.
CATEGORY_SPECIES_PAGE = {"bees": "bee-species", "trees": "tree-species", "butterflies": "butterfly-species"}
CATEGORY_MAIN_PAGE = {v: k for k, v in SLUG_CATEGORY.items()}

SIDEBAR = [
    ("Getting Started", [("Introduction", "index")]),
    ("Bees", [
        ("Beekeeping", "apiculture"),
        ("Bee Housing", "bee-housing"),
        ("Beekeeping Tools", "bee-tools"),
        ("Bee Species", "bee-species"),
        ("Breeding Explorer", "explorer"),
    ]),
    ("Trees & Butterflies", [
        ("Tree Breeding", "arboriculture"),
        ("Tree Species", "tree-species"),
        ("Butterflies", "lepidopterology"),
        ("Butterfly Species", "butterfly-species"),
    ]),
    ("Crafting & Machines", [
        ("Carpenter", "carpenter"), ("Thermionic Fabricator", "fabricator"),
        ("Moistener", "moistener"), ("Squeezer", "squeezer"),
        ("Centrifuge", "centrifuge"), ("Worktable", "worktable"),
    ]),
    ("Energy", [("Engines", "energy"), ("Fermenter", "fermenter"), ("Still", "still")]),
    ("Farms", [
        ("Multifarm", "farming"), ("Farm Structure", "farm-structure"),
        ("Cultivation", "cultivation"),
    ]),
    ("Logistics", [
        ("Backpacks & Crates", "storage"), ("Genetic Filter", "sorting"), ("Mail", "mail"),
    ]),
    ("Other", [
        ("Charcoal & Wood Piles", "charcoal"), ("Ores & Resources", "ores"),
        ("Rainmaker", "rainmaker"), ("Access Control", "access"),
    ]),
    ("Foundation", [("Core Materials", "core")]),
]
SLUG_OVERRIDES = {"README": "index"}
PAGE_TITLES = {
    "index": "Introduction", "apiculture": "Beekeeping",
    "bee-housing": "Bee Housing", "bee-tools": "Beekeeping Tools",
    "arboriculture": "Tree Breeding",
    "lepidopterology": "Butterfly Studies", "explorer": "Breeding Explorer",
    "bee-species": "Bee Species & Mutations", "tree-species": "Tree Species & Mutations",
    "butterfly-species": "Butterfly Species & Mutations",
    "carpenter": "Carpenter", "fabricator": "Thermionic Fabricator",
    "moistener": "Moistener", "squeezer": "Squeezer", "centrifuge": "Centrifuge",
    "energy": "Engines", "fermenter": "Fermenter", "still": "Still",
    "farming": "Multifarm", "farm-structure": "Farm Structure", "cultivation": "Cultivation",
    "storage": "Backpacks & Crates", "sorting": "Genetic Filter",
    "worktable": "Worktable", "mail": "Mail",
    "charcoal": "Charcoal & Wood Piles", "ores": "Ores & Resources",
    "rainmaker": "Rainmaker", "access": "Access Control", "core": "Core",
}


def slug_for(path: Path) -> str:
    return SLUG_OVERRIDES.get(path.stem, path.stem)


def rewrite_links(html_body: str) -> str:
    def repl(m):
        href = m.group(1)
        if href.startswith(("http://", "https://", "#")):
            return m.group(0)
        href = re.sub(r"README\.md", "index.html", href)
        href = re.sub(r"\.md(#|$)", r".html\1", href)
        return f'href="{href}"'
    return re.sub(r'href="([^"]+)"', repl, html_body)


def build_toc(html_body: str):
    headings = re.findall(r'<h([23]) id="([^"]+)">(.*?)</h[23]>', html_body, re.S)
    if not headings:
        return "", html_body
    items = []
    for level, hid, text in headings:
        text = re.sub(r"<[^>]+>", "", text).strip()
        cls = "level2" if level == "2" else "level3"
        items.append(f'<li class="{cls}"><div class="li"><a href="#{hid}">{text}</a></div></li>')
    toc = ('<div id="dw__toc" class="dw__toc">\n<h3 class="toggle">Table of Contents</h3>\n<div>\n'
           '<ul class="toc">\n' + "\n".join(items) + "\n</ul>\n</div>\n</div>\n")
    return toc, html_body


def render_sidebar(active_slug: str) -> str:
    out = []
    for section, links in SIDEBAR:
        out.append(f'<h3 class="sidebar-section">{html.escape(section)}</h3>')
        out.append('<div class="level3"><p>')
        rows = []
        for label, slug in links:
            cls = "wikilink1 active" if slug == active_slug else "wikilink1"
            rows.append(f'<a href="{slug}.html" class="{cls}">{html.escape(label)}</a>')
        out.append("<br/>\n".join(rows))
        out.append("</p></div>")
    return "\n".join(out)


# ---------------------------------------------------------------------------
# Generated genetics data. The full species + mutation tables live on their own
# `*-species` page; the breeding pages carry only a short pointer to it.
# ---------------------------------------------------------------------------
def render_explorer_note(category: str) -> str:
    """Plain, non-promotional pointer to the interactive explorer."""
    return (f'<p>The same breeding relationships can be viewed interactively in the '
            f'<a href="explorer.html#{category}">Breeding Explorer</a>, which filters by genus, '
            f'resource, or lineage and shows each species’ recipes and yields.</p>')


def render_breeding_reference(category: str, cat_model) -> str:
    """Short section appended to a breeding page, linking to the data page + explorer."""
    rows = cat_model["rows"]
    muts = cat_model["graph"]["muts"]
    species_page = CATEGORY_SPECIES_PAGE[category]
    species_title = PAGE_TITLES[species_page]
    return "\n".join([
        '<h2 id="species-and-mutations">Species and mutations</h2>',
        f'<p>A complete reference of all {len(rows)} registered {category} and their '
        f'{len(muts)} breeding mutations — with climates, products, and traits — is on the '
        f'<a href="{species_page}.html">{html.escape(species_title)}</a> page.</p>',
        render_explorer_note(category),
    ])


def render_species_page(category: str, cat_model) -> str:
    """Standalone data page: full per-genus species table + mutation table."""
    rows, graph = cat_model["rows"], cat_model["graph"]
    singular = CATEGORY_SINGULAR[category]
    with_products = category == "bees"
    main_page = CATEGORY_MAIN_PAGE[category]
    main_title = PAGE_TITLES[main_page]
    parts = [
        f'<p>Every registered {category[:-1]} breed ({len(rows)} total), grouped by genus, '
        'generated directly from the live registry and datapacks (including add-ons such as '
        f'Extra Bees). See <a href="{main_page}.html">{html.escape(main_title)}</a> for how '
        'breeding works.</p>',
        render_explorer_note(category),
        f'<h2 id="species">All {category}</h2>',
    ]
    current_genus = None
    for row in rows:
        if row["genus"] != current_genus:
            if current_genus is not None:
                parts.append("</tbody></table>")
            current_genus = row["genus"]
            # h4 (not h3) so the many genus groups don't flood the page TOC.
            parts.append(f'<h4>{html.escape(current_genus or "—")}</h4>')
            head = f"<th>{singular}</th><th>Climate</th>"
            if with_products:
                head += "<th>Products</th>"
            head += "<th>Notable traits</th>"
            parts.append(f'<table class="genetics"><thead><tr>{head}</tr></thead><tbody>')
        cells = [f'<td>{html.escape(row["label"])}</td>',
                 f'<td>{html.escape(row["climate"] or "—")}</td>']
        if with_products:
            cells.append(f'<td>{html.escape(row["products"] or "—")}</td>')
        cells.append(f'<td>{html.escape(", ".join(row["traits"]) if row["traits"] else "—")}</td>')
        parts.append("<tr>" + "".join(cells) + "</tr>")
    if current_genus is not None:
        parts.append("</tbody></table>")

    muts = graph["muts"]
    if muts:
        label = {n["id"]: n["label"] for n in graph["nodes"]}
        parts.append(f'<h2 id="mutations">{singular} mutations</h2>')
        parts.append(f'<p>{len(muts)} breeding mutations. Chance is the base combination chance '
                     'before mutation-rate modifiers.</p>')
        parts.append('<table class="genetics"><thead><tr><th>Result</th><th>Parent</th>'
                     '<th>Parent</th><th>Chance</th></tr></thead><tbody>')
        for m in sorted(muts, key=lambda m: (label.get(m["r"], m["r"]), label.get(m["a"], m["a"]))):
            parts.append("<tr>"
                         f'<td>{html.escape(label.get(m["r"], m["r"]))}</td>'
                         f'<td>{html.escape(label.get(m["a"], m["a"]))}</td>'
                         f'<td>{html.escape(label.get(m["b"], m["b"]))}</td>'
                         f'<td>{m["c"]}%</td></tr>')
        parts.append("</tbody></table>")
    return "\n".join(parts)


PAGE_TEMPLATE = """<!DOCTYPE html>
<html lang="en" dir="ltr">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>{title} [Forestry: CE]</title>
<style>
{style}
</style>
</head>
<body>
<div id="dokuwiki__site"><div id="dokuwiki__top" class="site">

<div id="dokuwiki__header"><div class="pad group">
  <div class="headings group">
    <h1><a href="index.html"><span>Forestry: Community Edition</span></a></h1>
  </div>
  <div class="breadcrumbs">
    <div class="trace"><span class="bchead">Trace:</span>
      <span class="bcsep">&bull;</span>
      <span class="curid">{title}</span>
    </div>
  </div>
</div></div><!-- /header -->

<div class="wrapper group">

  <div id="dokuwiki__aside"><div class="pad aside group">
    <h3 class="toggle">Sidebar</h3>
    <div class="content">
      {sidebar}
    </div>
  </div></div><!-- /aside -->

  <div id="dokuwiki__content"><div class="pad group">
    <div class="pageId"><span>{title}</span></div>
    <div class="page group">
      {toc}
      {body}
    </div>
    <div class="docInfo">
      Generated from source (Forestry: Community Edition). Genetics tables are built
      from the live registry + datapacks.
    </div>
  </div></div><!-- /content -->

</div><!-- /wrapper -->

<div id="dokuwiki__footer"><div class="pad">
  <div class="license">
    Forestry: Community Edition wiki &middot; content generated from mod source.
  </div>
</div></div><!-- /site -->

</div></div>
</body>
</html>
"""


def fetch_mermaid(cache_dir: Path) -> str:
    """Fetch the pinned Mermaid build (cached under build/wiki-data)."""
    cache = cache_dir / f"mermaid-{MERMAID_VERSION}.min.js"
    if cache.exists():
        return cache.read_text(encoding="utf-8")
    print(f"  fetching Mermaid {MERMAID_VERSION} from {MERMAID_URL}")
    with urllib.request.urlopen(MERMAID_URL, timeout=60) as resp:
        text = resp.read().decode("utf-8")
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(text, encoding="utf-8")
    return text


def build_explorer(out: Path, model, cache_dir: Path):
    """Assemble the self-contained explorer.html from template + mermaid + data + app."""
    explorer_dir = HERE / "explorer"
    template = (explorer_dir / "template.html").read_text(encoding="utf-8")
    app_js = (explorer_dir / "breeding-explorer-app.js").read_text(encoding="utf-8")
    data = {cat: model[cat]["graph"] for cat in ("bees", "trees", "butterflies")}
    data_js = "const DATA=" + json.dumps(data, separators=(",", ":"), ensure_ascii=False) + ";\n"
    mermaid_js = fetch_mermaid(cache_dir)
    scripts = (f"<script>\n{mermaid_js}\n</script>\n"
               f"<script>\n{data_js}</script>\n"
               f"<script>\n{app_js}\n</script>\n")
    (out / "explorer.html").write_text(template.replace("<!--SCRIPTS-->\n", scripts), encoding="utf-8")


def convert(src: Path, out: Path, model, cache_dir: Path, skip_explorer: bool):
    out.mkdir(parents=True, exist_ok=True)
    # Curated images (flavor + structure) ship alongside the prose; deploy them to site/images.
    img_src = HERE / "images"
    if img_src.is_dir():
        shutil.copytree(img_src, out / "images", dirs_exist_ok=True)
        print(f"  images/ ({len(list(img_src.glob('*')))} files)")
    for f in sorted(src.glob("*.md")):
        slug = slug_for(f)
        text = re.sub(r"^#\s+.*\n", "", f.read_text(encoding="utf-8"), count=1)
        body = rewrite_links(md.Markdown(extensions=["tables", "toc", "fenced_code"]).convert(text))
        if slug in SLUG_CATEGORY:
            body += "\n" + render_breeding_reference(SLUG_CATEGORY[slug], model[SLUG_CATEGORY[slug]])
        toc, body = build_toc(body)
        title = PAGE_TITLES.get(slug, slug.title())
        (out / f"{slug}.html").write_text(PAGE_TEMPLATE.format(
            title=html.escape(title), style=STYLESHEET, sidebar=render_sidebar(slug),
            toc=toc, body=body), encoding="utf-8")
        print(f"  {f.name} -> {slug}.html")

    # Generated data pages: the full species + mutation tables, one per category.
    for category, species_slug in CATEGORY_SPECIES_PAGE.items():
        body = render_species_page(category, model[category])
        toc, body = build_toc(body)
        (out / f"{species_slug}.html").write_text(PAGE_TEMPLATE.format(
            title=html.escape(PAGE_TITLES[species_slug]), style=STYLESHEET,
            sidebar=render_sidebar(species_slug), toc=toc, body=body), encoding="utf-8")
        print(f"  (generated) -> {species_slug}.html")

    if not skip_explorer:
        build_explorer(out, model, cache_dir)
        print(f"  explorer.html (Mermaid {MERMAID_VERSION} bundled)")
    print(f"Done. Site written to {out}")


STYLESHEET = """
:root {
  --green: #008800;
  --green-dark: #006600;
  --link: #4881cf;
  --link-visited: #6a8bb7;
  --border: #8cacbb;
  --bg: #ffffff;
  --sidebar-bg: #f7f9fa;
  --toc-bg: #f7f9fa;
  --text: #333333;
  --table-head: #dee7ec;
  --table-alt: #f2f5f7;
}
* { box-sizing: border-box; }
body {
  font: 87.5%/1.5 "Lucida Grande","DejaVu Sans",Verdana,Arial,sans-serif;
  color: var(--text);
  background: #e9ebe4;
  margin: 0;
}
a { color: var(--link); text-decoration: none; }
a:visited { color: var(--link-visited); }
a:hover { text-decoration: underline; }

#dokuwiki__site { max-width: 1120px; margin: 0 auto; }
#dokuwiki__top { background: var(--bg); border: 1px solid var(--border);
  border-top: none; }

/* Header */
#dokuwiki__header { border-bottom: 1px solid var(--border); }
#dokuwiki__header .pad { padding: 1em 1.5em 0.5em; }
#dokuwiki__header h1 { margin: 0; font-size: 1.9em; }
#dokuwiki__header h1 a,
#dokuwiki__header h1 a:visited { color: var(--green); font-weight: bold; }
.breadcrumbs { font-size: 0.9em; color: #666; padding-top: 0.6em; }
.breadcrumbs .bchead { color: #999; }
.breadcrumbs .curid { color: var(--text); font-weight: bold; }

/* Layout: sidebar + content */
.wrapper { display: flex; align-items: flex-start; }
#dokuwiki__aside { width: 16em; flex: 0 0 16em; }
#dokuwiki__aside .pad {
  margin: 1em; padding: 0.7em 1em;
  background: var(--sidebar-bg);
  border: 1px solid var(--border);
  font-size: 0.95em;
}
#dokuwiki__content { flex: 1 1 auto; min-width: 0; }
#dokuwiki__content .pad { padding: 1em 1.5em 1.5em; }

/* Sidebar sections */
#dokuwiki__aside h3.toggle {
  margin: 0 0 0.6em; padding-bottom: 0.3em;
  color: var(--green); font-size: 1.1em;
  border-bottom: 1px solid var(--border);
}
h3.sidebar-section {
  color: var(--green-dark);
  font-size: 1em; margin: 1em 0 0.2em; padding-bottom: 0.15em;
  border-bottom: 1px dotted var(--border);
}
#dokuwiki__aside .level3 p { margin: 0.3em 0 0.6em; line-height: 1.8; }
#dokuwiki__aside a.active {
  font-weight: bold; color: var(--green-dark);
  background: #e4ecdd; padding: 0 3px;
}

/* Page id + headings */
.pageId { text-align: right; }
.pageId span {
  font-size: 0.8em; color: #6d7b83; background: var(--sidebar-bg);
  border: 1px solid var(--border); border-top: none;
  padding: 0.1em 0.6em; border-radius: 0 0 3px 3px;
}
.page h2, .page h3, .page h4 {
  color: var(--green); font-weight: bold;
  border-bottom: 1px solid var(--border);
  margin: 1.2em 0 0.6em; padding-bottom: 0.2em;
}
.page h2 { font-size: 1.5em; }
.page h3 { font-size: 1.25em; border-bottom: 1px dotted var(--border); }
.page h4 { font-size: 1.05em; border-bottom: none; }
.page p, .page li { line-height: 1.6; }

/* Images & figures */
.page img { max-width: 100%; height: auto; }
.page figure { margin: 1em 0; text-align: center; }
.page figure img {
  border: 1px solid var(--border); background: #fff; padding: 4px; border-radius: 2px;
}
.page figure figcaption {
  font-size: 0.85em; color: #777; margin-top: 0.3em; font-style: italic;
}
/* Flavour illustration floated beside the prose */
.page figure.illus-right {
  float: right; width: 190px; margin: 0.2em 0 0.8em 1.3em;
}
.page figure.illus-right img { width: 100%; display: block; }
/* Block "showcase" render — anchored top-right as an infobox below the TOC, so it
   never crosses a section separator. */
.page figure.showcase {
  float: right; clear: right; width: 150px; margin: 0.2em 0 1em 1.3em;
}
.page figure.showcase img { width: 100%; display: block; }
/* Tiny pixel-grid diagrams (farm layouts) — keep crisp, don't over-enlarge */
.page figure.diagram { display: inline-block; margin: 0.6em; vertical-align: top; }
.page figure.diagram img { image-rendering: pixelated; width: 132px; height: auto; }
/* Full-width splash banner */
.page img.splash {
  display: block; width: 100%; border: 1px solid var(--border);
  border-radius: 3px; margin: 0 0 1.2em;
}
@media (max-width: 720px) {
  .page figure.illus-right { float: none; width: auto; max-width: 240px; margin: 1em auto; }
}

/* Blockquotes -> the "needs verification" callouts */
.page blockquote {
  margin: 1em 0; padding: 0.6em 1em;
  background: #fff8e1; border-left: 4px solid #e0a800;
  color: #665200;
}
.page blockquote p { margin: 0.3em 0; }

/* Inline data tables (DokuWiki .inline look) */
.page table {
  border-collapse: collapse; margin: 1em 0; font-size: 0.95em;
  border: 1px solid var(--border);
}
.page th, .page td {
  border: 1px solid var(--border); padding: 0.35em 0.7em; text-align: left;
  vertical-align: top;
}
.page th { background: var(--table-head); color: #2a2a2a; font-weight: bold; }
.page tr:nth-child(even) td { background: var(--table-alt); }
/* Generated genetics tables: shrink to their content (no full-width "double box"),
   but cap at the container and scroll horizontally when a table is genuinely wide. */
.page table.genetics { display: block; width: max-content; max-width: 100%; overflow-x: auto; }

.page code {
  background: #f4f4f4; border: 1px solid #d8d8d8; border-radius: 2px;
  padding: 0 3px; font-size: 0.9em;
}

/* TOC box */
.dw__toc {
  float: right; width: 18em; margin: 0 0 1em 1em;
  background: var(--toc-bg); border: 1px solid var(--border);
  font-size: 0.9em;
}
.dw__toc h3 {
  margin: 0; padding: 0.4em 0.8em; color: var(--green);
  border-bottom: 1px solid var(--border); font-size: 1em;
}
.dw__toc ul { list-style: none; margin: 0.4em 0; padding: 0 0.8em; }
.dw__toc li.level3 { padding-left: 1em; }
.dw__toc li { line-height: 1.7; }

.docInfo {
  margin-top: 2em; padding-top: 0.6em; font-size: 0.82em; color: #888;
  border-top: 1px solid var(--border); font-style: italic;
}

/* Footer */
#dokuwiki__footer { border-top: 1px solid var(--border); }
#dokuwiki__footer .pad { padding: 0.8em 1.5em; }
#dokuwiki__footer .license { font-size: 0.82em; color: #777; }

/* Responsive: collapse sidebar above content on narrow screens */
@media (max-width: 720px) {
  .wrapper { flex-direction: column; }
  #dokuwiki__aside { width: auto; flex: none; }
  .dw__toc { float: none; width: auto; margin: 1em 0; }
}
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=str(HERE.parent), help="repository root")
    ap.add_argument("--src", default=str(HERE / "wiki-markdown"), help="markdown source dir")
    ap.add_argument("--out", default=str(HERE / "site"), help="output site dir")
    ap.add_argument("--dump", default=None, help="path to species-dump.json")
    ap.add_argument("--skip-explorer", action="store_true", help="markdown only; no Mermaid fetch")
    args = ap.parse_args()

    repo = Path(args.repo).resolve()
    dump = Path(args.dump) if args.dump else repo / "build/wiki-data/species-dump.json"
    if not dump.exists():
        raise SystemExit(f"species dump not found at {dump}\nRun `./gradlew runData` first (see generate.sh).")

    model = sd.build_model(repo, dump)
    for cat in ("bees", "trees", "butterflies"):
        print(f"  {cat}: {len(model[cat]['rows'])} species, {len(model[cat]['graph']['muts'])} mutations")
    convert(Path(args.src).resolve(), Path(args.out).resolve(), model, repo / "build/wiki-data", args.skip_explorer)


if __name__ == "__main__":
    main()
