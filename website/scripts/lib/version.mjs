/** Used when the GitHub API cannot be reached; bump occasionally, it only affects the install line. */
export const FALLBACK_VERSION = '0.0.56';

const LATEST = 'https://api.github.com/repos/zio/zio-blocks/releases/latest';

/** Latest published (non-draft, non-prerelease) release of zio-blocks, or FALLBACK_VERSION with a warning. */
export async function resolveVersion({
  fetchFn = fetch,
  token = process.env.GITHUB_TOKEN,
  warn = console.warn,
} = {}) {
  try {
    const res = await fetchFn(LATEST, {
      headers: {
        accept: 'application/vnd.github+json',
        'user-agent': 'zio-blocks-landing',
        ...(token ? { authorization: `Bearer ${token}` } : {}),
      },
      signal: AbortSignal.timeout(10_000),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const tag = (await res.json()).tag_name;
    const version = typeof tag === 'string' ? tag.replace(/^v/, '') : '';
    if (!/^\d+\.\d+\.\d+$/.test(version)) throw new Error(`unexpected tag "${tag}"`);
    return version;
  } catch (e) {
    warn(`landing: could not resolve latest release (${e.message}); using ${FALLBACK_VERSION}`);
    return FALLBACK_VERSION;
  }
}
