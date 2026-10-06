import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

export function extractExternalLinks(html) {
  const links = [...html.matchAll(/href="(https:[^"]+)"/g)].map((m) => m[1].replace(/&amp;/g, '&'));
  return [...new Set(links)];
}

/** Fails (returns a reason) only on 404/410, 5xx after a retry, or network errors; 403/429 are warnings. */
async function check(url) {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const res = await fetch(url, { redirect: 'follow', signal: AbortSignal.timeout(20_000), headers: { 'user-agent': 'zio-blocks-landing-linkcheck' } });
      if (res.status === 404 || res.status === 410) return { url, fail: `HTTP ${res.status}` };
      if (res.status === 403 || res.status === 429) return { url, warn: `HTTP ${res.status}` };
      if (res.status < 500) return { url };
      if (attempt === 1) return { url, fail: `HTTP ${res.status}` };
    } catch (e) {
      if (attempt === 1) return { url, fail: e.message };
    }
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const html = await readFile(new URL('../dist/index.html', import.meta.url), 'utf8');
  // The canonical link points at the site's own (not yet deployed) origin; `localhost` when URL is unset.
  const urls = extractExternalLinks(html).filter((u) => new URL(u).hostname !== 'localhost');
  const results = [];
  for (let i = 0; i < urls.length; i += 6) results.push(...(await Promise.all(urls.slice(i, i + 6).map(check))));
  for (const r of results.filter((r) => r.warn)) console.warn(`warn ${r.warn} ${r.url}`);
  const failed = results.filter((r) => r.fail);
  for (const r of failed) console.error(`FAIL ${r.fail} ${r.url}`);
  console.log(`landing: checked ${urls.length} external links, ${failed.length} broken`);
  process.exit(failed.length ? 1 : 0);
}
