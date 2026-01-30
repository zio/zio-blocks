# OpenAPI 3.1 Support for ZIO Blocks

**Status**: Planning  
**Date**: 2026-01-30  
**Branch**: `planning/openapi-3.1`

---

## Context

### Original Request
Create a standalone OpenAPI 3.1.0 library for ZIO Blocks that can be used independently or integrated with zio-http later.

### Interview Summary
**Key Discussions**:
- **Primary use case**: Standalone OpenAPI library (independent, zio-http integration optional/later)
- **Dependency strategy**: Depend on zio-blocks-schema (reuse Json, JsonSchema)
- **Pain points to address**: OpenAPI extensions (x-*), separate summary/description, proper examples
- **Schema approach**: Wrap JsonSchema with OpenAPI vocabulary
- **JSON encoding**: Derive Schema[OpenAPI] for ADTs, use zio-blocks JSON codec
- **Validation**: Smart constructors for critical invariants
- **Testing**: TDD with zio-test, full OpenAPI 3.1 spec coverage

**Research Findings**:
- JsonSchema already implemented with 817/844 official tests passing
- OpenAPI 3.1 has 30 core objects
- zio-http pain points: no extensions, no summary, example issues
- OpenAPI 3.1 Schema Object = JSON Schema 2020-12 + discriminator/xml/externalDocs/example

### Metis Review
**Identified Gaps** (addressed in this plan):
- All 30 OpenAPI objects must be enumerated explicitly
- Extension (x-*) support must be specified per object
- Smart constructor invariants must be defined
- "Must NOT Have" list must be explicit
- Integration hooks for future zio-http must be considered

---

## Work Objectives

### Core Objective
Build a complete, type-safe OpenAPI 3.1.0 ADT in a single `zio-blocks-openapi` module that integrates with existing JsonSchema and provides Schema derivation for JSON encoding/decoding.

### Concrete Deliverables
- `openapi/shared/src/main/scala/zio/blocks/openapi/` - All OpenAPI 3.1 ADT types
- `openapi/shared/src/test/scala/zio/blocks/openapi/` - Comprehensive TDD tests
- `Schema[T]` instances for all OpenAPI types (derived or handrolled)
- JSON round-trip support via existing zio-blocks codec
- Scaladoc for all public APIs

### Definition of Done
- [ ] All 30 OpenAPI 3.1 objects implemented as case classes/sealed traits
- [ ] All objects with extensions field support `Map[String, Json]` for x-* fields
- [ ] Schema instances exist for all types: `sbt openapiJVM/compile` succeeds
- [ ] JSON round-trip works: encode -> decode -> encode produces identical output
- [ ] TDD tests pass: `sbt openapiJVM/test` with >90% coverage
- [ ] Code formatted: `sbt openapiJVM/scalafmt openapiJVM/Test/scalafmt`
- [ ] Scaladoc on all public types and methods

