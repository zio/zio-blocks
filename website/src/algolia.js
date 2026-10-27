// Algolia DocSearch settings for the docs search box. Used by docusaurus.config.js. The three values come from the
// environment, so the search box appears only where they are set (the production build, once the index exists). Without
// them there is no search box, rather than one that searches an empty or wrong index.
const ALGOLIA_ENV = {
  appId: 'ALGOLIA_APP_ID',
  // The search-only key. It is public by design, but it stays out of the repository so the index can change without a commit.
  apiKey: 'ALGOLIA_SEARCH_API_KEY',
  indexName: 'ALGOLIA_INDEX_NAME',
};

/** The `themeConfig.algolia` object, or `undefined` when the search is not configured. Warns when only some values are set. */
function algoliaConfig(env = process.env) {
  const values = Object.entries(ALGOLIA_ENV).map(([key, name]) => [key, (env[name] || '').trim()]);
  const set = values.filter(([, value]) => value !== '');
  if (set.length === 0) return undefined;
  if (set.length < values.length) {
    const missing = values.filter(([, value]) => value === '').map(([key]) => ALGOLIA_ENV[key]);
    console.warn(`Algolia search is off: ${missing.join(', ')} not set.`);
    return undefined;
  }
  return Object.fromEntries(values);
}

module.exports = { ALGOLIA_ENV, algoliaConfig };
