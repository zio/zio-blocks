const sidebars = {
  // Getting Started has its own sidebar, reached from the "Getting Started" navbar item: the overview page, then the short
  // tour for newcomers in reading order. website/test/unit/getting-started-sidebar.test.mjs fails if a page under
  // docs/getting-started/ is missing here.
  gettingStarted: [
    { type: "doc", id: "index", label: "Overview" },
    {
      type: "category",
      label: "Getting Started",
      link: { type: "doc", id: "getting-started/index" },
      // Always expanded, with no fold toggle: the tour is short and read in order.
      collapsible: false,
      items: [
        "getting-started/installation",
        "getting-started/first-five-minutes",
        "getting-started/how-zio-blocks-thinks",
        {
          type: "category",
          label: "Core Concepts",
          collapsible: false,
          items: [
            "getting-started/concepts/schema",
            "getting-started/concepts/scope",
            "getting-started/concepts/wire-and-context",
            "getting-started/concepts/streams",
          ]
        },
        "getting-started/use-with-effect-systems",
        "getting-started/where-next",
      ]
    },
  ],
  // The reference sidebar, reached from the "Reference" navbar item: the block groups sit at its top level, with no
  // "ZIO Blocks" wrapper category.
  sidebar: [
         {
           type: "category",
           label: "Meta Programming",
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
                     "reference/schema/reflect-transformer",
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
                     "reference/schema/schema-search",
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
           ]
         },
         {
           type: "category",
           label: "Resource Management",
           link: { type: "doc", id: "reference/resource-management/index" },
           items: [
             "reference/resource-management/scope",
             "reference/resource-management/resource",
             "reference/resource-management/unscoped",
             "reference/resource-management/defer-handle",
             "reference/resource-management/finalizer",
             "reference/resource-management/finalization",
           ]
         },
         {
           type: "category",
           label: "Dependency Injection",
           items: [
             "reference/resource-management/wire",
             "reference/context",
           ]
         },
         {
           type: "category",
           label: "Configuration & Feature Flags",
           link: { type: "doc", id: "reference/config/index" },
           items: [
             "reference/config/config-source",
             "reference/config/config-decoder",
             "reference/config/errors",
             "reference/config/flags",
             "reference/config/rollout",
             "reference/config/formats",
           ]
         },
          {
            type: "category",
            label: "Web & HTTP",
            items: [
              "reference/media-type",
              {
                type: "category",
                label: "HTTP Model",
                link: { type: "doc", id: "reference/http-model/index" },
                items: [
                  "reference/http-model/model",
                  "reference/http-model/headers",
                  "reference/http-model/server-sent-event",
                  "reference/http-model/schema",
                  "reference/http-model/schema-codecs",
                ]
              },
              {
                type: "category",
                label: "Endpoint",
                link: { type: "doc", id: "reference/endpoint/index" },
                items: [
                  "reference/endpoint/endpoint",
                  "reference/endpoint/bulk-creation",
                  "reference/endpoint/http-codec",
                  "reference/endpoint/route-pattern",
                  "reference/endpoint/path-codec",
                  "reference/endpoint/segment-codec",
                  "reference/endpoint/auth-type",
                  "reference/endpoint/route-tree",
                ]
              },
              "reference/openapi",
              "reference/jwt",
              "reference/html",
             {
                type: "category",
                label: "Datastar",
               link: { type: "doc", id: "reference/datastar/index" },
               items: [
                 "reference/datastar/signals",
                 "reference/datastar/attributes",
                 "reference/datastar/events",
                 "reference/datastar/sse",
               ]
             },
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
                  "reference/htmx/response-headers",
                ]
              },
            ],
          },
          {
            type: "category",
            label: "Data Types",
            items: [
              "reference/chunk",
              "reference/maybe",
              "reference/combinators",
            ]
          },
          {
            type: "category",
            label: "Concurrency",
            items: [
              "reference/async",
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
            ],
          },
          {
            type: "category",
            label: "Streams",
            link: { type: "doc", id: "reference/streams/index" },
            items: [
              {
                type: "category",
                label: "Core Types",
                collapsed: false,
                link: { type: "doc", id: "reference/streams/core/index" },
                items: [
                  "reference/streams/core/stream",
                  "reference/streams/core/pipeline",
                  "reference/streams/core/sink",
                ]
              },
              {
                type: "category",
                label: "Low-Level Primitives",
                collapsed: false,
                link: { type: "doc", id: "reference/streams/primitives/index" },
                items: [
                  "reference/streams/primitives/reader",
                  "reference/streams/primitives/writer",
                ]
              },
              {
                type: "category",
                label: "Execution and Compatibility",
                collapsed: false,
                link: { type: "doc", id: "reference/streams/execution-and-compatibility/index" },
                items: [
                  "reference/streams/execution-and-compatibility/async-execution",
                  "reference/streams/execution-and-compatibility/zero-boxing",
                  "reference/streams/execution-and-compatibility/platform-differences",
                  "reference/streams/execution-and-compatibility/scala-2-compatibility",
                ]
              },
            ]
          },
          {
            type: "category",
            label: "Telemetry",
            link: { type: "doc", id: "reference/telemetry/index" },
            items: [
              {
                type: "category",
                label: "Tracing",
                link: { type: "doc", id: "reference/telemetry/tracing/index" },
                collapsed: false,
                items: [
                  "reference/telemetry/tracing/tracer-provider",
                  "reference/telemetry/tracing/tracer",
                  "reference/telemetry/tracing/span",
                  "reference/telemetry/tracing/span-context",
                  "reference/telemetry/tracing/span-data",
                  "reference/telemetry/tracing/span-builder",
                  "reference/telemetry/tracing/span-processor",
                  "reference/telemetry/tracing/sampler",
                  "reference/telemetry/tracing/span-kind",
                  "reference/telemetry/tracing/span-status",
                ]
              },
              {
                type: "category",
                label: "Logging",
                link: { type: "doc", id: "reference/telemetry/logging/index" },
                collapsed: false,
                items: [
                  "reference/telemetry/logging/logger-provider",
                  "reference/telemetry/logging/logger",
                  "reference/telemetry/logging/log-record",
                  "reference/telemetry/logging/log-record-processor",
                  "reference/telemetry/logging/log-formatter",
                  "reference/telemetry/logging/log-writer",
                  "reference/telemetry/logging/severity",
                  "reference/telemetry/logging/log-enrichment",
                ]
              },
              {
                type: "category",
                label: "Metrics",
                link: { type: "doc", id: "reference/telemetry/metrics/index" },
                collapsed: false,
                items: [
                  "reference/telemetry/metrics/meter-provider",
                  "reference/telemetry/metrics/meter",
                  "reference/telemetry/metrics/instruments",
                  "reference/telemetry/metrics/labeled-instruments",
                  "reference/telemetry/metrics/metric-data",
                ]
              },
              {
                type: "category",
                label: "Common Types",
                link: { type: "doc", id: "reference/telemetry/common/index" },
                collapsed: false,
                items: [
                  "reference/telemetry/common/attributes",
                  "reference/telemetry/common/attribute-key",
                  "reference/telemetry/common/any-value",
                  "reference/telemetry/common/resource",
                  "reference/telemetry/common/instrumentation-scope",
                ]
              },
              {
                type: "category",
                label: "OTLP Export",
                link: { type: "doc", id: "reference/telemetry/otel/index" },
                collapsed: false,
                items: [
                  "reference/telemetry/otel/custom-exporter",
                ]
              },
            ]
          },
          {
            type: "category",
            label: "Persistence",
            items: [
            {
              type: "category",
              label: "SQL Module",
              link: { type: "doc", id: "reference/sql/index" },
              items: [
                {
                  type: "category",
                  label: "Core Types",
                  collapsed: false,
                  items: [
                    "reference/sql/db-codec",
                    "reference/sql/frag",
                    "reference/sql/table",
                    "reference/sql/repo",
                    "reference/sql/transactor",
                    "reference/sql/db-con",
                    "reference/sql/db-tx",
                  ],
                },
                {
                  type: "category",
                  label: "Query Builder",
                  collapsed: false,
                  items: [
                    "reference/sql/sql-query",
                    "reference/sql/rel",
                    "reference/sql/dump",
                  ],
                },
                {
                  type: "category",
                  label: "Supporting Types",
                  collapsed: false,
                  items: [
                    "reference/sql/db-value",
                    "reference/sql/db-param",
                    "reference/sql/sql-dialect",
                    "reference/sql/sql-logger",
                    "reference/sql/sql-name-mapper",
                    "reference/sql/table-metadata",
                    "reference/sql/ddl",
                    "reference/sql/db-connection",
                    "reference/sql/db-result-reader",
                    "reference/sql/db-param-writer",
                    "reference/sql/db-codec-deriver",
                    "reference/sql/transactor-zio",
                  ],
                },
              ],
            },
                "reference/sql-zio",
                "reference/data-migration",
                {
                  type: "category",
                  label: "Projection",
                  link: { type: "doc", id: "reference/projection/index" },
                  items: [
                    "reference/projection/projection",
                    "reference/projection/projection-engine",
                    {
                      type: "category",
                      label: "Stores",
                      collapsed: false,
                      items: [
                        "reference/projection/event-store",
                        "reference/projection/projection-store",
                      ],
                    },
                    "reference/projection/migration",
                    "reference/projection/testing",
                  ],
                },
            ],
          },
          {
            type: "category",
            label: "Tooling & Codegen",
            items: [
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
              "reference/docs",
              "reference/smithy",
            ]
          },
  ],
  // Guides get their own sidebar, reached from the "Guides" navbar item, so the reference sidebar stays reference-only.
  // Its four sections sit at the top level; the guides index (/guides/) is not an entry here, but shows this sidebar
  // beside it (its displayed_sidebar) and draws the sections as cards (SidebarCards). Each section and how-to topic
  // links a generated index page that lists its items as cards, and gives the guides under it their breadcrumb trail
  // (Home > section > topic > guide). A new guide only needs an entry here, under its section and topic
  // (website/test/unit/guides-sidebar.test.mjs fails if a guide page is missing).
  guides: [
    {
      type: "category",
      label: "Tutorials",
      link: {
        type: "generated-index",
        slug: "/guides/tutorials",
        description: "Step-by-step lessons for newcomers, in the order to read them. Start with the first one.",
      },
      collapsed: false,
      // The learning path of docs/dev/backlog.md §3 (foundations, runtime blocks, building applications, capstone),
      // then the electives.
      items: [
        "guides/your-first-schema",
        "guides/evolve-your-data",
        "guides/compile-time-resource-safety-with-scope",
        "guides/typed-config-loader",
        "guides/async-getting-started",
        "guides/your-first-streaming-pipeline",
        "guides/your-first-sqlite-repository",
        "guides/your-first-html-page",
        "guides/instrument-a-program-with-logs-traces-and-metrics",
        "guides/small-json-backend-from-blocks",
        "guides/nested-data-with-optics",
        "guides/one-type-many-formats",
        "guides/add-interactivity-with-htmx",
        "guides/build-a-live-dashboard-with-datastar",
        "guides/getting-started-with-mux",
        "guides/generate-scala-code-from-smithy",
      ]
    },
    {
      type: "category",
      label: "How-to guides",
      link: {
        type: "generated-index",
        slug: "/guides/how-to",
        description: "Recipes for one task each, grouped by topic.",
      },
      collapsed: false,
      items: [
        {
          type: "category",
          label: "Schema",
          link: {
            type: "generated-index",
            slug: "/guides/how-to/schema",
            description: "Validate, compare, register and derive with Schema.",
          },
          collapsed: true,
          items: [
            "guides/validated-newtypes",
            "guides/use-iron-refined-types-and-typed-ids",
            "guides/derive-your-own-type-class",
            "guides/diff-and-patch",
            "guides/build-a-schema-registry",
            "guides/use-named-tuples-with-schema",
            "guides/generate-json-schema-and-validate-payloads",
          ]
        },
        {
          type: "category",
          label: "Formats & codecs",
          link: {
            type: "generated-index",
            slug: "/guides/how-to/formats",
            description: "Read and write JSON, YAML, CSV, Avro, BSON and TOON, and customize codecs.",
          },
          collapsed: true,
          items: [
            "guides/customize-json-encoding",
            "guides/override-codec-for-one-type",
            "guides/json-without-case-classes",
            "guides/read-and-write-yaml",
            "guides/read-and-write-csv-and-tsv-files",
            "guides/encode-and-decode-records-with-avro",
            "guides/store-case-classes-in-mongodb",
            "guides/llm-data-with-toon",
          ]
        },
        {
          type: "category",
          label: "SQL & persistence",
          link: {
            type: "generated-index",
            slug: "/guides/how-to/sql",
            description: "Connect, query, page, stream, migrate and test database code with the sql module.",
          },
          collapsed: true,
          items: [
            "guides/connect-to-postgresql-with-a-connection-pool",
            "guides/test-database-code-against-sqlite",
            "guides/sql-transactions",
            "guides/sql-checked-interpolation",
            "guides/write-joins-and-inspect-generated-sql",
            "guides/upsert-and-paginate-with-keyset-cursors",
            "guides/map-custom-column-types",
            "guides/stream-large-query-results",
            "guides/migrate-table-rows-online",
            "guides/store-and-search-vector-embeddings",
            "guides/sql-from-zio",
          ]
        },
        {
          type: "category",
          label: "Streams & async",
          link: {
            type: "generated-index",
            slug: "/guides/how-to/streams",
            description: "Run streams safely and without blocking, and bridge callback-based APIs.",
          },
          collapsed: true,
          items: [
            "guides/stream-files-and-sockets",
            "guides/run-streams-without-blocking",
            "guides/wrap-callback-and-future-apis",
            "guides/run-a-mux-over-a-socket",
          ]
        },
        {
          type: "category",
          label: "Config, resources & DI",
          link: {
            type: "generated-index",
            slug: "/guides/how-to/config",
            description: "Load configuration, wire dependencies, fix Scope errors and roll out feature flags.",
          },
          collapsed: true,
          items: [
            "guides/load-configuration-files",
            "guides/feed-config-into-a-wire-graph",
            "guides/fix-scope-and-wire-compile-errors",
            "guides/roll-out-feature-gradually",
          ]
        },
        {
          type: "category",
          label: "Web & HTTP",
          link: {
            type: "generated-index",
            slug: "/guides/how-to/web",
            description: "Serve requests, validate input, push events, render HTMX, secure endpoints with JWT and work with OpenAPI.",
          },
          collapsed: true,
          items: [
            "guides/serve-http-model-requests",
            "guides/validate-query-parameters-and-headers",
            "guides/send-server-sent-events",
            "guides/return-htmx-fragments-and-response-headers",
            "guides/issue-and-verify-jwts",
            "guides/protect-endpoints-with-jwt",
            "guides/write-and-export-openapi",
            "guides/generate-scala-types-from-openapi",
          ]
        },
        {
          type: "category",
          label: "Observability",
          link: {
            type: "generated-index",
            slug: "/guides/how-to/observability",
            description: "Test and correlate logs, traces and metrics.",
          },
          collapsed: true,
          items: [
            "guides/test-telemetry",
            "guides/correlate-logs-and-traces",
          ]
        },
        {
          type: "category",
          label: "Performance",
          link: {
            type: "generated-index",
            slug: "/guides/how-to/performance",
            description: "Cut allocation and boxing, and tune codec throughput.",
          },
          collapsed: true,
          items: [
            "guides/tune-json-throughput",
            "guides/avoid-allocation-on-hot-paths",
            "guides/process-binary-data-without-boxing",
          ]
        },
        {
          type: "category",
          label: "Interop",
          link: {
            type: "generated-index",
            slug: "/guides/how-to/interop",
            description: "Use ZIO Blocks from ZIO and cats-effect applications.",
          },
          collapsed: true,
          items: [
            "guides/use-zio-blocks-in-zio-or-cats-effect",
          ]
        },
      ]
    },
    {
      type: "category",
      label: "Migration guides",
      link: {
        type: "generated-index",
        slug: "/guides/migration",
        description: "Move code from another Scala library to ZIO Blocks.",
      },
      collapsed: false,
      items: [
        "guides/zio-schema-migration",
        "guides/json-library-migration",
        "guides/resource-and-di-migration",
        "guides/config-library-migration",
        "guides/streaming-library-migration",
        "guides/migrate-from-magnum-or-doobie",
      ]
    },
    {
      type: "category",
      label: "Deep dives",
      link: {
        type: "generated-index",
        slug: "/guides/deep-dives",
        description: "Long-form explanations of how a part of ZIO Blocks works and why.",
      },
      collapsed: false,
      items: [
        "guides/telemetry-guide",
        {
          type: "category",
          label: "Query DSL",
          link: { type: "doc", id: "guides/query-dsl/index" },
          collapsed: false,
          items: [
            "guides/query-dsl-reified-optics",
            "guides/query-dsl-sql",
            "guides/query-dsl-extending",
            "guides/query-dsl-fluent-builder",
          ]
        },
      ]
    },
  ]
};

module.exports = sidebars;