### Must Have
- Complete OpenAPI 3.1.0 ADT (all 30 objects listed below)
- Extensions (x-*) support on all extensible objects
- Separate `summary` and `description` fields where spec requires
- `SchemaObject` wrapping `JsonSchema` with OpenAPI vocabulary
- Smart constructors for version format, required fields
- Internal $ref support (#/components/...)
- Schema derivation for JSON encoding/decoding

### Must NOT Have (Guardrails)
- External $ref support (file://, https://) - defer to future
- Webhooks implementation - defer to future
- SwaggerUI helpers - leave to zio-http
- zio-http integration code - will live in zio-http repo
- Dependencies outside zio-blocks-schema
- Runtime validation beyond smart constructors
- AsyncAPI consideration - out of scope
- OpenAPI 3.0 compatibility - 3.1 only

---

## OpenAPI 3.1 Objects (All 30)

### Objects Requiring Implementation

| # | Object | Extensions | Required Fields | Notes |
|---|--------|------------|-----------------|-------|
| 1 | OpenAPI | Yes | openapi, info | Root object |
| 2 | Info | Yes | title, version | API metadata |
| 3 | Contact | Yes | - | Contact info |
| 4 | License | Yes | name | License info |
| 5 | Server | Yes | url | Server config |
| 6 | ServerVariable | Yes | default | Variable substitution |
| 7 | Components | Yes | - | Reusable components |
| 8 | PathItem | Yes | - | Path operations |
| 9 | Operation | Yes | responses | HTTP operation |
| 10 | ExternalDocumentation | Yes | url | External docs link |
| 11 | Parameter | Yes | name, in | Request parameter |
| 12 | RequestBody | Yes | content | Request body |
| 13 | MediaType | Yes | - | Content type |
| 14 | Encoding | Yes | - | Encoding options |
| 15 | Responses | Yes | - | Response map |
| 16 | Response | Yes | description | Single response |
| 17 | Callback | Yes | - | Callback definition |
| 18 | Example | Yes | - | Example value |
| 19 | Link | Yes | - | Link definition |
| 20 | Header | Yes | - | Header parameter |
| 21 | Tag | Yes | name | Grouping tag |
| 22 | Reference | No | $ref | Reference object |
| 23 | SchemaObject | Yes | - | Wraps JsonSchema |
| 24 | Discriminator | No | propertyName | Polymorphism |
| 25 | XML | No | - | XML hints |
| 26 | SecurityScheme | Yes | type | Auth scheme |
| 27 | OAuthFlows | Yes | - | OAuth2 flows |
| 28 | OAuthFlow | Yes | scopes | Single flow |
| 29 | SecurityRequirement | No | - | Security ref |
| 30 | Paths | No | - | Type alias |

**Extension Support Rule**: All objects marked "Yes" get `extensions: Map[String, Json] = Map.empty` field.

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES (zio-test in schema module)
- **User wants tests**: TDD
- **Framework**: zio-test (ZIO Blocks standard)

### TDD Workflow

Each TODO follows RED-GREEN-REFACTOR:

1. **RED**: Write failing test first
   - Test file: `openapi/shared/src/test/scala/zio/blocks/openapi/[Type]Spec.scala`
   - Test command: `sbt openapiJVM/testOnly *[Type]Spec`
   - Expected: FAIL (test exists, implementation doesn't)

2. **GREEN**: Implement minimum code to pass
   - Command: `sbt openapiJVM/testOnly *[Type]Spec`
   - Expected: PASS

3. **REFACTOR**: Clean up while keeping green
   - Command: `sbt openapiJVM/test`
   - Expected: PASS (all tests)

### Test Categories Required
- Unit tests for each ADT type
- Smart constructor validation tests
- JSON round-trip tests (encode -> decode -> encode)
- Extension preservation tests
- Schema derivation tests

---

## Task Flow

```
Phase 1: Project Setup
    |
    v
Phase 2: Core Types (parallel group)
    |
    v
Phase 3: Schema Object + Integration
    |
    v
Phase 4: Path/Operation Objects (parallel group)
    |
    v
Phase 5: Component Objects (parallel group)
    |
    v
Phase 6: Security Objects
    |
    v
Phase 7: Integration & Polish
```

## Parallelization

| Group | Tasks | Reason |
|-------|-------|--------|
| A | 2, 3, 4, 5, 6 | Core metadata types independent |
| B | 8, 9, 10, 11 | Path-related types independent |
| C | 12, 13, 14, 15 | Response-related types independent |
| D | 16, 17, 18, 19 | Component types independent |
| E | 20, 21, 22, 23 | Security types independent |

---

## TODOs

### Phase 1: Project Setup

- [ ] 1. Create openapi module structure

  **What to do**:
  - Add `openapi` module to `build.sbt` with cross-platform support (JVM, JS, Native)
  - Create directory structure: `openapi/shared/src/main/scala/zio/blocks/openapi/`
  - Create test directory: `openapi/shared/src/test/scala/zio/blocks/openapi/`
  - Add dependency on `schemaJVM`, `schemaJS`, `schemaNative`
  - Create package object with common type aliases

  **Must NOT do**:
  - Add any external dependencies
  - Add zio-http dependencies

  **Parallelizable**: NO (foundation for all other tasks)

  **References**:
  - `build.sbt` - Existing module definitions (schema, chunk, schema-avro patterns)
  - `schema/shared/src/main/scala/zio/blocks/schema/package.scala` - Package object pattern

  **Acceptance Criteria**:
  - [ ] `sbt openapiJVM/compile` succeeds with empty module
  - [ ] `sbt openapiJS/compile` succeeds
  - [ ] `sbt openapiNative/compile` succeeds
  - [ ] Directory structure matches schema module pattern

  **Commit**: YES
  - Message: `feat(openapi): add openapi module structure`
  - Files: `build.sbt`, `openapi/**`

---

### Phase 2: Core Metadata Types

- [ ] 2. Implement OpenAPI root object

  **What to do**:
  - Create `OpenAPI.scala` with root case class
  - Fields: openapi (String), info (Info), jsonSchemaDialect (Option), servers (List), paths (Option), components (Option), security (List), tags (List), externalDocs (Option), extensions (Map)
  - Smart constructor validates `openapi` field is "3.1.0" or valid semver
  - Derive Schema[OpenAPI]
  - Write tests first (TDD)

  **Must NOT do**:
  - Add webhooks field (deferred)
  - Support OpenAPI 3.0 format

  **Parallelizable**: NO (depends on task 1, other tasks depend on this)

  **References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala:1-100` - ADT pattern with extensions
  - OpenAPI 3.1 spec: https://spec.openapis.org/oas/v3.1.0#openapi-object

  **Acceptance Criteria**:
  - [ ] Test file created: `OpenAPISpec.scala`
  - [ ] `sbt openapiJVM/testOnly *OpenAPISpec` passes
  - [ ] Smart constructor rejects invalid version strings
  - [ ] Extensions map preserves x-* fields on round-trip

  **Commit**: YES
  - Message: `feat(openapi): add OpenAPI root object with smart constructor`
  - Files: `OpenAPI.scala`, `OpenAPISpec.scala`

- [ ] 3. Implement Info, Contact, License objects

  **What to do**:
  - Create `Info.scala` with Info case class (title, summary, description, termsOfService, contact, license, version, extensions)
  - Create `Contact.scala` with Contact case class (name, url, email, extensions)
  - Create `License.scala` with License case class (name, identifier, url, extensions)
  - Note: identifier and url are mutually exclusive in License
  - Derive Schema instances for all
  - TDD approach

  **Must NOT do**:
  - Skip summary field (this is a pain point we're fixing)

  **Parallelizable**: YES (with 4, 5, 6)

  **References**:
  - OpenAPI 3.1 spec sections 4.8.2, 4.8.3, 4.8.4

  **Acceptance Criteria**:
  - [ ] Test files created for Info, Contact, License
  - [ ] All tests pass
  - [ ] Info has separate summary and description fields
  - [ ] License enforces identifier/url mutual exclusivity

  **Commit**: YES
  - Message: `feat(openapi): add Info, Contact, License objects`
  - Files: `Info.scala`, `Contact.scala`, `License.scala`, tests

- [ ] 4. Implement Server and ServerVariable objects

  **What to do**:
  - Create `Server.scala` with Server case class (url, description, variables, extensions)
  - Create `ServerVariable.scala` with ServerVariable case class (enum, default, description, extensions)
  - Smart constructor: default must be in enum if enum is provided
  - Derive Schema instances
  - TDD approach

  **Parallelizable**: YES (with 3, 5, 6)

  **References**:
  - OpenAPI 3.1 spec sections 4.8.5, 4.8.6

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] ServerVariable validates default is in enum

  **Commit**: YES
  - Message: `feat(openapi): add Server and ServerVariable objects`
  - Files: `Server.scala`, `ServerVariable.scala`, tests

- [ ] 5. Implement Tag and ExternalDocumentation objects

  **What to do**:
  - Create `Tag.scala` with Tag case class (name, description, externalDocs, extensions)
  - Create `ExternalDocumentation.scala` (url, description, extensions)
  - Derive Schema instances
  - TDD approach

  **Parallelizable**: YES (with 3, 4, 6)

  **References**:
  - OpenAPI 3.1 spec sections 4.8.22, 4.8.11

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] URL validation in ExternalDocumentation

  **Commit**: YES
  - Message: `feat(openapi): add Tag and ExternalDocumentation objects`
  - Files: `Tag.scala`, `ExternalDocumentation.scala`, tests

- [ ] 6. Implement Reference object and ReferenceOr[A] pattern

  **What to do**:
  - Create `Reference.scala` with Reference case class ($ref, summary, description)
  - Create `ReferenceOr.scala` with sealed trait and two cases: Ref(Reference), Value(A)
  - No extensions on Reference (per spec)
  - Schema derivation for polymorphic type
  - TDD approach

  **Must NOT do**:
  - Support external $ref (only #/components/...)

  **Parallelizable**: YES (with 3, 4, 5)

  **References**:
  - OpenAPI 3.1 spec section 4.8.23
  - `schema/shared/src/main/scala/zio/blocks/schema/Reflect.scala` - Sum type patterns

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] ReferenceOr[A] can serialize as either $ref object or inline value
  - [ ] JSON round-trip distinguishes reference from value

  **Commit**: YES
  - Message: `feat(openapi): add Reference and ReferenceOr pattern`
  - Files: `Reference.scala`, `ReferenceOr.scala`, tests

---

### Phase 3: Schema Integration

- [ ] 7. Implement SchemaObject wrapping JsonSchema

  **What to do**:
  - Create `SchemaObject.scala` wrapping existing JsonSchema
  - Add OpenAPI vocabulary: discriminator, xml, externalDocs, example (deprecated), extensions
  - Conversion methods: `fromJsonSchema`, `toJsonSchema`
  - Derive Schema[SchemaObject]
  - TDD approach

  **Must NOT do**:
  - Duplicate JsonSchema keywords
  - Support deprecated `nullable` keyword

  **Parallelizable**: NO (foundation for many objects)

  **References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala` - Existing implementation
  - OpenAPI 3.1 spec section 4.8.24

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] SchemaObject wraps JsonSchema without duplication
  - [ ] OpenAPI vocabulary (discriminator, xml) serializes correctly
  - [ ] Round-trip preserves both JsonSchema and OpenAPI keywords

  **Commit**: YES
  - Message: `feat(openapi): add SchemaObject wrapping JsonSchema`
  - Files: `SchemaObject.scala`, tests

- [ ] 8. Implement Discriminator and XML objects

  **What to do**:
  - Create `Discriminator.scala` (propertyName, mapping) - no extensions
  - Create `XML.scala` (name, namespace, prefix, attribute, wrapped) - no extensions
  - Derive Schema instances
  - TDD approach

  **Parallelizable**: YES (with 9, 10, 11)

  **References**:
  - OpenAPI 3.1 spec sections 4.8.25, 4.8.26

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] Discriminator maps type names correctly

  **Commit**: YES
  - Message: `feat(openapi): add Discriminator and XML objects`
  - Files: `Discriminator.scala`, `XML.scala`, tests

---

### Phase 4: Path and Operation Objects

- [ ] 9. Implement Paths and PathItem objects

  **What to do**:
  - Create `Paths.scala` as type alias or wrapper for Map[String, PathItem]
  - Create `PathItem.scala` (ref, summary, description, get/put/post/delete/options/head/patch/trace, servers, parameters, extensions)
  - Derive Schema instances
  - TDD approach

  **Parallelizable**: YES (with 8, 10, 11)

  **References**:
  - OpenAPI 3.1 spec sections 4.8.8, 4.8.9

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] PathItem has separate summary/description fields
  - [ ] All HTTP methods represented

  **Commit**: YES
  - Message: `feat(openapi): add Paths and PathItem objects`
  - Files: `Paths.scala`, `PathItem.scala`, tests

