// @ts-check
const prismThemes = require('./src/prism-themes');
const { algoliaConfig } = require('./src/algolia');

// LANDING_ONLY=1 builds just the landing page (no docs plugin, so no mdoc output or sbt is needed); broken links to
// the docs only warn then. The full build, which CI deploys, never sets it.
const LANDING_ONLY = process.env.LANDING_ONLY === '1';

// Google Analytics 4 (property "zioblocks.com"). The "Deploy Website" workflow (.github/workflows/site.yml) sets
// CONTEXT=production only for the production build; PR previews are built in CI without it, so they never send page views.
const GA_MEASUREMENT_ID = 'G-D1FMWLNJ8K';
const ANALYTICS = process.env.CONTEXT === 'production';

// The docs search box (Algolia DocSearch). Undefined unless the ALGOLIA_* variables are set; see src/algolia.js and README.md.
const ALGOLIA = algoliaConfig();

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
              // docs/plans, docs/dev, docs/superpowers and docs/undocumented-report.md hold internal planning notes and reports: keep them in the repo but out of the site. This replaces
              // Docusaurus' default excludes, so they are repeated here.
              exclude: ['**/_*.{js,jsx,ts,tsx,md,mdx}', '**/_*/**', '**/*.test.{js,jsx,ts,tsx}', '**/__tests__/**', 'plans/**', 'dev/**', 'superpowers/**', 'undocumented-report.md'],
            },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
        blog: false,
        gtag: ANALYTICS ? { trackingID: GA_MEASUREMENT_ID, anonymizeIP: true } : undefined,
      },
    ],
  ],
  // /guides and /reference become /guides/ and /reference/ (src/clientModules/folderIndexes.js).
  clientModules: [require.resolve('./src/clientModules/folderIndexes.js')],
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
              ignoreFiles: ['plans/**', 'dev/**'],
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
      ...(ALGOLIA ? { algolia: ALGOLIA } : {}),
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
          // The getting-started sidebar (docs/sidebars.js): the overview page, then the newcomers' tour under
          // /getting-started/. activeBaseRegex keeps the item highlighted on every page of that sidebar.
          { to: '/overview', label: 'Getting Started', position: 'right', activeBaseRegex: '^/(overview|getting-started)(/|$)' },
          // The reference index; activeBaseRegex keeps it highlighted on every page of the reference sidebar.
          { to: '/reference/', label: 'Reference', position: 'right', activeBaseRegex: '^/reference/' },
          // A plain link, not a docSidebar item: the landing-only build has no docs plugin. activeBaseRegex keeps it
          // highlighted on every page of the guides sidebar (docs/sidebars.js).
          { to: '/guides/', label: 'Guides', position: 'right', activeBaseRegex: '^/guides/' },
          // The Scaladoc is plain HTML copied into static/api by `sbt docs/generateApiDocs`, outside the Docusaurus
          // router: pathname:// makes it a full page load and keeps the broken link checker off it.
          { to: 'pathname:///api/', label: 'API', position: 'right', target: '_self' },
          // A hash link to the landing page: never mark it active. Docusaurus' default prefix match on `/` would light it
          // up on every docs page, now that docs are served at the site root. A pathname is never empty, so '^$' never matches.
          { to: '/#catalog', label: 'Blocks', position: 'right', activeBaseRegex: '^$' },
          { href: 'https://github.com/zio/zio-blocks', position: 'right', className: 'header-github-link', 'aria-label': 'GitHub repository' },
          { href: 'https://discord.gg/2ccFBr4', position: 'right', className: 'header-discord-link', 'aria-label': 'Join the ZIO Discord server' },
        ],
      },
      // Laid out by src/theme/Footer/Layout (logo and tagline beside the columns) and styled by `.zb-footer` in
      // src/css/custom.css; the colours come from there, not from Infima's footer styles.
      footer: {
        logo: {
          alt: 'ZIO Blocks',
          src: 'brand/zio-blocks-logo-mono-white.svg',
          href: '/',
          width: 160,
          height: 37,
        },
        links: [
          {
            title: 'Learn',
            items: [
              { label: 'Getting Started', to: '/overview' },
              { label: 'Guides', to: '/guides/' },
              { label: 'Reference', to: '/reference/' },
              { label: 'API', to: 'pathname:///api/', target: '_self' },
            ],
          },
          {
            title: 'Community',
            items: [
              { label: 'GitHub', href: 'https://github.com/zio/zio-blocks' },
              { label: 'Discord', href: 'https://discord.gg/2ccFBr4' },
            ],
          },
          {
            title: 'ZIO ecosystem',
            items: [
              { label: 'ZIO', href: 'https://zio.dev' },
              { label: 'ZIO HTTP', href: 'https://ziohttp.com' },
            ],
          },
        ],
        // Copyright, licence and credit on one line; the year is the build year.
        copyright: `Copyright © ${new Date().getFullYear()} ZIO Maintainers · Licensed under the <a href="https://github.com/zio/zio-blocks/blob/main/LICENSE">Apache License 2.0</a> · Built with <a href="https://docusaurus.io/">Docusaurus</a>`,
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
