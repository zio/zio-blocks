const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Blocks",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
         "reference/schema",
         "reference/reflect",
         "reference/binding",
         "reference/registers",
         "reference/typeid",
         "reference/modifier",
         "reference/dynamic-value",
         "reference/optics",
         "reference/schema-expr",
         "reference/dynamic-optic",
         "reference/type-class-derivation",
         "reference/codec",
         "reference/formats",
         "path-interpolator",
         "reference/chunk",
         "reference/schema-error",
         "reference/validation",
         "reference/schema-evolution",
         "reference/context",
         "reference/docs",
         "reference/json",
         "reference/json-schema",
         "reference/syntax",
         "reference/media-type",
      ]
    },
    {
      type: "category",
      label: "Guides",
      items: [
        "guides/query-dsl-reified-optics",
        "guides/query-dsl-sql",
        "guides/query-dsl-extending",
        "guides/query-dsl-fluent-builder",
      ]
    }
  ]
};

module.exports = sidebars;
