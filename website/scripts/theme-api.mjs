// Gives the Scaladoc pages in build/api the site's navbar, fonts and colour mode (see lib/api-theme.mjs). Runs after
// `docusaurus build`. build/api exists only when `sbt docs/generateApiDocs` filled static/api first; without it there
// is nothing to do.
import { copyFile, readdir, readFile, stat, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { themeApiPage } from './lib/api-theme.mjs';
import { promptFor } from '../src/lib/prompt.mjs';

const root = fileURLToPath(new URL('..', import.meta.url));
const apiDir = join(root, 'build', 'api');

const exists = (p) => stat(p).then(() => true, () => false);

async function* htmlFiles(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) yield* htmlFiles(path);
    else if (entry.name.endsWith('.html')) yield path;
  }
}

if (!(await exists(apiDir))) {
  console.log('theme-api: no build/api (run `sbt docs/generateApiDocs` first to publish the API reference), skipped');
} else {
  // The same origin the rest of the site uses for the onboard-agent prompt (SITE_URL or the production domain).
  const { url } = createRequire(import.meta.url)('../docusaurus.config.js');
  const prompt = promptFor(url);
  let pages = 0;
  for await (const file of htmlFiles(apiDir)) {
    await writeFile(file, themeApiPage(await readFile(file, 'utf8'), { prompt }));
    pages++;
  }
  await copyFile(join(root, 'src', 'api-theme', 'api.css'), join(apiDir, 'styles', 'zio-blocks.css'));
  await copyFile(join(root, 'src', 'api-theme', 'theme.js'), join(apiDir, 'scripts', 'theme.js'));
  console.log(`theme-api: gave ${pages} API pages the site navbar and theme`);
}
