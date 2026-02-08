# OpenAPI Type Enrichment: Doc Integration & ADT Typing

**Status**: Planning  
**Date**: 2026-02-08  
**Branch**: `planning/openapi-3.1`

---

## Context

### User Request
1. Replace `Option[String]` description/summary fields with `Option[Doc]` from the `markdown` module
2. Replace raw `Json` types with proper Scala ADTs wherever zio-http uses typed representations
3. `Schema[Doc]` defined in the `openapi` module (for now), can move later
4. `Doc` serializes as a markdown string in JSON (render on encode, parse on decode)

### Current State
- OpenAPI module: 30 types, 344 tests, Scala 2.13 + 3.x, JVM + JS
- All types in single file: `openapi/shared/src/main/scala/zio/blocks/openapi/OpenAPI.scala`
- 12 test files in `openapi/shared/src/test/scala/zio/blocks/openapi/`
- `openapi` depends on `schema`; `markdown` depends on `chunk` only

### Key Design Decision
`Schema[Doc]` is implemented as a `Schema[String]` transform (NOT a full ADT derivation):
```scala
implicit val docSchema: Schema[Doc] = Schema[String].transformOrFail(
  string => Parser.parse(string).left.map(_.toString),
  doc => Right(Renderer.render(doc))
)
```
This avoids Schema derivation for the entire Block/Inline hierarchy.

---

## Definition of Done
- [ ] `openapi` depends on `markdown` in build.sbt
- [ ] `Schema[Doc]` defined in openapi module, serializes as markdown string
- [ ] All description/summary fields use `Option[Doc]` (or `Doc` for Response.description)
- [ ] PathItem has full HTTP method operation fields (get/put/post/delete/options/head/patch/trace)
- [ ] Components uses typed maps with ReferenceOr[T] instead of Map[String, Json]
- [ ] Operation uses typed parameters, requestBody, responses, callbacks
- [ ] Parameter and Header use typed schema, examples, content fields
- [ ] MediaType uses typed schema field
- [ ] All 12 test files updated to match new signatures
- [ ] `sbt "++3.7.4; openapiJVM/test"` passes
- [ ] `sbt "++2.13.18; openapiJVM/test"` passes
- [ ] Code formatted

---

## TODOs

### Phase 1: Foundation — Add Doc dependency and Schema[Doc]

- [ ] 1. Add markdown dependency and Schema[Doc]

  **What to do**:
  - In `build.sbt`, add `.dependsOn(markdown)` to the `openapi` project (in addition to existing `schema`)
  - Create file: `openapi/shared/src/main/scala/zio/blocks/openapi/DocSchema.scala`
  - Define `Schema[Doc]` using transform: parse markdown string on decode, render on encode
  - Import `zio.blocks.docs.{Doc, Parser, Renderer}`

  **Must NOT do**:
  - Do NOT modify the `markdown` module
  - Do NOT derive Schema for Block/Inline/HeadingLevel etc.

  **Parallelizable**: NO (foundation for everything)

  **Acceptance Criteria**:
  - [ ] `sbt "++3.7.4; openapiJVM/compile"` succeeds
  - [ ] `sbt "++2.13.18; openapiJVM/compile"` succeeds

---

### Phase 2: Replace description/summary fields with Doc

