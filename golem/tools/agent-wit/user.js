// Loader stub for wasm-rquickjs.
//
// golem-cli's inject step provides the real JS bundle via the imported
// `get-script` function, exposed to QuickJS as a virtual module named
// `@composition`.
export * from '@composition';
