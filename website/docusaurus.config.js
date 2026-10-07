// @ts-check
const prismThemes = require('./src/prism-themes');

// LANDING_ONLY=1 builds just the landing page (no docs plugin, so no mdoc output or sbt is needed); broken links to
// /docs only warn then. The full build, which CI deploys, never sets it.
const LANDING_ONLY = process.env.LANDING_ONLY === '1';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'ZIO Blocks',
  tagline: 'Type-safe, modular building blocks for Scala',
  url: process.env.SITE_URL || 'http://localhost:3000',
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
              routeBasePath: '/docs',
              sidebarPath: require.resolve('../docs/sidebars.js'),
            },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
        blog: false,
      },
    ],
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
          { to: '/docs/', label: 'Docs', position: 'right' },
          { to: '/#catalog', label: 'Blocks', position: 'right' },
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
              { label: 'Docs', to: '/docs/' },
              { label: 'Reference', to: '/docs/reference/schema/' },
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
