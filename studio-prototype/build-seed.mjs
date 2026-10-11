// Embeds templates/*.json, scripts/**/*.js and sample-rows.json into schema-studio.html.
// Usage: node studio-prototype/build-seed.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const page = path.join(here, 'schema-studio.html');

const templates = {};
for (const f of fs.readdirSync(path.join(root, 'templates')).filter(f => f.endsWith('.json')).sort()) {
  const t = JSON.parse(fs.readFileSync(path.join(root, 'templates', f), 'utf8'));
  templates[t.name] = t;
}

const scripts = {};
const scriptsRoot = path.join(root, 'scripts');
(function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p);
    else if (e.name.endsWith('.js')) scripts[path.relative(scriptsRoot, p).split(path.sep).join('/')] = fs.readFileSync(p, 'utf8');
  }
})(scriptsRoot);

const samples = JSON.parse(fs.readFileSync(path.join(here, 'sample-rows.json'), 'utf8'));
const seed = JSON.stringify({ templates, scripts, samples }).replace(/</g, '\\u003c');

const html = fs.readFileSync(page, 'utf8');
const re = /(<script id="seed" type="application\/json">)[\s\S]*?(<\/script>)/;
if (!re.test(html)) throw new Error('seed <script> tag not found in schema-studio.html');
fs.writeFileSync(page, html.replace(re, (_, open, close) => open + seed + close));
console.log(`Seeded ${Object.keys(templates).length} templates, ${Object.keys(scripts).length} scripts into ${path.relative(root, page)}`);
