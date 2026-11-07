// Writes build/version.txt after `docusaurus build`, so `curl https://zioblocks.com/version.txt` shows which commit is live.
import { writeFile } from 'node:fs/promises';
import { resolveCommit, versionText } from './lib/build-info.mjs';

const text = versionText({ commit: resolveCommit(), builtAt: new Date() });
await writeFile(new URL('../build/version.txt', import.meta.url), text);
console.log(`version.txt: ${text.split('\n')[0]}`);
