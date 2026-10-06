import { defineConfig } from 'astro/config';

// Netlify exposes the primary site URL as `URL`; fall back to the local dev server.
export default defineConfig({
  site: process.env.URL ?? 'https://localhost:4321',
  output: 'static',
  // Keep template whitespace so the static-output tests extract text deterministically.
  compressHTML: false,
  devToolbar: { enabled: false },
});
