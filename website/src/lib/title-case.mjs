// Articles, conjunctions and short prepositions stay lowercase in a title unless they are the first word.
const SMALL = new Set(['a', 'an', 'the', 'and', 'but', 'or', 'nor', 'for', 'so', 'yet', 'as', 'at', 'by', 'in', 'of', 'on', 'to', 'up']);

const upFirst = (s) => (s === '' ? s : s[0].toUpperCase() + s.slice(1));

/**
 * Title-cases `text`: the first letter of every word, and of every part of a hyphenated word, is upper-cased, except
 * small words that are not first. The rest of each word is left alone, so acronyms (`ZIO`) and camelCase
 * (`jsonCodec` -> `JsonCodec`) keep their shape.
 */
export function titleCase(text) {
  return text
    .split(' ')
    .map((word, i) => (i > 0 && SMALL.has(word) ? word : word.split('-').map(upFirst).join('-')))
    .join(' ');
}
