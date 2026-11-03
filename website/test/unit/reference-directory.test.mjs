import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  groupId, platformLabel, scalaLabel, isScala3Only, filterGroups, countBlocks, splitColumns,
} from '../../src/lib/reference-directory.mjs';

const rel = (name, artifact, description) => ({ name, url: '/x', artifact, description });
const blk = (name, artifact, description, related = [], scala = ['2.13', '3.x']) => ({
  name, url: '/x', artifact, platforms: ['JVM', 'JS'], scala, description, related,
});
const groups = () => [
  { name: 'Meta Programming', blocks: [
    blk('Schema', 'zio-blocks-schema', 'Type-safe schemas', [rel('Avro Codec', 'zio-blocks-schema-avro', 'Apache Avro binary serialization')]),
    blk('TypeId', 'zio-blocks-typeid', 'Compile-time type identity'),
  ] },
  { name: 'Web & HTTP', blocks: [
    blk('JWT', 'zio-blocks-jwt', 'Token signing'),
    blk('HTMX', 'zio-blocks-http-htmx', 'Typed HTMX DSL', [], ['3.x']),
  ] },
];

test('group ids are anchors', () => {
  assert.equal(groupId('Web & HTTP'), 'ref-web-http');
  assert.equal(groupId('Configuration & Feature Flags'), 'ref-configuration-feature-flags');
  assert.equal(groupId('Tooling & Codegen'), 'ref-tooling-codegen');
});

test('badge labels, with the Scala 3 only case', () => {
  const [both, only] = [groups()[1].blocks[0], groups()[1].blocks[1]];
  assert.equal(platformLabel(both), 'JVM · JS');
  assert.equal(scalaLabel(both), 'Scala 2.13 · 3.x');
  assert.equal(isScala3Only(both), false);
  assert.equal(scalaLabel(only), 'Scala 3 only');
  assert.equal(isScala3Only(only), true);
});

test('an empty query keeps every group; a query matches name, artifact, description and related links', () => {
  const g = groups();
  assert.equal(filterGroups(g, '  '), g);
  assert.deepEqual(filterGroups(g, 'jwt').map((x) => [x.name, x.blocks.map((y) => y.name)]), [['Web & HTTP', ['JWT']]]);
  assert.deepEqual(filterGroups(g, 'zio-blocks-typeid')[0].blocks.map((y) => y.name), ['TypeId']);
  assert.deepEqual(filterGroups(g, 'SIGNING')[0].blocks.map((y) => y.name), ['JWT']);
  const avro = filterGroups(g, 'avro');
  assert.deepEqual(avro[0].blocks.map((y) => y.name), ['Schema']);
  assert.deepEqual(avro[0].blocks[0].related.map((r) => [r.name, r.hit]), [['Avro Codec', true]]);
  assert.deepEqual(filterGroups(g, 'nothing here'), []);
});

test('a group name that matches keeps all its blocks, with no related link marked', () => {
  const [only] = filterGroups(groups(), 'meta');
  assert.deepEqual(only.blocks.map((y) => y.name), ['Schema', 'TypeId']);
  assert.deepEqual(only.blocks[0].related.map((r) => r.hit), [false]);
});

test('counts blocks', () => {
  assert.equal(countBlocks(groups()), 4);
  assert.equal(countBlocks([]), 0);
});

test('two columns keep the groups in order and balance the height', () => {
  const g = (name, n) => ({ name, blocks: Array.from({ length: n }, (_, i) => blk(`${name}${i}`, 'a', 'd')) });
  const [left, right] = splitColumns([g('A', 2), g('B', 8), g('C', 1), g('D', 2), g('E', 2)]);
  assert.deepEqual(left.map((x) => x.name), ['A', 'B']);
  assert.deepEqual(right.map((x) => x.name), ['C', 'D', 'E']);
  assert.deepEqual(splitColumns(groups()).flat().map((x) => x.name), ['Meta Programming', 'Web & HTTP']);
  assert.deepEqual(splitColumns([]), [[], []]);
  assert.deepEqual(splitColumns([g('Solo', 3)]).map((c) => c.map((x) => x.name)), [['Solo'], []]);
});

test('a block with related links weighs one line more when the columns are cut', () => {
  const withRelated = { name: 'X', blocks: [blk('X0', 'a', 'd', [rel('R', 'a', 'd')])] };
  const plain = (name) => ({ name, blocks: [blk(name + '0', 'a', 'd')] });
  assert.deepEqual(splitColumns([withRelated, plain('Y'), plain('Z')]).map((c) => c.map((x) => x.name)), [['X'], ['Y', 'Z']]);
  assert.deepEqual(splitColumns([plain('X'), plain('Y'), plain('Z')]).map((c) => c.map((x) => x.name)), [['X', 'Y'], ['Z']]);
});