- [ ] 10. Implement Operation object

  **What to do**:
  - Create `Operation.scala` (tags, summary, description, externalDocs, operationId, parameters, requestBody, responses, callbacks, deprecated, security, servers, extensions)
  - Derive Schema[Operation]
  - TDD approach

  **Parallelizable**: YES (with 8, 9, 11)

  **References**:
  - OpenAPI 3.1 spec section 4.8.10

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] Operation has separate summary/description
  - [ ] responses field is required (non-optional)

  **Commit**: YES
  - Message: `feat(openapi): add Operation object`
  - Files: `Operation.scala`, tests

- [ ] 11. Implement Parameter and Header objects

  **What to do**:
  - Create `Parameter.scala` (name, in, description, required, deprecated, allowEmptyValue, style, explode, allowReserved, schema, example, examples, content, extensions)
  - Create `Header.scala` (same as Parameter without name/in)
  - `in` field as sealed trait: Query, Header, Path, Cookie
  - Smart constructor: required must be true when in=path
  - Derive Schema instances
  - TDD approach

  **Parallelizable**: YES (with 8, 9, 10)

  **References**:
  - OpenAPI 3.1 spec sections 4.8.12, 4.8.21

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] ParameterLocation sealed trait works
  - [ ] Path parameters enforce required=true

  **Commit**: YES
  - Message: `feat(openapi): add Parameter and Header objects`
  - Files: `Parameter.scala`, `Header.scala`, `ParameterLocation.scala`, tests

