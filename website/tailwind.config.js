/** @type {import('tailwindcss').Config} */
module.exports = {
  important: true,
  content: ['./src/**/*.{js,jsx,ts,tsx}'],
  darkMode: ['class', '[data-theme="dark"]'], // hooks into docusaurus' dark mode
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: 'var(--ifm-color-primary)',
        },
      },
    },
  },
  plugins: [],
};
