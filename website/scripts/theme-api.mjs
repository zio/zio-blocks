// Gives the Scaladoc pages in build/api the site's navbar, favicon, fonts, colour mode and side menu (see
// lib/api-theme.mjs). Runs after `docusaurus build`. Each file is read from static/api, as `sbt docs/generateApiDocs`
// wrote it, and written over its copy in build/api, so running this again is safe. Without static/api there is nothing
// to do.
import { copyFile, readdir, readFile, stat, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extensionlessSearchData, extensionlessUxJs, hoistSideMenu, themeApiPage } from './lib/api-theme.mjs';
import { promptFor } from '../src/lib/prompt.mjs';

const root = fileURLToPath(new URL('..', import.meta.url));
const sourceDir = join(root, 'static', 'api');
const apiDir = join(root, 'build', 'api');

const exists = (p) => stat(p).then(() => true, () => false);

async function* htmlFiles(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) yield* htmlFiles(path);
    else if (entry.name.endsWith('.html')) yield path;
  }
}

if (!(await exists(sourceDir)) || !(await exists(apiDir))) {
  console.log('theme-api: no static/api (run `sbt docs/generateApiDocs` first to publish the API reference), skipped');
} else {
  // The same origin the rest of the site uses for the onboard-agent prompt (SITE_URL or the production domain).
  const { url } = createRequire(import.meta.url)('../docusaurus.config.js');
  const prompt = promptFor(url);
  let pages = 0;
  for await (const file of htmlFiles(sourceDir)) {
    const target = join(apiDir, relative(sourceDir, file));
    await writeFile(target, themeApiPage(await readFile(file, 'utf8'), { prompt }));
    pages++;
  }
  const menu = JSON.parse(await readFile(join(sourceDir, 'dynamicSideMenu.json'), 'utf8'));
  await writeFile(join(apiDir, 'dynamicSideMenu.json'), JSON.stringify(hoistSideMenu(menu)));
  for (const [file, rewrite] of [
    ['scripts/ux.js', extensionlessUxJs],
    ['scripts/searchData.js', extensionlessSearchData],
  ])
    await writeFile(join(apiDir, file), rewrite(await readFile(join(sourceDir, file), 'utf8')));
  await copyFile(join(root, 'src', 'api-theme', 'api.css'), join(apiDir, 'styles', 'zio-blocks.css'));
  await copyFile(join(root, 'src', 'api-theme', 'theme.js'), join(apiDir, 'scripts', 'theme.js'));
  console.log(`theme-api: gave ${pages} API pages the site navbar, favicon and theme`);
}
