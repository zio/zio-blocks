import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { FOLDER_INDEXES, withFolderSlash } from '../../src/clientModules/folderIndexes.js';

test('a docs folder URL gets its trailing slash, query and hash kept', () => {
  assert.equal(withFolderSlash({ pathname: '/guides', search: '?q=1', hash: '#top' }), '/guides/?q=1#top');
  assert.equal(withFolderSlash({ pathname: '/reference', search: '', hash: '' }), '/reference/');
  assert.equal(withFolderSlash({ pathname: '/guides/', search: '', hash: '' }), null);
  assert.equal(withFolderSlash({ pathname: '/guides/sql-transactions', search: '', hash: '' }), null);
});

test('every listed folder has an index page in docs/', () => {
  for (const folder of FOLDER_INDEXES)
    assert.ok(existsSync(new URL(`../../../docs${folder}/index.md`, import.meta.url)), `docs${folder}/index.md`);
});
