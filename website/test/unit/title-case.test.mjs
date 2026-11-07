import { test } from 'node:test';
import assert from 'node:assert/strict';
import { titleCase } from '../../src/lib/title-case.mjs';

test('titleCase capitalises each word except small words that are not first', () => {
  assert.equal(titleCase('building blocks for modern Scala applications'), 'Building Blocks for Modern Scala Applications');
  assert.equal(titleCase('the end of the road'), 'The End of the Road');
  assert.equal(titleCase('a tale to remember'), 'A Tale to Remember');
});

test('titleCase keeps the casing of the rest of each word (acronyms, camelCase)', () => {
  assert.equal(titleCase('zio blocks and ZIO tests for HTTP'), 'Zio Blocks and ZIO Tests for HTTP');
  // AP style: only short (<= 3 letters) articles, conjunctions and prepositions stay lowercase; "with" is capitalised.
  assert.equal(titleCase('use jsonCodec with ox'), 'Use JsonCodec With Ox');
});

test('titleCase handles empty and single-word input and repeated spaces', () => {
  assert.equal(titleCase(''), '');
  assert.equal(titleCase('scala'), 'Scala');
  assert.equal(titleCase('for'), 'For');
  assert.equal(titleCase('a  b'), 'A  B');
});

test('titleCase capitalises the part after a hyphen', () => {
  assert.equal(titleCase('type-safe, modular building blocks for Scala'), 'Type-Safe, Modular Building Blocks for Scala');
  assert.equal(titleCase('zero lock-in and built-in codecs'), 'Zero Lock-In and Built-In Codecs');
  assert.equal(titleCase('Compile-Time Resource Safety with Scope'), 'Compile-Time Resource Safety With Scope');
});

test('titleCase leaves punctuation-led tokens and numbers alone', () => {
  assert.equal(titleCase('47 blocks, take only what you need'), '47 Blocks, Take Only What You Need');
  assert.equal(titleCase('web & http'), 'Web & Http');
  assert.equal(titleCase('coming from zio schema?'), 'Coming From Zio Schema?');
});
