// The docs folders that have an index page (docs/<folder>/index.md) are served at /<folder>/, with the slash. Docusaurus
// renders /guides the same, but its links resolve and read as /guides/; give such a URL its slash, in place, without a
// reload. (The hosts serve the same page for both forms, so this runs in the browser rather than as a redirect rule.)
// /api has its own redirect in the Scaladoc index page (scripts/lib/api-theme.mjs).
export const FOLDER_INDEXES = ['/guides', '/reference'];

export function withFolderSlash({ pathname, search, hash }) {
  return FOLDER_INDEXES.includes(pathname) ? `${pathname}/${search}${hash}` : null;
}

export function onRouteDidUpdate({ location }) {
  const target = withFolderSlash(location);
  if (target) window.history.replaceState(window.history.state, '', target);
}
