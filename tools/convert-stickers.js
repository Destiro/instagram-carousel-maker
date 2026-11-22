// Converts the hand-sketched SVG stickers from the design wireframe into app/src/main/assets/stickers.json.
// Usage: node tools/convert-stickers.js
const fs = require("fs");
const path = require("path");

const src = fs.readFileSync(path.join(__dirname, "wireframe-stickers.html"), "utf8");
const decode = (s) => s.replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"');
const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "");

const f = (n) => Math.round(n * 100) / 100;
const circle = (cx, cy, r) => `M${f(cx - r)} ${f(cy)}a${r} ${r} 0 1 0 ${f(2 * r)} 0a${r} ${r} 0 1 0 ${f(-2 * r)} 0z`;
const ellipse = (cx, cy, rx, ry) => `M${f(cx - rx)} ${f(cy)}a${rx} ${ry} 0 1 0 ${f(2 * rx)} 0a${rx} ${ry} 0 1 0 ${f(-2 * rx)} 0z`;
const rect = (x, y, w, h, rx) =>
  rx
    ? `M${f(x + rx)} ${y}h${f(w - 2 * rx)}a${rx} ${rx} 0 0 1 ${rx} ${rx}v${f(h - 2 * rx)}a${rx} ${rx} 0 0 1 ${-rx} ${rx}h${f(-(w - 2 * rx))}a${rx} ${rx} 0 0 1 ${-rx} ${-rx}v${f(-(h - 2 * rx))}a${rx} ${rx} 0 0 1 ${rx} ${-rx}z`
    : `M${x} ${y}h${w}v${h}h${-w}z`;

function attrs(str) {
  const out = {};
  for (const m of str.matchAll(/([\w:-]+)="([^"]*)"/g)) out[m[1]] = m[2];
  if (out.style) {
    for (const part of out.style.split(";")) {
      const [k, v] = part.split(":").map((x) => x && x.trim());
      if (k && v && out[k] === undefined) out[k] = v;
    }
  }
  return out;
}

function parseTransform(t) {
  if (!t) return {};
  const r = t.match(/rotate\(\s*([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s*\)/);
  const tr = t.match(/translate\(\s*([-\d.]+)[ ,]+([-\d.]+)\s*\)/);
  return { rot: r ? [Number(r[1]), Number(r[2]), Number(r[3])] : undefined, tr: tr ? [Number(tr[1]), Number(tr[2])] : undefined };
}

function parseSvg(inner) {
  const layers = [];
  const stack = []; // {layer, rot, sw}
  const tagRe = /<(\/?)([a-zA-Z]+)([^>]*?)(\/?)>/g;
  let m;
  while ((m = tagRe.exec(inner))) {
    const [, closing, tag, rawAttrs, selfClose] = m;
    if (tag === "g") {
      if (closing) { stack.pop(); continue; }
      const a = attrs(rawAttrs);
      const parent = stack[stack.length - 1];
      const tf = parseTransform(a.transform);
      let layer = parent && parent.layer;
      if (a.class) {
        layer = { k: a.class, dx: tf.tr ? tf.tr[0] : 0, dy: tf.tr ? tf.tr[1] : 0, sw: a["stroke-width"] ? Number(a["stroke-width"]) : undefined, shapes: [] };
        layers.push(layer);
      }
      stack.push({ layer, rot: tf.rot || (parent && parent.rot), sw: a["stroke-width"] ? Number(a["stroke-width"]) : parent && parent.sw });
      if (selfClose) stack.pop();
      continue;
    }
    if (closing) continue;
    const ctx = stack[stack.length - 1];
    if (!ctx || !ctx.layer) continue;
    const a = attrs(rawAttrs);
    const shape = {};
    if (tag === "path") shape.d = a.d;
    else if (tag === "circle") shape.d = circle(+a.cx, +a.cy, +a.r);
    else if (tag === "ellipse") shape.d = ellipse(+a.cx, +a.cy, +a.rx, +a.ry);
    else if (tag === "rect") shape.d = rect(+a.x, +a.y, +a.width, +a.height, a.rx ? +a.rx : 0);
    else if (tag === "text") {
      const end = inner.indexOf("</text>", tagRe.lastIndex);
      shape.t = decode(inner.slice(tagRe.lastIndex, end));
      shape.x = +a.x; shape.y = +a.y; shape.size = +a["font-size"];
      shape.anchor = a["text-anchor"] || "start";
      shape.mono = /Elite|Courier/i.test(a["font-family"] || "");
      shape.fill = a.fill;
      tagRe.lastIndex = end + 7;
    } else continue;
    if (a.fill && a.fill !== "none") shape.fill = shape.fill || a.fill;
    if (a.fill === "none") shape.nofill = true;
    if (a.stroke && a.stroke !== "none") shape.stroke = a.stroke;
    const sw = a["stroke-width"] ? Number(a["stroke-width"]) : ctx.sw;
    if (sw !== undefined && !Number.isNaN(sw)) shape.sw = sw;
    if (a["stroke-dasharray"]) shape.dash = a["stroke-dasharray"].split(/[ ,]+/).map(Number);
    if (a.opacity) shape.op = Number(a.opacity);
    if (ctx.rot) shape.rot = ctx.rot;
    ctx.layer.shapes.push(shape);
  }
  return layers.map((l) => ({ k: l.k, dx: l.dx, dy: l.dy, sw: l.sw, shapes: l.shapes }));
}

const packs = [];
const sectionRe = /<h2 class="sec"><span>([^<]*)<\/span>/g;
const sections = [];
let sm;
while ((sm = sectionRe.exec(src))) sections.push({ name: decode(sm[1]), at: sm.index });

const tileRe = /<a href="EditorStickers\.dc\.html" class="tile3?" aria-label="([^"]*)"[^>]*>\s*<svg class="sk"([^>]*)>([\s\S]*?)<\/svg>/g;
let tm;
while ((tm = tileRe.exec(src))) {
  const sec = [...sections].reverse().find((s) => s.at < tm.index);
  if (!sec) continue;
  const packName = sec.name === "Hiking & outdoors" ? "Hiking & outdoors" : sec.name.replace(/ · swim bike run/, "");
  let pack = packs.find((p) => p.name === packName);
  if (!pack) packs.push((pack = { id: slug(packName), name: packName, stickers: [] }));
  const label = decode(tm[1]);
  const name = label.split(/[–,]/)[0].trim();
  pack.stickers.push({ id: `${pack.id}_${slug(label)}`, name: label, short: name, layers: parseSvg(tm[3]) });
}

const out = path.join(__dirname, "..", "app", "src", "main", "assets", "stickers.json");
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, JSON.stringify({ viewBox: 64, packs }));
console.log(packs.map((p) => `${p.name}: ${p.stickers.length}`).join("\n"));
console.log("total", packs.reduce((n, p) => n + p.stickers.length, 0), "->", out, fs.statSync(out).size, "bytes");
