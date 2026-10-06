const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

export const escapeHtml = (s) => s.replace(/[&<>"']/g, (c) => ESCAPES[c]);

/**
 * Renders the small markdown subset used in docs/index.md prose: `code`, **bold**, *emphasis*,
 * [links](url). Everything else is escaped. Code spans and links are protected from other rules.
 * Link URLs are validated (only http, https, mailto, and relative URLs are allowed).
 * Link output (href and label) is escaped to prevent injection.
 */
export function renderInline(text, linkFor = (url) => url) {
  const codes = [];
  const links = [];

  const isSafeUrl = (url) => {
    const scheme = /^([a-z]+):/.exec(url);
    if (!scheme) return true; // Relative URLs and fragments are safe
    return ['http', 'https', 'mailto'].includes(scheme[1]);
  };

  // Extract links first (validate schemes, apply linkFor, escape output; protects URLs from code/emphasis processing)
  const withLinkSlots = text.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, label, url) => {
    if (isSafeUrl(url)) {
      const mapped = linkFor(url);
      links.push(`<a href="${escapeHtml(mapped)}">${escapeHtml(label)}</a>`);
    } else {
      // Unsafe URL scheme - render just the escaped label
      links.push(escapeHtml(label));
    }
    return `\u0001${links.length - 1}\u0001`;
  });

  // Extract code spans (protect from emphasis/bold processing)
  const withCodeSlots = withLinkSlots.replace(/`([^`]+)`/g, (_, code) => {
    codes.push(`<code>${escapeHtml(code)}</code>`);
    return `\u0000${codes.length - 1}\u0000`;
  });

  // Escape HTML in remaining text
  const escaped = escapeHtml(withCodeSlots);

  // Process emphasis and bold
  const withEmphasis = escaped
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>');

  // Restore code spans
  const withCodes = withEmphasis.replace(/\u0000(\d+)\u0000/g, (_, i) => codes[Number(i)]);

  // Restore links
  return withCodes.replace(/\u0001(\d+)\u0001/g, (_, i) => links[Number(i)]);
}
