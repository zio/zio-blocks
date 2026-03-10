const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Blocks",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
         "reference/schema",
         "reference/allows",
         "reference/reflect",
         "reference/binding",
         "reference/binding-resolver",
         "reference/registers",
         "reference/typeid",
         "reference/allows",
         "reference/modifier",
         "reference/dynamic-value",
         "reference/dynamic-schema",
         "reference/lazy",
         "reference/structural-types",
         "reference/optics",
         "reference/patch",
         "reference/schema-expr",
         "reference/dynamic-optic",
         "reference/type-class-derivation",
         "reference/codec",
         "reference/formats",
         "path-interpolator",
         "reference/chunk",
         "reference/schema-error",
         "reference/validation",
         {
           type: "category",
           label: "Schema Evolution",
           link: { type: "doc", id: "reference/schema-evolution/index" },
           items: [
             "reference/schema-evolution/into",
             "reference/schema-evolution/as",
           ]
         },
         "reference/context",
         "reference/scope",
         "reference/wire",
         "reference/docs",
         "reference/json",
         "reference/json-patch",
         "reference/json-schema",
         "reference/xml",
         "reference/syntax",
         "reference/media-type",
      ]
    },
    {
      type: "category",
      label: "Guides",
      items: [
        "guides/zio-schema-migration",
        "guides/query-dsl-reified-optics",
        "guides/query-dsl-sql",
        "guides/query-dsl-extending",
        "guides/query-dsl-fluent-builder",
      ]
    }
  ]
};

module.exports = sidebars;