---

### Phase 5: Request/Response Objects

- [ ] 12. Implement RequestBody object

  **What to do**:
  - Create `RequestBody.scala` (description, content, required, extensions)
  - content is Map[String, MediaType] (required)
  - Derive Schema[RequestBody]
  - TDD approach

  **Parallelizable**: YES (with 13, 14, 15)

  **References**:
  - OpenAPI 3.1 spec section 4.8.13

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] content field is required

  **Commit**: YES
  - Message: `feat(openapi): add RequestBody object`
  - Files: `RequestBody.scala`, tests

- [ ] 13. Implement MediaType and Encoding objects

  **What to do**:
  - Create `MediaType.scala` (schema, example, examples, encoding, extensions)
  - Create `Encoding.scala` (contentType, headers, style, explode, allowReserved, extensions)
  - Derive Schema instances
  - TDD approach

  **Parallelizable**: YES (with 12, 14, 15)

  **References**:
  - OpenAPI 3.1 spec sections 4.8.14, 4.8.15

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] example and examples fields both supported

  **Commit**: YES
  - Message: `feat(openapi): add MediaType and Encoding objects`
  - Files: `MediaType.scala`, `Encoding.scala`, tests

- [ ] 14. Implement Responses and Response objects

  **What to do**:
  - Create `Responses.scala` as Map[String, ReferenceOr[Response]] with default key
  - Create `Response.scala` (description, headers, content, links, extensions)
  - description is required
  - Derive Schema instances
  - TDD approach

  **Parallelizable**: YES (with 12, 13, 15)

  **References**:
  - OpenAPI 3.1 spec sections 4.8.16, 4.8.17

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] Response.description is required
  - [ ] Responses supports "default" key and status codes

  **Commit**: YES
  - Message: `feat(openapi): add Responses and Response objects`
  - Files: `Responses.scala`, `Response.scala`, tests

