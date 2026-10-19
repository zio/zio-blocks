// Organizations that use ZIO Blocks, shown in the landing page's "Who Is Using ZIO Blocks?" section in this order.
// `logo` is a path under static/ (one SVG per organization in static/img/users/); `logoDark`, when present, replaces it in
// dark mode, for a logo drawn in a single dark colour. `width` and `height` are the logo's viewBox size, so the page
// reserves the right space before it loads. `description` is one short line.
export const USERS = [
  {
    name: 'Ziverge',
    url: 'https://ziverge.com',
    logo: '/img/users/ziverge.svg',
    width: 380,
    height: 70,
    description: 'Brilliant solutions for innovative companies.',
  },
  {
    name: 'Golem',
    url: 'https://golem.cloud',
    logo: '/img/users/golem.svg',
    logoDark: '/img/users/golem-dark.svg',
    width: 450,
    height: 114,
    description: 'The durable runtime for agents, their tools, and their artifacts.',
  },
];

// Where the "join the list" link sends a visitor: GitHub's editor for this file, which opens a pull request.
export const JOIN_URL = 'https://github.com/zio/zio-blocks/edit/main/website/src/lib/users.mjs';
