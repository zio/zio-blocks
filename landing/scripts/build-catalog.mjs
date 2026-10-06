import { existsSync } from 'node:fs';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { join } from 'node:path';
import { parseIndex } from './lib/parse-index.mjs';
import { resolveVersion } from './lib/version.mjs';

const REPO_ROOT = fileURLToPath(new URL('../../', import.meta.url));
const LANDING_ROOT = fileURLToPath(new URL('../', import.meta.url));

/** Brand files the site references, copied from assets/logo at build time (never committed twice). */
export const BRAND_ASSETS = [
  'zio-blocks-logo-on-dark.svg',
  'zio-blocks-logo-mono-white.svg',
  'zio-blocks-mark-favicon.svg',
  'zio-blocks-social-og.png',
];

export async function build({ repoRoot = REPO_ROOT, outDir = LANDING_ROOT, version } = {}) {
  const parsed = parseIndex(await readFile(join(repoRoot, 'docs/index.md'), 'utf8'), {
    exists: (rel) => existsSync(join(repoRoot, 'docs', rel)),
  });
  const resolved = version ?? (await resolveVersion());

  const site = {
    version: resolved,
    ...parsed,
    hero: { ...parsed.hero, install: parsed.hero.install.replaceAll('@VERSION@', resolved) },
    blockCount: parsed.categories.reduce((n, c) => n + c.blocks.length, 0),
  };

  await mkdir(join(outDir, 'src/data'), { recursive: true });
  await writeFile(join(outDir, 'src/data/site.json'), JSON.stringify(site, null, 2) + '\n');

  await mkdir(join(outDir, 'public/brand'), { recursive: true });
  for (const file of BRAND_ASSETS) {
    await copyFile(join(repoRoot, 'assets/logo', file), join(outDir, 'public/brand', file));
  }
  return site;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const site = await build();
    console.log(`landing: ${site.blockCount} blocks in ${site.categories.length} categories, version ${site.version}`);
  } catch (e) {
    console.error(`landing: ${e.message}`);
    process.exit(1);
  }
}