- [ ] 2. Change all description/summary fields from String to Doc

  **What to do**:
  - In `OpenAPI.scala`, change ALL `description: Option[String]` → `description: Option[Doc]` and `summary: Option[String]` → `summary: Option[Doc]`
  - For `Response.description: String` → `Response.description: Doc`
  - Import `zio.blocks.docs.Doc` in OpenAPI.scala
  - Ensure the implicit `Schema[Doc]` from DocSchema.scala is in scope (via package import or explicit import)

  **Fields to change** (19 total):
  - `Info.summary`, `Info.description`
  - `Server.description`
  - `ServerVariable.description`
  - `PathItem.summary`, `PathItem.description`
  - `Operation.summary`, `Operation.description`
  - `Parameter.description`
  - `Header.description`
  - `RequestBody.description`
  - `Response.description` (String → Doc, non-optional)
  - `Example.summary`, `Example.description`
  - `Link.description`
  - `ExternalDocumentation.description`
  - `Tag.description`
  - `SecurityScheme.APIKey.description`, `SecurityScheme.HTTP.description`, `SecurityScheme.OAuth2.description`, `SecurityScheme.OpenIdConnect.description`, `SecurityScheme.MutualTLS.description`
  - `Reference.summary`, `Reference.description`

  **Must NOT do**:
  - Do NOT change any typed fields yet (Json → ADT changes come in Phase 3)
  - Do NOT modify test files yet

  **Parallelizable**: NO (must come before test updates)

  **Acceptance Criteria**:
  - [ ] `sbt "++3.7.4; openapiJVM/compile"` succeeds (tests may fail, that's ok)

---

- [ ] 3. Update all test files for Doc changes

  **What to do**:
  - In ALL 12 test files, replace `Some("description text")` with `Some(Doc.parse("description text"))` or construct Doc appropriately
  - For `Response`, change `description = "text"` to `description = Doc.parse("text")`
  - Add `import zio.blocks.docs._` to all test files
  - Use `Parser.parse(text).getOrElse(Doc(Chunk.empty))` or a helper for constructing Doc values in tests

  **Test files to update**:
  - `OpenAPISpec.scala`
  - `InfoContactLicenseSpec.scala`
  - `ServerSpec.scala`
  - `TagExternalDocsSpec.scala`
  - `ReferenceSpec.scala`
  - `ParameterSpec.scala`
  - `OperationSpec.scala`
  - `RequestResponseSpec.scala`
  - `SecuritySpec.scala`
  - `SchemaObjectSpec.scala`
  - `SchemaToOpenAPISpec.scala`
  - `OpenAPIRoundTripSpec.scala`

  **Must NOT do**:
  - Do NOT change the typed field tests yet

  **Parallelizable**: NO (depends on task 2)

  **Acceptance Criteria**:
  - [ ] `sbt "++3.7.4; openapiJVM/test"` passes
  - [ ] `sbt "++2.13.18; openapiJVM/test"` passes

  **Commit**: YES — `feat(openapi): use Doc type for description and summary fields`

---

### Phase 3: Type enrichment — Replace Json with proper ADTs

- [ ] 4. Enrich PathItem with full HTTP method operations

  **What to do**:
  - Add to `PathItem`: `get`, `put`, `post`, `delete`, `options`, `head`, `patch`, `trace` as `Option[Operation]`
  - Add to `PathItem`: `servers: List[Server] = Nil`
  - Add to `PathItem`: `parameters: List[ReferenceOr[Parameter]] = Nil`
  - Keep existing `summary`, `description`, `extensions` fields

  **Must NOT do**:
  - Do NOT change Components or Operation yet

  **Parallelizable**: NO (Operation already exists, but tests need sequential update)

  **Acceptance Criteria**:
  - [ ] `sbt "++3.7.4; openapiJVM/compile"` succeeds

---

- [ ] 5. Enrich Operation with typed fields

  **What to do**:
  - Change `responses: Json` → `responses: Map[String, ReferenceOr[Response]]`
  - Change `parameters: List[Json]` → `parameters: List[ReferenceOr[Parameter]]`
  - Change `requestBody: Option[Json]` → `requestBody: Option[ReferenceOr[RequestBody]]`
  - Change `callbacks: Map[String, Json]` → `callbacks: Map[String, ReferenceOr[Callback]]`

  **Must NOT do**:
  - Do NOT change Parameter/Header/MediaType/Components yet

  **Parallelizable**: NO (depends on task 4)

  **Acceptance Criteria**:
  - [ ] `sbt "++3.7.4; openapiJVM/compile"` succeeds

---

- [ ] 6. Enrich Parameter, Header, and MediaType with typed fields

  **What to do**:
  - Parameter: `schema: Option[Json]` → `schema: Option[ReferenceOr[SchemaObject]]`
  - Parameter: `examples: Map[String, Json]` → `examples: Map[String, ReferenceOr[Example]]`
  - Parameter: `content: Map[String, Json]` → `content: Map[String, MediaType]`
  - Header: same changes as Parameter (schema, examples, content)
  - MediaType: `schema: Option[Json]` → `schema: Option[ReferenceOr[SchemaObject]]`

  **Parallelizable**: NO (depends on task 5)

  **Acceptance Criteria**:
  - [ ] `sbt "++3.7.4; openapiJVM/compile"` succeeds

---

- [ ] 7. Enrich Components with typed maps

  **What to do**:
  - `schemas: Map[String, Json]` → `schemas: Map[String, ReferenceOr[SchemaObject]]`
  - `responses: Map[String, Json]` → `responses: Map[String, ReferenceOr[Response]]`
  - `parameters: Map[String, Json]` → `parameters: Map[String, ReferenceOr[Parameter]]`
  - `examples: Map[String, Json]` → `examples: Map[String, ReferenceOr[Example]]`
  - `requestBodies: Map[String, Json]` → `requestBodies: Map[String, ReferenceOr[RequestBody]]`
  - `headers: Map[String, Json]` → `headers: Map[String, ReferenceOr[Header]]`
  - `securitySchemes: Map[String, Json]` → `securitySchemes: Map[String, ReferenceOr[SecurityScheme]]`
  - `links: Map[String, Json]` → `links: Map[String, ReferenceOr[Link]]`
  - `callbacks: Map[String, Json]` → `callbacks: Map[String, ReferenceOr[Callback]]`
  - `pathItems: Map[String, Json]` → `pathItems: Map[String, ReferenceOr[PathItem]]`

  **Parallelizable**: NO (depends on tasks 4-6, since it references all those types)

  **Acceptance Criteria**:
  - [ ] `sbt "++3.7.4; openapiJVM/compile"` succeeds

---

### Phase 4: Update Tests and Verify

- [ ] 8. Update all test files for ADT type changes

  **What to do**:
  - Update all 12 test files to use the new typed fields
  - Replace `Json.Object(...)` / `Json.Array(...)` / raw Json values with proper typed constructors
  - For Operation: use `Map[String, ReferenceOr[Response]]` instead of Json
  - For Components: use typed maps
  - For Parameter/Header: use typed schema, examples, content

  **Must NOT do**:
  - Do NOT delete tests — update them to use new types

  **Parallelizable**: NO (depends on tasks 4-7)

  **Acceptance Criteria**:
  - [ ] `sbt "++3.7.4; openapiJVM/test"` passes — ALL tests green
  - [ ] `sbt "++2.13.18; openapiJVM/test"` passes — ALL tests green

  **Commit**: YES — `feat(openapi): replace raw Json with typed ADTs matching zio-http`

---

### Phase 5: Format and Final Verification

- [ ] 9. Format code and cross-verify

  **What to do**:
  - Run `sbt "++3.7.4; openapiJVM/scalafmt; openapiJVM/Test/scalafmt"`
  - Run `sbt "++3.7.4; openapiJVM/test; ++2.13.18; openapiJVM/test"`
  - Verify no regressions

  **Acceptance Criteria**:
  - [ ] Code formatted
  - [ ] All tests pass on both Scala versions

  **Commit**: YES (if formatting changed files) — `chore(openapi): format code`

---

## Verification Commands

```bash
# Compile both Scala versions
sbt "++3.7.4; openapiJVM/compile; ++2.13.18; openapiJVM/compile"

# Run tests both Scala versions
sbt "++3.7.4; openapiJVM/test; ++2.13.18; openapiJVM/test"

# Format
sbt "++3.7.4; openapiJVM/scalafmt; openapiJVM/Test/scalafmt"
```

## Risk Mitigation

- **StackOverflow on Schema derivation**: If Schema.derived fails for types with deep ReferenceOr nesting on Scala 2.13, fall back to manual Schema definitions (same pattern as Discriminator)
- **Circular references**: PathItem → Operation → Callback → PathItem is circular. Use `lazy val` for Schema instances if needed
- **Test breakage**: Update tests after each phase compilation succeeds

---

**Author**: Atlas (Orchestrator)  
**Status**: Ready for Execution
