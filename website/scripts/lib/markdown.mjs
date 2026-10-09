/** Raised for any problem with the shape of docs/index.md; the message names the offender. */
export class CatalogError extends Error {
  constructor(message) {
    super(message);
    this.name = 'CatalogError';
  }
}

/** Marks each line as inside or outside a fenced code block. The fence lines themselves count as fenced. */
function annotate(md) {
  let open = null;
  return md.split('\n').map((text) => {
    if (open === null) {
      const m = /^(`{3,})/.exec(text);
      if (m) {
        open = m[1];
        return { text, fenced: true };
      }
      return { text, fenced: false };
    }
    if (text.startsWith(open) && text.slice(open.length).trim() === '') open = null;
    return { text, fenced: true };
  });
}

/**
 * Splits `md` on headings of exactly `level` hashes. Deeper headings stay in the section body; a
 * shallower heading ends the current section (its own content is not returned).
 */
export function splitSections(md, level) {
  const marker = '#'.repeat(level) + ' ';
  const raw = [{ title: null, lines: [] }];
  for (const { text, fenced } of annotate(md)) {
    const hashes = fenced ? null : /^(#{1,6}) /.exec(text);
    if (hashes && hashes[1].length === level) {
      raw.push({ title: text.slice(marker.length).trim(), lines: [] });
    } else if (hashes && hashes[1].length < level) {
      raw.push({ title: null, lines: [] });
    } else {
      raw[raw.length - 1].lines.push(text);
    }
  }
  return {
    preamble: raw[0].lines.join('\n'),
    sections: raw.slice(1).filter((s) => s.title !== null).map((s) => ({ title: s.title, body: s.lines.join('\n') })),
  };
}

/** Fenced code blocks in order. `lang` is the first word of the info string (mdoc modifiers dropped). */
export function fences(body) {
  const out = [];
  let open = null;
  for (const line of body.split('\n')) {
    if (open === null) {
      const m = /^(`{3,})\s*(.*)$/.exec(line);
      if (m) open = { ticks: m[1], info: m[2].trim(), lines: [] };
    } else if (line.startsWith(open.ticks) && line.slice(open.ticks.length).trim() === '') {
      out.push({ info: open.info, lang: open.info.split(/\s+/)[0] || 'text', source: open.lines.join('\n') });
      open = null;
    } else {
      open.lines.push(line);
    }
  }
  return out;
}

/** Blank-line separated paragraphs with wrapped lines joined by a space; horizontal rules dropped. */
export function paragraphs(text) {
  return text
    .split(/\n\s*\n/)
    .map((p) => p.split('\n').map((l) => l.trim()).filter((l) => l !== '' && !/^-{3,}$/.test(l)).join(' '))
    .filter((p) => p !== '');
}

/** Everything before the first fenced block, trimmed. */
export function proseBeforeFence(body) {
  const lines = [];
  for (const line of body.split('\n')) {
    if (/^`{3,}/.test(line)) break;
    lines.push(line);
  }
  return lines.join('\n').trim();
}

/** The first pipe table in unfenced `body`, or null. Rows must match the header's cell count. */
export function parseTable(body) {
  const lines = annotate(body).filter((l) => !l.fenced).map((l) => l.text.trim());
  const start = lines.findIndex((l) => l.startsWith('|'));
  if (start === -1) return null;
  let end = start;
  while (end < lines.length && lines[end].startsWith('|')) end += 1;
  if (lines.slice(end).some((l) => l.startsWith('|'))) throw new CatalogError('body has more than one table');
  const rows = lines.slice(start, end);
  if (rows.length < 2) return null;
  const cells = (line) =>
    line.replace(/^\|/, '').replace(/(?<!\\)\|$/, '').split(/(?<!\\)\|/).map((c) => c.trim().replaceAll('\\|', '|'));
  const header = cells(rows[0]);
  if (!cells(rows[1]).every((c) => /^:?-+:?$/.test(c))) {
    throw new CatalogError(`table after header "${rows[0]}" has no separator row`);
  }
  const parsed = rows.slice(2).map((line) => {
    const row = cells(line);
    if (row.length !== header.length) {
      throw new CatalogError(`table row "${line}" has ${row.length} cells, expected ${header.length}`);
    }
    return row;
  });
  return { header, rows: parsed };
}

/** Top-level `- ` or `* ` list items outside fenced code (nested items are ignored). */
export function bullets(body) {
  return annotate(body)
    .filter((l) => !l.fenced && /^[-*] /.test(l.text))
    .map((l) => l.text.slice(2).trim());
}
