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
           link: { type: "doc", id: "reference/schema/index" },
           items: [
             {
               type: "category",
               label: "Core Type System",
               collapsed: false,
               items: [
                 "reference/schema/schema",
                 "reference/schema/reflect",
                 "reference/schema/binding",
                 "reference/schema/registers",
                 "reference/schema/binding-resolver",
                 "reference/schema/modifier",
                 "reference/schema/structural-types",
               ]
             },
             {
               type: "category",
               label: "Dynamic Values",
               collapsed: false,
               items: [
                 "reference/schema/dynamic-value",
                 "reference/schema/dynamic-schema",
               ]
             },
             {
               type: "category",
               label: "Reflective Optics",
               collapsed: false,
               items: [
                 "reference/schema/optics",
                 "reference/schema/dynamic-optic",
                 "reference/schema/schema-expr",
                 "reference/schema/patch",
               ]
             },
             {
               type: "category",
               label: "Serialization",
               collapsed: false,
               items: [
                 "reference/schema/type-class-derivation",
                 "reference/schema/codec",
                 {
                   type: "category",
                   label: "Formats",
                   link: { type: "doc", id: "reference/schema/formats" },
                   collapsed: false,
                   items: [
                     {
                       type: "category",
                       label: "Json Format",
                       link: { type: "doc", id: "reference/schema/formats" },
                       collapsed: false,
                       items: [
                         "reference/schema/json",
                         "reference/schema/json-patch",
                         "reference/schema/json-differ",
                         "reference/schema/json-schema",
                       ]
                     },
                     "reference/schema/xml",
                   ]
                 },
                 "reference/schema/lazy",
               ]
             },
             {
               type: "category",
               label: "Validation & Errors",
               collapsed: false,
               items: [
                 "reference/schema/validation",
                 "reference/schema/schema-error",
                 "reference/schema/allows",
               ]
             },
             {
               type: "category",
               label: "Schema Evolution",
               link: { type: "doc", id: "reference/schema/schema-evolution/index" },
               items: [
                 "reference/schema/schema-evolution/into",
                 "reference/schema/schema-evolution/as",
               ]
             },
             "reference/schema/syntax",
           ]
         },
         "reference/typeid",
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
          {
            type: "category",
            label: "ZIO Blocks HTTP Model",
            link: { type: "doc", id: "reference/http-model/index" },
            items: [
              "reference/http-model/model",
              "reference/http-model/schema",
            ]
          },
          {
            type: "category",
            label: "ZIO Blocks Endpoint",
            link: { type: "doc", id: "reference/endpoint/index" },
            items: [
              "reference/endpoint/endpoint",
              "reference/endpoint/http-codec",
              "reference/endpoint/route-pattern",
              "reference/endpoint/path-codec",
              "reference/endpoint/segment-codec",
              "reference/endpoint/auth-type",
              "reference/endpoint/route-tree",
            ]
          },
          "reference/chunk",
          "reference/maybe",
          "path-interpolator",
          "reference/ringbuffer",
          "reference/html",
          "reference/datastar",
          {
            type: "category",
            label: "ZIO Blocks Streams",
            link: { type: "doc", id: "reference/streams/index" },
            items: [
             "reference/streams/stream",
             "reference/streams/pipeline",
             "reference/streams/sink",
             "reference/streams/reader",
             "reference/streams/writer",
             "reference/streams/zero-boxing",
           ]
         },
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
