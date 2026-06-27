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
           label: "Schema",
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
                 "reference/schema/path-interpolator",
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
                 "reference/schema/format",
                 "reference/schema/lazy",
               ]
             },
             {
               type: "category",
               label: "Built-in Formats and Codecs",
               link: { type: "doc", id: "reference/schema/built-in-codecs/index" },
               collapsed: false,
               items: [
                 {
                   type: "category",
                   label: "JSON Codec",
                   link: { type: "doc", id: "reference/schema/built-in-codecs/json/index" },
                   collapsed: false,
                   items: [
                     "reference/schema/built-in-codecs/json/json",
                     "reference/schema/built-in-codecs/json/json-config",
                     "reference/schema/built-in-codecs/json/json-patch",
                     "reference/schema/built-in-codecs/json/json-differ",
                     "reference/schema/built-in-codecs/json/json-selection",
                     "reference/schema/built-in-codecs/json/json-schema",
                   ]
                 },
                 "reference/schema/built-in-codecs/avro",
                 "reference/schema/built-in-codecs/bson",
                 "reference/schema/built-in-codecs/csv",
                 "reference/schema/built-in-codecs/messagepack",
                 "reference/schema/built-in-codecs/thrift",
                 "reference/schema/built-in-codecs/toon",
                 "reference/schema/built-in-codecs/xml",
                 "reference/schema/built-in-codecs/yaml",
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
                  "reference/schema/migration",
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
          "reference/config",
          "reference/media-type",
         {
           type: "category",
           label: "Code Generation",
           link: { type: "doc", id: "reference/codegen/index" },
           items: [
             "reference/codegen/scala-file",
             "reference/codegen/type-definition",
             "reference/codegen/case-class",
             "reference/codegen/sealed-trait",
             "reference/codegen/field",
             "reference/codegen/type-ref",
             "reference/codegen/scala-emitter",
             "reference/codegen/emitter-config",
             "reference/codegen/examples",
           ]
         },
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
          "reference/mux",
          {
            type: "category",
            label: "RingBuffer",
            link: { type: "doc", id: "reference/ringbuffer/index" },
            items: [
              "reference/ringbuffer/spsc",
              "reference/ringbuffer/spmc",
              "reference/ringbuffer/mpsc",
              "reference/ringbuffer/mpmc",
              "reference/ringbuffer/advanced",
            ]
          },
          "reference/html",
          "reference/smithy",
          "reference/datastar",
          {
            type: "category",
            label: "HTMX",
            link: { type: "doc", id: "reference/htmx/index" },
            items: [
              "reference/htmx/hx-swap",
              "reference/htmx/hx-trigger",
              "reference/htmx/hx-target",
              "reference/htmx/hx-params",
              "reference/htmx/hx-url-update",
              "reference/htmx/hx-encoding",
              "reference/htmx/hx-sync",
              "reference/htmx/attribute-values",
            ]
          },
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
             "reference/streams/concurrent-operators",
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
        "guides/getting-started-with-mux",
        "guides/query-dsl-extending",
        "guides/query-dsl-fluent-builder",
        "guides/query-dsl-reified-optics",
        "guides/query-dsl-sql",
        "guides/zio-schema-migration",
      ]
    }
  ]
};

module.exports = sidebars;
