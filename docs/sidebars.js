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
                     "reference/schema/built-in-codecs/json/json-patch",
                     "reference/schema/built-in-codecs/json/json-differ",
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
           label: "HTTP Model",
           link: { type: "doc", id: "reference/http-model/index" },
           items: [
             "reference/http-model/model",
             "reference/http-model/schema",
           ]
         },
         "reference/endpoint",
         "reference/chunk",
         "reference/maybe",
         "reference/ringbuffer",
         "reference/html",
         "reference/smithy",
         "reference/datastar",
         "reference/htmx",
         {
           type: "category",
           label: "Streams",
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
