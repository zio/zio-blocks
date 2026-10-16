// @ts-check
const prismThemes = require('./src/prism-themes');

// LANDING_ONLY=1 builds just the landing page (no docs plugin, so no mdoc output or sbt is needed); broken links to
// the docs only warn then. The full build, which CI deploys, never sets it.
const LANDING_ONLY = process.env.LANDING_ONLY === '1';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'ZIO Blocks',
  tagline: 'Type-safe, modular building blocks for Scala',
  // The production domain. SITE_URL overrides it (previews, demos, local experiments); canonical links, the sitemap, llms.txt
  // and the onboard-agent prompt are all built from it.
  url: process.env.SITE_URL || 'https://zioblocks.com',
  baseUrl: '/',
  onBrokenLinks: LANDING_ONLY ? 'warn' : 'throw',
  onDuplicateRoutes: 'throw',
  markdown: {
    hooks: {
      onBrokenMarkdownLinks: LANDING_ONLY ? 'warn' : 'throw',
    },
  },
  favicon: 'brand/zio-blocks-mark-favicon.svg',

  organizationName: 'zio',
  projectName: 'zio-blocks',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },
  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      {
        docs: LANDING_ONLY
          ? false
          : {
              id: 'default',
              path: './docs',
              // Docs live at the site root (no /docs prefix). The landing page owns `/`, so scripts/set-docs-home.mjs gives the docs home the slug /overview.
              routeBasePath: '/',
              sidebarPath: require.resolve('../docs/sidebars.js'),
              // docs/plans holds internal planning notes: keep them in the repo but out of the site. This replaces
              // Docusaurus' default excludes, so they are repeated here.
              exclude: ['**/_*.{js,jsx,ts,tsx,md,mdx}', '**/_*/**', '**/*.test.{js,jsx,ts,tsx}', '**/__tests__/**', 'plans/**'],
            },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
        blog: false,
      },
    ],
  ],
  plugins: [
    // Tailwind (utilities only, see src/css/custom.css) for the Onboard Agent button.
    async function tailwindPlugin() {
      return {
        name: 'docusaurus-tailwindcss',
        configurePostCss(postcssOptions) {
          postcssOptions.plugins.push(require('@tailwindcss/postcss'));
          return postcssOptions;
        },
      };
    },
    // llms.txt, llms-full.txt and per-page markdown for coding agents. It reads website/docs, which the landing-only
    // build does not have, so it is registered only for the full build.
    ...(LANDING_ONLY
      ? []
      : [
          [
            'docusaurus-plugin-llms',
            /** @type {import('docusaurus-plugin-llms').PluginOptions} */
            ({
              generateLLMsTxt: true,
              generateLLMsFullTxt: true,
              generateMarkdownFiles: true,
              docsDir: 'docs',
              includeBlog: false,
              // Keep internal planning notes out of llms.txt and the per-page markdown too.
              ignoreFiles: ['plans/**'],
              // Docs are served at the site root, so drop the docs/ directory from the generated markdown paths: the links
              // in llms.txt are then /<page>.md, next to the pages they describe.
              preserveDirectoryStructure: false,
              excludeImports: true,
              removeDuplicateHeadings: true,
              includeUnmatchedLast: true,
            }),
          ],
        ]),
  ],
  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'brand/zio-blocks-social-og.png',
      colorMode: {
        defaultMode: 'light',
        respectPrefersColorScheme: true,
      },
      navbar: {
        logo: {
          alt: 'ZIO Blocks',
          src: 'brand/zio-blocks-logo-on-dark.svg',
          width: 160,
          height: 37,
        },
        items: [
          { to: '/overview', label: 'Docs', position: 'right' },
          // A plain link, not a docSidebar item: the landing-only build has no docs plugin. activeBaseRegex keeps it
          // highlighted on every page of the guides sidebar (docs/sidebars.js).
          { to: '/guides/async-getting-started', label: 'Guides', position: 'right', activeBaseRegex: '^/guides/' },
          // A hash link to the landing page: never mark it active. Docusaurus' default prefix match on `/` would light it
          // up on every docs page, now that docs are served at the site root. A pathname is never empty, so '^$' never matches.
          { to: '/#catalog', label: 'Blocks', position: 'right', activeBaseRegex: '^$' },
          { href: 'https://github.com/zio/zio-blocks', label: 'GitHub', position: 'right' },
        ],
      },
      footer: {
        style: 'dark',
        logo: {
          alt: 'ZIO Blocks',
          src: 'brand/zio-blocks-logo-mono-white.svg',
          width: 160,
          height: 37,
        },
        links: [
          {
            items: [
              { label: 'Docs', to: '/overview' },
              { label: 'Reference', to: '/reference/schema/' },
              { label: 'GitHub', href: 'https://github.com/zio/zio-blocks' },
            ],
          },
        ],
      },
      prism: {
        theme: prismThemes.light,
        darkTheme: prismThemes.dark,
        // Scala extends Java in Prism, so Java must be loaded first.
        additionalLanguages: ['java', 'scala', 'bash', 'sql'],
      },
    }),
};

module.exports = config;