- [ ] 15. Implement Example, Link, Callback objects

  **What to do**:
  - Create `Example.scala` (summary, description, value, externalValue, extensions)
  - value and externalValue are mutually exclusive
  - Create `Link.scala` (operationRef, operationId, parameters, requestBody, description, server, extensions)
  - operationRef and operationId are mutually exclusive
  - Create `Callback.scala` as Map[String, ReferenceOr[PathItem]]
  - Derive Schema instances
  - TDD approach

  **Parallelizable**: YES (with 12, 13, 14)

  **References**:
  - OpenAPI 3.1 spec sections 4.8.19, 4.8.20, 4.8.18

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] Example enforces value/externalValue mutual exclusivity
  - [ ] Link enforces operationRef/operationId mutual exclusivity

  **Commit**: YES
  - Message: `feat(openapi): add Example, Link, Callback objects`
  - Files: `Example.scala`, `Link.scala`, `Callback.scala`, tests

---

### Phase 6: Components and Security

- [ ] 16. Implement Components object

  **What to do**:
  - Create `Components.scala` with all component maps:
    - schemas, responses, parameters, examples, requestBodies, headers, securitySchemes, links, callbacks, pathItems
  - All values are ReferenceOr[T]
  - extensions field
  - Derive Schema[Components]
  - TDD approach

  **Parallelizable**: NO (integrates many types)

  **References**:
  - OpenAPI 3.1 spec section 4.8.7

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] All component types supported
  - [ ] pathItems field included (OpenAPI 3.1 addition)

  **Commit**: YES
  - Message: `feat(openapi): add Components object`
  - Files: `Components.scala`, tests

