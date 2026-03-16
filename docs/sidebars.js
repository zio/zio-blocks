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
         "patch",
        "reference/migration",
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
         {
           type: "category",
           label: "Resource Management & DI",
           link: { type: "doc", id: "reference/resource-management/index" },
           items: [
             "reference/resource-management/scope",
             "reference/resource-management/resource",
             "reference/resource-management/wire",
             "reference/resource-management/unscoped",
             "reference/resource-management/defer-handle",
             "reference/resource-management/finalizer",
             "reference/resource-management/finalization",
           ]
         },
         "reference/combinators",
         "reference/docs",
         "reference/json",
         "reference/json-patch",
         "reference/json-differ",
         "reference/json-schema",
         "reference/xml",
         "reference/syntax",
         "reference/media-type",
         "reference/http-model",
         "ringbuffer",
      ]
    },
    {
      type: "category",
      label: "Guides",
      items: [
        "guides/compile-time-resource-safety-with-scope",
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
