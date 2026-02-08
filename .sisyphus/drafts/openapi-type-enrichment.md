# Draft: OpenAPI Type Enrichment & Doc Integration

## Requirements (confirmed)
- Replace `Option[String]` description/summary fields with `Option[Doc]` from the docs/markdown module
- Replace raw `Json` types with proper Scala ADTs wherever zio-http uses typed representations
- Model parity with zio-http OpenAPI types

## Research Findings

### zio-http OpenAPI Type Comparison

#### 1. Doc for descriptions/summaries
zio-http uses `Doc` for all `description` fields across:
- Info.description: `Option[Doc]`
- Server.description: `Option[Doc]`
- ServerVariable.description: `Doc` (not Option!)
- PathItem.description: `Option[Doc]`
- Operation.description: `Option[Doc]`
- Parameter.description: `Option[Doc]`
- Header.description: `Option[Doc]`
- RequestBody.description: `Option[Doc]`
- Response.description: `Option[Doc]`
- ExternalDoc.description: `Option[Doc]`
- SecurityScheme.description: `Option[Doc]`
- Tag (implicit via Doc)

#### 2. PathItem - MASSIVE gap
Our current PathItem is a stub with just summary/description/extensions.
zio-http has full operations:
```scala
case class PathItem(
  ref: Option[String],
  summary: Option[String],
  description: Option[Doc],
  get: Option[Operation],
  put: Option[Operation],
  post: Option[Operation],
  delete: Option[Operation],
  options: Option[Operation],
  head: Option[Operation],
  patch: Option[Operation],
  trace: Option[Operation],
  servers: List[Server],
  parameters: Set[ReferenceOr[Parameter]],
)
```

#### 3. Components - ALL raw Json, should be typed
Our Components uses `Map[String, Json]` for everything.
zio-http uses typed maps:
```scala
case class Components(
  schemas: ListMap[Key, ReferenceOr[JsonSchema]],
  responses: ListMap[Key, ReferenceOr[Response]],
  parameters: ListMap[Key, ReferenceOr[Parameter]],
  examples: ListMap[Key, ReferenceOr[Example]],
  requestBodies: ListMap[Key, ReferenceOr[RequestBody]],
  headers: ListMap[Key, ReferenceOr[Header]],
  securitySchemes: ListMap[Key, ReferenceOr[SecurityScheme]],
  links: ListMap[Key, ReferenceOr[Link]],
  callbacks: ListMap[Key, ReferenceOr[Callback]],
)
```

#### 4. Operation - several Json fields should be typed
Our Operation uses:
- `responses: Json` → should be `Map[String, ReferenceOr[Response]]`
- `parameters: List[Json]` → should be `List[ReferenceOr[Parameter]]`
- `requestBody: Option[Json]` → should be `Option[ReferenceOr[RequestBody]]`
- `callbacks: Map[String, Json]` → should be `Map[String, ReferenceOr[Callback]]`
- `description: Option[String]` → should be `Option[Doc]`

#### 5. MediaType - schema should be typed
Our MediaType uses:
- `schema: Option[Json]` → should be `Option[ReferenceOr[JsonSchema]]` (or keep as Json since JsonSchema IS a Json wrapper)

Actually, zio-http uses `ReferenceOr[JsonSchema]` but our `JsonSchema` is already in zio-blocks-schema. We already have `SchemaObject` which wraps JsonSchema. 

#### 6. Parameter - schema should be typed
Our Parameter uses:
- `schema: Option[Json]` → should be `Option[ReferenceOr[JsonSchema]]`
- `description: Option[String]` → should be `Option[Doc]`

#### 7. Header - schema should be typed
Our Header uses:
- `schema: Option[Json]` → should be `Option[ReferenceOr[JsonSchema]]`
- `description: Option[String]` → should be `Option[Doc]`

### Our Docs Module
- Located at: `markdown/shared/src/main/scala/zio/blocks/docs/`
- Package: `zio.blocks.docs`
- Type: `final case class Doc(blocks: Chunk[Block], metadata: Map[String, String])`
- Depends on: `chunk` module only
- Does NOT have `Schema[Doc]` derived yet - would need to add
- The `markdown` module does NOT depend on `schema` module

### Dependency Implications
- Current: `openapi` depends on `schema`
- `markdown` depends on `chunk`
- `schema` depends on `chunk` and `typeid`
- To use Doc in OpenAPI: `openapi` must also depend on `markdown`
- Need: `Schema[Doc]` must exist, either in `markdown` (if it depends on schema) or via a bridge

## Technical Decisions
- Need to add `markdown` as dependency of `openapi`
- Need to derive/define `Schema[Doc]` (and Schema[Block], Schema[Inline], etc.)

## Open Questions
- Should openapi depend on markdown directly, or should we add Schema[Doc] in the schema module?
- How deep to type the Components maps? (ReferenceOr pattern is already in our codebase)
- JsonSchema references: keep as Json or use our JsonSchema type?

## Scope Boundaries
- INCLUDE: All description/summary → Doc changes, PathItem full operations, Components typing, Operation typing
- EXCLUDE: External $ref resolution, webhooks
