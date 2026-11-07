// Gives the docs home page the slug /overview in the mdoc output (website/docs/index.md). Docs are served at the site root,
// where the landing page already owns `/`. The slug is set here, not in docs/index.md, because that source page is also built
// by the zio-sbt-website template site (`sbt docs/checkDocsOnFreshWebsite`), which links to the docs root.
import { readFile, writeFile } from 'node:fs/promises';

const file = new URL('../docs/index.md', import.meta.url);
let text;
try {
  text = await readFile(file, 'utf8');
} catch (e) {
  if (e.code === 'ENOENT') process.exit(0); // landing-only build: no mdoc output
  throw e;
}
const m = /^---\r?\n([\s\S]*?)\r?\n---/.exec(text);
if (!m) throw new Error('website/docs/index.md has no front matter');
if (/^slug:/m.test(m[1])) process.exit(0);
await writeFile(file, text.replace(/^---\r?\n/, '---\nslug: /overview\n'));
console.log('docs home: slug /overview');
