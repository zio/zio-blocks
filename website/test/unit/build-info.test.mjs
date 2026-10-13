import { test } from 'node:test';
import assert from 'node:assert/strict';
import { resolveCommit, versionText } from '../../scripts/lib/build-info.mjs';

const A = 'a'.repeat(40);
const B = 'b'.repeat(40);
const C = 'c'.repeat(40);
const noGit = () => undefined;

test('resolveCommit prefers Netlify COMMIT_REF, then GITHUB_SHA, then git', () => {
  assert.equal(resolveCommit({ env: { COMMIT_REF: A, GITHUB_SHA: B }, head: () => C }), A);
  assert.equal(resolveCommit({ env: { GITHUB_SHA: B }, head: () => C }), B);
  assert.equal(resolveCommit({ env: {}, head: () => C }), C);
});

test('resolveCommit trims and lower-cases the hash', () => {
  assert.equal(resolveCommit({ env: { COMMIT_REF: ` ${A.toUpperCase()}\n` }, head: noGit }), A);
});

test('resolveCommit skips values that are not a commit hash', () => {
  assert.equal(resolveCommit({ env: { COMMIT_REF: '', GITHUB_SHA: 'not a sha' }, head: () => C }), C);
});

test('resolveCommit is "unknown" when no source gives a hash', () => {
  assert.equal(resolveCommit({ env: {}, head: noGit }), 'unknown');
});

test('versionText has the commit and the build time, one per line', () => {
  assert.equal(
    versionText({ commit: A, builtAt: new Date('2026-10-12T12:00:00.000Z') }),
    `commit: ${A}\nbuilt: 2026-10-12T12:00:00.000Z\n`,
  );
});
