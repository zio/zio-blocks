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
           link: { type: "doc", id: "reference/zio-blocks-schema/zio-blocks-schema" },
           items: [
             "reference/zio-blocks-schema/allows",
             "reference/zio-blocks-schema/binding",
             "reference/zio-blocks-schema/codec",
             "reference/zio-blocks-schema/dynamic-optic",
             "reference/zio-blocks-schema/dynamic-schema",
             "reference/zio-blocks-schema/dynamic-value",
             "reference/zio-blocks-schema/formats",
             "reference/zio-blocks-schema/json",
             "reference/zio-blocks-schema/json-differ",
             "reference/zio-blocks-schema/json-patch",
             "reference/zio-blocks-schema/json-schema",
             "reference/zio-blocks-schema/lazy",
             "reference/zio-blocks-schema/modifier",
             "reference/zio-blocks-schema/optics",
             "reference/zio-blocks-schema/patch",
             "reference/zio-blocks-schema/reflect",
             "reference/zio-blocks-schema/registers",
             "reference/zio-blocks-schema/schema",
             "reference/zio-blocks-schema/schema-error",
             "reference/zio-blocks-schema/schema-expr",
             "reference/zio-blocks-schema/structural-types",
             "reference/zio-blocks-schema/syntax",
             "reference/zio-blocks-schema/type-class-derivation",
             "reference/zio-blocks-schema/validation",
             "reference/zio-blocks-schema/xml",
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