- [ ] 17. Implement SecurityScheme sealed trait

  **What to do**:
  - Create `SecurityScheme.scala` as sealed trait with cases:
    - APIKey (name, in, description, extensions)
    - HTTP (scheme, bearerFormat, description, extensions)
    - OAuth2 (flows, description, extensions)
    - OpenIdConnect (openIdConnectUrl, description, extensions)
    - MutualTLS (description, extensions)
  - `in` for APIKey as sealed trait: Query, Header, Cookie
  - Derive Schema[SecurityScheme]
  - TDD approach

  **Parallelizable**: YES (with 18, 19)

  **References**:
  - OpenAPI 3.1 spec section 4.8.27

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] All 5 security scheme types implemented
  - [ ] type field serializes correctly for each variant

  **Commit**: YES
  - Message: `feat(openapi): add SecurityScheme sealed trait`
  - Files: `SecurityScheme.scala`, tests

- [ ] 18. Implement OAuthFlows and OAuthFlow objects

  **What to do**:
  - Create `OAuthFlows.scala` (implicit, password, clientCredentials, authorizationCode, extensions)
  - Create `OAuthFlow.scala` (authorizationUrl, tokenUrl, refreshUrl, scopes, extensions)
  - Different flows have different required fields
  - Derive Schema instances
  - TDD approach

  **Parallelizable**: YES (with 17, 19)

  **References**:
  - OpenAPI 3.1 spec sections 4.8.28, 4.8.29

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] Flow-specific required fields validated

  **Commit**: YES
  - Message: `feat(openapi): add OAuthFlows and OAuthFlow objects`
  - Files: `OAuthFlows.scala`, `OAuthFlow.scala`, tests

- [ ] 19. Implement SecurityRequirement object

  **What to do**:
  - Create `SecurityRequirement.scala` as Map[String, List[String]]
  - No extensions (per spec)
  - Derive Schema[SecurityRequirement]
  - TDD approach

  **Parallelizable**: YES (with 17, 18)

  **References**:
  - OpenAPI 3.1 spec section 4.8.30

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] Empty list means no scopes required

  **Commit**: YES
  - Message: `feat(openapi): add SecurityRequirement object`
  - Files: `SecurityRequirement.scala`, tests

---

### Phase 7: Integration and Polish

- [ ] 20. Add Schema[A].toOpenAPISchema derivation

  **What to do**:
  - Add extension method or helper: `Schema[A].toOpenAPISchema: SchemaObject`
  - Converts Schema -> JsonSchema -> SchemaObject
  - Handle discriminator for sealed traits
  - TDD approach

  **Parallelizable**: NO (integrates with SchemaObject)

  **References**:
  - `schema/shared/src/main/scala/zio/blocks/schema/Schema.scala:68` - toJsonSchema pattern
  - `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala` - Derivation patterns

  **Acceptance Criteria**:
  - [ ] Tests pass
  - [ ] Schema[CaseClass].toOpenAPISchema produces correct object schema
  - [ ] Schema[SealedTrait].toOpenAPISchema produces oneOf with discriminator

  **Commit**: YES
  - Message: `feat(openapi): add Schema[A].toOpenAPISchema derivation`
  - Files: Schema extensions, tests

- [ ] 21. Comprehensive round-trip tests

  **What to do**:
  - Create `OpenAPIRoundTripSpec.scala`
  - Test that OpenAPI -> JSON -> OpenAPI preserves all data
  - Test with real-world OpenAPI examples (Petstore, etc.)
  - Test extension preservation
  - Test all edge cases

  **Parallelizable**: NO (needs all types complete)

  **References**:
  - `schema/shared/src/test/scala/zio/blocks/schema/json/JsonSchemaRoundTripSpec.scala` - Round-trip test pattern
  - Official OpenAPI examples: https://github.com/OAI/OpenAPI-Specification/tree/main/examples

  **Acceptance Criteria**:
  - [ ] Round-trip tests pass for all object types
  - [ ] Petstore example parses and round-trips correctly
  - [ ] Extensions preserved on all extensible objects

  **Commit**: YES
  - Message: `test(openapi): add comprehensive round-trip tests`
  - Files: `OpenAPIRoundTripSpec.scala`

