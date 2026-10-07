// Flat Prism themes in the brand palette. Used by docusaurus.config.js and by the landing page's hero code card.
const dark = {
  plain: { color: '#f3f5fc', backgroundColor: '#0d1224' },
  styles: [
    { types: ['comment', 'prolog', 'doctype', 'cdata'], style: { color: '#7c859e' } },
    { types: ['punctuation'], style: { color: '#f3f5fc' } },
    { types: ['keyword', 'operator', 'tag', 'deleted'], style: { color: '#ff7b72' } },
    { types: ['string', 'char', 'attr-value', 'inserted'], style: { color: '#a5d6ff' } },
    { types: ['number', 'boolean', 'constant', 'symbol'], style: { color: '#79c0ff' } },
    { types: ['function', 'class-name', 'builtin', 'attr-name'], style: { color: '#ffa657' } },
    { types: ['variable', 'parameter'], style: { color: '#f3f5fc' } },
  ],
};

const light = {
  plain: { color: '#141a2e', backgroundColor: '#f3f5fc' },
  styles: [
    { types: ['comment', 'prolog', 'doctype', 'cdata'], style: { color: '#5b6580' } },
    { types: ['punctuation'], style: { color: '#141a2e' } },
    { types: ['keyword', 'operator', 'tag', 'deleted'], style: { color: '#b31d28' } },
    { types: ['string', 'char', 'attr-value', 'inserted'], style: { color: '#032f62' } },
    { types: ['number', 'boolean', 'constant', 'symbol'], style: { color: '#005cc5' } },
    { types: ['function', 'class-name', 'builtin', 'attr-name'], style: { color: '#6f42c1' } },
    { types: ['variable', 'parameter'], style: { color: '#141a2e' } },
  ],
};

module.exports = { light, dark };
