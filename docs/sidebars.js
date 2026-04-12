const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Blocks",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
         {
           type: "category",
           label: "ZIO Blocks Schema",
           link: { type: "doc", id: "reference/schema/schema-index" },
           items: [
             "reference/schema/allows",
             "reference/schema/binding",
             "reference/schema/codec",
             "reference/schema/dynamic-optic",
             "reference/schema/dynamic-schema",
             "reference/schema/dynamic-value",
             "reference/schema/formats",
             "reference/schema/json",
             "reference/schema/json-differ",
             "reference/schema/json-patch",
             "reference/schema/json-schema",
             "reference/schema/lazy",
             "reference/schema/modifier",
             "reference/schema/optics",
             "reference/schema/patch",
             "reference/schema/reflect",
             "reference/schema/registers",
             "reference/schema/schema",
             "reference/schema/schema-error",
             "reference/schema/schema-expr",
             "reference/schema/structural-types",
             "reference/schema/syntax",
             "reference/schema/type-class-derivation",
             "reference/schema/validation",
             "reference/schema/xml",
           ]
         },
         "reference/binding-resolver",
         "reference/typeid",
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
         "reference/media-type",
         "reference/http-model",
         "reference/streams",
         "reference/chunk",
         "path-interpolator",
         "reference/ringbuffer",
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
