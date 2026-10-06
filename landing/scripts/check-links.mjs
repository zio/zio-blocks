import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

export function extractExternalLinks(html) {
  const links = [...html.matchAll(/href="(https:[^"]+)"/g)].map((m) => m[1].replace(/&amp;/g, '&'));
  return [...new Set(links)];
}

const DOCS_PREFIX = 'https://zio.dev/zio-blocks/';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * 'warn' for 403/429 and for a 404/410 on a zio.dev docs page (it may be unpublished until the next
 * release); 'fail' for any other 404/410 and for 5xx; 'ok' otherwise.
 */
export function classify(url, status) {
  if (status === 404 || status === 410) return url.startsWith(DOCS_PREFIX) ? 'warn' : 'fail';
  if (status === 403 || status === 429) return 'warn';
  return status >= 500 ? 'fail' : 'ok';
}

/** Retries once (after 1.5 s) on 5xx and network errors. */
async function check(url) {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const res = await fetch(url, { redirect: 'follow', signal: AbortSignal.timeout(20_000), headers: { 'user-agent': 'zio-blocks-landing-linkcheck' } });
      const verdict = classify(url, res.status);
      if (verdict === 'ok') return { url };
      if (verdict === 'warn') {
        const gone = res.status === 404 || res.status === 410;
        return { url, warn: String(res.status), note: gone ? ' (docs page may be unpublished until the next release)' : '' };
      }
      if (res.status < 500 || attempt === 1) return { url, fail: `HTTP ${res.status}` };
    } catch (e) {
      if (attempt === 1) return { url, fail: e.message };
    }
    await sleep(1500);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  let html;
  try {
    html = await readFile(new URL('../dist/index.html', import.meta.url), 'utf8');
  } catch (e) {
    if (e.code !== 'ENOENT') throw e;
    console.error('landing: dist/index.html not found; run npm run build first');
    process.exit(1);
  }
  // The canonical link points at the site's own (not yet deployed) origin; `localhost` when URL is unset.
  const urls = extractExternalLinks(html).filter((u) => new URL(u).hostname !== 'localhost');
  const results = [];
  for (let i = 0; i < urls.length; i += 6) results.push(...(await Promise.all(urls.slice(i, i + 6).map(check))));
  for (const r of results.filter((r) => r.warn)) console.warn(`warn ${r.warn} ${r.url}${r.note}`);
  const failed = results.filter((r) => r.fail);
  for (const r of failed) console.error(`FAIL ${r.fail} ${r.url}`);
  console.log(`landing: checked ${urls.length} external links, ${failed.length} broken`);
  process.exit(failed.length ? 1 : 0);
}