- [ ] 22. Coverage and formatting

  **What to do**:
  - Run coverage: `sbt "project openapiJVM; coverage; test; coverageReport"`
  - Ensure >90% coverage
  - Format all code: `sbt openapiJVM/scalafmt openapiJVM/Test/scalafmt`
  - Add missing Scaladoc

  **Parallelizable**: NO (final step)

  **References**:
  - AGENTS.md coverage requirements

  **Acceptance Criteria**:
  - [ ] Coverage >90%
  - [ ] All code formatted
  - [ ] All public APIs have Scaladoc
  - [ ] `sbt openapiJVM/test` passes on both Scala 2.13 and 3.3

  **Commit**: YES
  - Message: `chore(openapi): ensure coverage and formatting`
  - Files: All openapi files

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1 | `feat(openapi): add openapi module structure` | build.sbt, openapi/** | sbt compile |
| 2 | `feat(openapi): add OpenAPI root object with smart constructor` | OpenAPI.scala, tests | sbt test |
| 3 | `feat(openapi): add Info, Contact, License objects` | *.scala, tests | sbt test |
| 4 | `feat(openapi): add Server and ServerVariable objects` | *.scala, tests | sbt test |
| 5 | `feat(openapi): add Tag and ExternalDocumentation objects` | *.scala, tests | sbt test |
| 6 | `feat(openapi): add Reference and ReferenceOr pattern` | *.scala, tests | sbt test |
| 7 | `feat(openapi): add SchemaObject wrapping JsonSchema` | *.scala, tests | sbt test |
| 8 | `feat(openapi): add Discriminator and XML objects` | *.scala, tests | sbt test |
| 9 | `feat(openapi): add Paths and PathItem objects` | *.scala, tests | sbt test |
| 10 | `feat(openapi): add Operation object` | *.scala, tests | sbt test |
| 11 | `feat(openapi): add Parameter and Header objects` | *.scala, tests | sbt test |
| 12 | `feat(openapi): add RequestBody object` | *.scala, tests | sbt test |
| 13 | `feat(openapi): add MediaType and Encoding objects` | *.scala, tests | sbt test |
| 14 | `feat(openapi): add Responses and Response objects` | *.scala, tests | sbt test |
| 15 | `feat(openapi): add Example, Link, Callback objects` | *.scala, tests | sbt test |
| 16 | `feat(openapi): add Components object` | *.scala, tests | sbt test |
| 17 | `feat(openapi): add SecurityScheme sealed trait` | *.scala, tests | sbt test |
| 18 | `feat(openapi): add OAuthFlows and OAuthFlow objects` | *.scala, tests | sbt test |
| 19 | `feat(openapi): add SecurityRequirement object` | *.scala, tests | sbt test |
| 20 | `feat(openapi): add Schema[A].toOpenAPISchema derivation` | *.scala, tests | sbt test |
| 21 | `test(openapi): add comprehensive round-trip tests` | *Spec.scala | sbt test |
| 22 | `chore(openapi): ensure coverage and formatting` | all | sbt coverage |

---

## Success Criteria

### Verification Commands
```bash
# Compile all platforms
sbt openapiJVM/compile openapiJS/compile openapiNative/compile

# Run tests
sbt openapiJVM/test

# Check coverage
sbt "project openapiJVM; coverage; test; coverageReport"

# Format
sbt openapiJVM/scalafmt openapiJVM/Test/scalafmt

# Cross-Scala
sbt "++2.13.18; openapiJVM/test"
sbt "++3.3.7; openapiJVM/test"
```

### Final Checklist
- [ ] All 30 OpenAPI 3.1 objects implemented
- [ ] All "Must Have" items present
- [ ] All "Must NOT Have" items absent
- [ ] TDD tests pass with >90% coverage
- [ ] JSON round-trip works for all types
- [ ] Extensions (x-*) preserved on all extensible objects
- [ ] Separate summary/description fields where required
- [ ] Smart constructors validate invariants
- [ ] Code formatted for both Scala versions
- [ ] Scaladoc on all public APIs

---

**Author**: Prometheus (OpenCode AI)  
**Status**: Ready for Execution
