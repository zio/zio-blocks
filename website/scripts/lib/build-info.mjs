import { execFileSync } from 'node:child_process';

const SHA = /^[0-9a-f]{7,40}$/i;

/** `git rev-parse HEAD`, or undefined outside a git checkout. */
function gitHead() {
  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
  } catch {
    return undefined;
  }
}

/**
 * The commit this build was made from: `COMMIT_REF` (Netlify), `GITHUB_SHA` (GitHub Actions), then `git rev-parse HEAD`.
 * `unknown` when none of them yields a hash.
 */
export function resolveCommit({ env = process.env, head = gitHead } = {}) {
  for (const candidate of [env.COMMIT_REF, env.GITHUB_SHA, head()]) {
    const sha = typeof candidate === 'string' ? candidate.trim() : '';
    if (SHA.test(sha)) return sha.toLowerCase();
  }
  return 'unknown';
}

const REPO_URL = 'https://github.com/zio/zio-blocks';

/** Contents of `version.txt`: which commit is deployed, a link to it on GitHub, and when it was built. */
export function versionText({ commit, builtAt }) {
  const url = commit === 'unknown' ? 'unknown' : `${REPO_URL}/commit/${commit}`;
  return `commit: ${commit}\nurl: ${url}\nbuilt: ${builtAt.toISOString()}\n`;
}
