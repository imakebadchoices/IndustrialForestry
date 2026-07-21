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

# Sidebar targets are page slugs, except explorer category links which carry an
# explicit "explorer.html#<cat>" href (see link_href / link_slug below).
SIDEBAR = [
    ("Breeding Explorer", [
        ("Bees", "explorer.html#bees"),
        ("Trees", "explorer.html#trees"),
    ]),
    ("Add-ons", [
        ("Beegistics", "beegistics"),
        ("Beeripherals", "beeripherals"),
        ("Modern Bees", "modern-bees"),
        ("Extra Bees", "extra-bees"),
    ]),
    ("Get it", [
        ("Downloads", "downloads"),
    ]),
]
# Categories surfaced in the breeding explorer. Butterflies are omitted — the data
# defines only a single butterfly mutation, so a whole tab for them isn't worthwhile.
EXPLORER_CATEGORIES = ("bees", "trees")
SLUG_OVERRIDES = {"README": "index"}
PAGE_TITLES = {
    "index": "BadMods", "extra-bees": "Extra Bees",
    "beegistics": "Beegistics", "beeripherals": "Beeripherals",
    "modern-bees": "Modern Bees", "downloads": "Downloads",
    "explorer": "Breeding Explorer",
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


def extract_banner(html_body: str):
    """Pull a leading splash <img> out of the body and render it as a full-width
    banner above the TOC, so it never gets squeezed beside the floated TOC."""
    m = re.search(r'(?:<p>\s*)?(<img class="splash"[^>]*>)(?:\s*</p>)?', html_body)
    if not m:
        return "", html_body
    banner = f'<div class="banner">{m.group(1)}</div>'
    return banner, html_body[:m.start()] + html_body[m.end():]


# A sidebar target is either a bare page slug ("index") or an explicit href with a
# "." or "#" ("explorer.html#bees"). link_href resolves it to an href; link_slug
# gives the underlying page slug for active-state matching.
def link_href(target: str) -> str:
    return target if ("." in target or "#" in target) else f"{target}.html"


def link_slug(target: str) -> str:
    return target.split("#", 1)[0].removesuffix(".html")


def render_sidebar(active_slug: str) -> str:
    out = []
    for section, links in SIDEBAR:
        out.append(f'<h3 class="sidebar-section">{html.escape(section)}</h3>')
        out.append('<div class="level3"><p>')
        rows = []
        for label, target in links:
            cls = "wikilink1 active" if link_slug(target) == active_slug else "wikilink1"
            rows.append(f'<a href="{link_href(target)}" class="{cls}">{html.escape(label)}</a>')
        out.append("<br/>\n".join(rows))
        out.append("</p></div>")
    return "\n".join(out)


PAGE_TEMPLATE = """<!DOCTYPE html>
<html lang="en" dir="ltr">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>{title} [BadMods]</title>
<style>
{style}
</style>
</head>
<body>
<div id="dokuwiki__site"><div id="dokuwiki__top" class="site">

<div id="dokuwiki__header"><div class="pad group">
  <div class="headings group">
    <h1><a href="index.html"><span>BadMods</span></a></h1>
  </div>
</div></div><!-- /header -->

<div class="wrapper group">

  <div id="dokuwiki__aside"><div class="pad aside group">
    <div class="content">
      {sidebar}
    </div>
  </div></div><!-- /aside -->

  <div id="dokuwiki__content"><div class="pad group">
    <div class="pageId"><span>{title}</span></div>
    <div class="page group">
      {banner}
      {toc}
      {body}
    </div>
  </div></div><!-- /content -->

</div><!-- /wrapper -->

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
    data = {cat: model[cat]["graph"] for cat in EXPLORER_CATEGORIES}
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
        banner, body = extract_banner(body)
        toc, body = build_toc(body)
        title = PAGE_TITLES.get(slug, slug.title())
        (out / f"{slug}.html").write_text(PAGE_TEMPLATE.format(
            title=html.escape(title), style=STYLESHEET, sidebar=render_sidebar(slug),
            banner=banner, toc=toc, body=body), encoding="utf-8")
        print(f"  {f.name} -> {slug}.html")

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
.page h2 { font-size: 1.5em; clear: left; }
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
/* GUI screenshots floated beside the section they illustrate. Floated left (the TOC
   floats right) with clear:left on headings below, so a tall shot never overruns into
   the next section. */
.page figure.screenshot {
  float: left; clear: left; width: 300px; margin: 0.2em 1.3em 1em 0;
}
.page figure.screenshot img { width: 100%; display: block; }
/* Full-width splash banner — sits above the TOC and clears it */
.page .banner { clear: both; margin: 0 0 1.2em; }
.page img.splash {
  display: block; width: 100%; height: 180px; object-fit: cover;
  border: 1px solid var(--border); border-radius: 4px;
}
@media (max-width: 720px) {
  .page figure.illus-right { float: none; width: auto; max-width: 240px; margin: 1em auto; }
  .page figure.screenshot { float: none; width: auto; max-width: 300px; margin: 1em 0; }
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
    # Species now come entirely from on-disk JSON (base under src/generated, add-ons under src/main/resources),
    # so no code dump is required. --dump is accepted but ignored, for backwards compatibility.
    model = sd.build_model(repo)
    for cat in EXPLORER_CATEGORIES:
        print(f"  {cat}: {len(model[cat]['rows'])} species, {len(model[cat]['graph']['muts'])} mutations")
    convert(Path(args.src).resolve(), Path(args.out).resolve(), model, repo / "build/wiki-data", args.skip_explorer)


if __name__ == "__main__":
    main()
