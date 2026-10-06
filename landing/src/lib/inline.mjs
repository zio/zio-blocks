const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

export const escapeHtml = (s) => s.replace(/[&<>"']/g, (c) => ESCAPES[c]);

/**
 * Renders the small markdown subset used in docs/index.md prose: `code`, **bold**, *emphasis*,
 * [links](url). Everything else is escaped. Code spans are protected from the other rules.
 */
export function renderInline(text, linkFor = (url) => url) {
  const codes = [];
  const withSlots = text.replace(/`([^`]+)`/g, (_, code) => {
    codes.push(`<code>${escapeHtml(code)}</code>`);
    return `\u0000${codes.length - 1}\u0000`;
  });
  return escapeHtml(withSlots)
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, label, url) => `<a href="${linkFor(url)}">${label}</a>`)
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/\u0000(\d+)\u0000/g, (_, i) => codes[Number(i)]);
}
