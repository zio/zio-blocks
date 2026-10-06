import {
  CatalogError, splitSections, fences, paragraphs, proseBeforeFence, parseTable, bullets,
} from './markdown.mjs';

const DOCS_BASE = 'https://zio.dev/zio-blocks/';
const PLATFORMS = ['JVM', 'JS'];
const SCALA_VERSIONS = ['2.13', '3.x'];
const BLOCK_COLUMNS = ['Block', 'Artifact', 'Platform', 'Scala', 'Description'];
const DIVES = [
  ['schema', 'Schema'],
  ['scope', 'Scope'],
  ['async', 'Async'],
  ['sql', 'SQL'],
];

/** `./reference/schema/index.md` -> `https://zio.dev/zio-blocks/reference/schema/`. */
export function docsUrl(link) {
  if (!/^\.\/[\w\-./]+\.mdx?$/.test(link)) throw new CatalogError(`unmapped docs link "${link}"`);
  let path = link.slice(2).replace(/\.mdx?$/, '');
  if (path === 'index') path = '';
  else if (path.endsWith('/index')) path = path.slice(0, -'index'.length);
  return DOCS_BASE + path;
}

/** Text up to the first sentence end (". " followed by a capital); version numbers do not end it. */
export function firstSentence(text) {
  const m = /^(.*?[.!?])\s+(?=[A-Z])/.exec(text);
  return m ? m[1] : text;
}

const linkTarget = (url) => (/^https?:\/\//.test(url) ? url : docsUrl(url));

function section(sections, title, where = 'docs/index.md') {
  const found = sections.find((s) => s.title === title);
  if (!found) throw new CatalogError(`${where} has no "## ${title}" section`);
  return found.body;
}

function parseList(cell, allowed, what, name) {
  const items = cell.split('·').map((s) => s.trim()).filter(Boolean);
  if (items.length === 0) throw new CatalogError(`${name}: empty ${what} cell`);
  for (const item of items) {
    if (!allowed.includes(item)) throw new CatalogError(`${name}: unknown ${what} "${item}"`);
  }
  return items;
}

function parseBlock(cells) {
  const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(cells[0]);
  if (!link) throw new CatalogError(`block cell "${cells[0]}" is not a [name](link)`);
  const name = link[1];
  const artifact = /^`([a-z0-9-]+)`$/.exec(cells[1]);
  if (!artifact) throw new CatalogError(`${name}: artifact cell "${cells[1]}" is not a \`code\` span`);
  return {
    name,
    docsUrl: docsUrl(link[2]),
    artifact: artifact[1],
    platforms: parseList(cells[2], PLATFORMS, 'platform', name),
    scala: parseList(cells[3], SCALA_VERSIONS, 'Scala version', name),
    description: cells[4],
  };
}

function parseCategories(body) {
  const seen = new Set();
  const categories = [];
  for (const { title, body: catBody } of splitSections(body, 3).sections) {
    const table = parseTable(catBody);
    if (!table) throw new CatalogError(`category "${title}" has no block table`);
    if (table.header.join(' | ') !== BLOCK_COLUMNS.join(' | ')) {
      throw new CatalogError(`category "${title}": expected columns ${BLOCK_COLUMNS.join(' | ')}`);
    }
    if (table.rows.length === 0) throw new CatalogError(`category "${title}" has no blocks`);
    const note = paragraphs(catBody.split('\n').filter((l) => !l.trim().startsWith('|')).join('\n'));
    const blocks = table.rows.map((cells) => {
      const block = parseBlock(cells);
      if (seen.has(block.name)) throw new CatalogError(`block "${block.name}" is listed twice`);
      seen.add(block.name);
      return block;
    });
    categories.push({ name: title, note: note.length ? note.join(' ') : null, blocks });
  }
  if (categories.length === 0) throw new CatalogError('"## All Blocks" contains no category tables');
  return categories;
}

function parseHero(body) {
  const blocks = fences(body);
  const install = blocks.find((b) => b.source.includes('libraryDependencies'));
  if (!install) throw new CatalogError('Getting Started: no libraryDependencies code block');
  const line = blocks.flatMap((b) => b.source.split('\n')).find((l) => l.includes('toJsonString') && l.includes('//'));
  const m = line && /^(.*?)\s*\/\/\s*(.+)$/.exec(line);
  if (!m) throw new CatalogError('Getting Started: no code line containing toJsonString with a // result comment');
  return { install: install.source.trim(), jsonCode: m[1].trim(), jsonResult: m[2].trim() };
}

function parseDive([id, title], sections) {
  const body = section(sections, title);
  const sub = splitSections(body, 3);
  const need = (t) => {
    const found = sub.sections.find((s) => s.title === t);
    if (!found) throw new CatalogError(`${title}: no "### ${t}" section`);
    return found.body;
  };
  const problem = need('The Problem');
  const solution = need('The Solution');
  const solutionCode = fences(solution)[0];
  if (!solutionCode) throw new CatalogError(`${title}: "The Solution" has no code block`);
  const intro = paragraphs(sub.preamble)[0];
  if (!intro) throw new CatalogError(`${title}: no intro paragraph`);
  return {
    id,
    title,
    intro,
    problem: { paragraphs: paragraphs(proseBeforeFence(problem)), code: fences(problem)[0] ?? null },
    solution: { paragraphs: paragraphs(proseBeforeFence(solution)), code: solutionCode },
    learnMore: bullets(need('Learn More')).map((item) => {
      const m = /^\[(.+?)\]\((.+?)\) [—-] (.+)$/.exec(item);
      if (!m) throw new CatalogError(`${title}: unparsable Learn More item "${item}"`);
      return { title: m[1], url: linkTarget(m[2]), description: m[3] };
    }),
  };
}

/** Parses docs/index.md. `hero.install` still contains the `@VERSION@` placeholder. */
export function parseIndex(md) {
  const top = splitSections(md, 2);
  const tagline = /^\*\*(.+)\*\*$/m.exec(top.preamble)?.[1];
  if (!tagline) throw new CatalogError('docs/index.md has no bold tagline line before the first section');

  const lead = paragraphs(section(top.sections, 'What Is ZIO Blocks?'))[1];
  if (!lead) throw new CatalogError('"What Is ZIO Blocks?" has no second paragraph');

  const principles = bullets(section(top.sections, 'Core Principles')).map((item) => {
    const m = /^\*\*(.+?)\*\*: (.+)$/.exec(item);
    if (!m) throw new CatalogError(`unparsable principle "${item}"`);
    return { name: m[1], text: firstSentence(m[2]) };
  });

  const compat = parseTable(section(top.sections, 'Compatibility'));
  const compatibility = (compat?.rows ?? []).filter((r) => r[1].includes('✅')).map((r) => r[0]);

  return {
    tagline,
    lead,
    principles,
    hero: parseHero(section(top.sections, 'Getting Started')),
    compatibility,
    categories: parseCategories(section(top.sections, 'All Blocks')),
    deepDives: DIVES.map((d) => parseDive(d, top.sections)),
    guides: bullets(section(top.sections, 'Guides')).map((item) => {
      const m = /^\[(.+?)\]\((.+?)\) - (.+)$/.exec(item);
      if (!m) throw new CatalogError(`unparsable guide "${item}"`);
      return { title: m[1], url: docsUrl(m[2]), description: m[3] };
    }),
  };
}
