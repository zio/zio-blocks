/**
 * The setup prompt copied by the "Onboard your agent to ZIO Blocks" buttons. `siteUrl` is the site origin from the
 * Docusaurus config (`SITE_URL` at build time); a trailing slash is dropped so the link never has a double slash.
 */
export function promptFor(siteUrl) {
  const origin = siteUrl.replace(/\/+$/, '');
  return `Fetch ${origin}/start.md and follow the instructions to set up my environment for ZIO Blocks development.`;
}
