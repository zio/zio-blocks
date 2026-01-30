# Add OpenAPI 3.1 Support

## Summary

Add a standalone OpenAPI 3.1.0 module (`zio-blocks-openapi`) that provides a complete, type-safe ADT for the OpenAPI specification, integrated with the existing JSON Schema 2020-12 implementation.

## Motivation

### Current State
- ZIO Blocks now has comprehensive JSON Schema 2020-12 support (817/844 official tests passing)
- zio-http has its own OpenAPI implementation with several pain points:
  - No OpenAPI extensions (`x-*`) support
  - No separate `summary` field (uses `description` for both)
  - Example generation issues
  - Hard dependency on zio-http types

### Goals
1. **Standalone library**: Can be used independently of zio-http or any web framework
2. **Type-safe**: Complete ADT with compile-time guarantees
3. **JSON Schema integration**: Leverage existing `JsonSchema` implementation
4. **Fix pain points**: Extensions, summary/description separation, proper examples
5. **Schema derivation**: `Schema[A].toOpenAPISchema` for automatic generation

## Design

### Module Structure
```
openapi/
├── shared/src/main/scala/zio/blocks/openapi/
│   ├── OpenAPI.scala           # Root object
│   ├── Info.scala              # Info, Contact, License
│   ├── Server.scala            # Server, ServerVariable
│   ├── PathItem.scala          # Paths, PathItem, Operation
│   ├── Parameter.scala         # Parameter, Header
│   ├── RequestBody.scala       # RequestBody, MediaType, Encoding
│   ├── Response.scala          # Response, Responses
│   ├── SchemaObject.scala      # Wraps JsonSchema + OpenAPI vocabulary
│   ├── SecurityScheme.scala    # All security scheme types
│   ├── Components.scala        # Reusable components
│   └── Reference.scala         # Reference, ReferenceOr[A]
└── shared/src/test/scala/zio/blocks/openapi/
    └── *Spec.scala             # TDD tests for all types
```

### Key Design Decisions

1. **SchemaObject wraps JsonSchema**
   ```scala
   final case class SchemaObject(
     schema: JsonSchema,
     discriminator: Option[Discriminator] = None,
     xml: Option[XML] = None,
     externalDocs: Option[ExternalDocumentation] = None,
     example: Option[Json] = None,  // deprecated, prefer examples
     extensions: Map[String, Json] = Map.empty
   )
   ```

2. **Extensions on all extensible objects**
   - 22 of 30 OpenAPI objects support `extensions: Map[String, Json]`
   - Enables custom `x-*` fields for integrations (GPT Actions, etc.)

3. **Smart constructors for invariants**
   - Version format validation
   - Required field enforcement
   - Mutual exclusivity (e.g., License.identifier vs url)

4. **Schema derivation via zio-blocks**
   - All OpenAPI types have `Schema[T]` instances
   - JSON encoding/decoding via existing codec infrastructure
   - `Schema[A].toOpenAPISchema` for user types

### OpenAPI 3.1 Objects (All 30)

| Object | Extensions | Required Fields |
|--------|------------|-----------------|
| OpenAPI | Yes | openapi, info |
| Info | Yes | title, version |
| Contact | Yes | - |
| License | Yes | name |
| Server | Yes | url |
| ServerVariable | Yes | default |
| Components | Yes | - |
| PathItem | Yes | - |
| Operation | Yes | responses |
| ExternalDocumentation | Yes | url |
| Parameter | Yes | name, in |
| RequestBody | Yes | content |
| MediaType | Yes | - |
| Encoding | Yes | - |
| Responses | Yes | - |
| Response | Yes | description |
| Callback | Yes | - |
| Example | Yes | - |
| Link | Yes | - |
| Header | Yes | - |
| Tag | Yes | name |
| Reference | No | $ref |
| SchemaObject | Yes | - |
| Discriminator | No | propertyName |
| XML | No | - |
| SecurityScheme | Yes | type |
| OAuthFlows | Yes | - |
| OAuthFlow | Yes | scopes |
| SecurityRequirement | No | - |
| Paths | No | - |

## Scope

### In Scope
- Complete OpenAPI 3.1.0 ADT (all 30 objects)
- Extensions (`x-*`) on all extensible objects
- Separate `summary` and `description` fields
- `SchemaObject` wrapping `JsonSchema` with OpenAPI vocabulary
- Smart constructors for invariants
- Internal `$ref` support (`#/components/...`)
- `Schema[A].toOpenAPISchema` derivation
- JSON round-trip via zio-blocks codec
- TDD tests with >90% coverage
- Cross-platform (JVM, JS, Native)
- Cross-Scala (2.13, 3.3)

### Out of Scope (Deferred)
- External `$ref` support (file://, https://)
- Webhooks implementation
- SwaggerUI helpers (leave to zio-http)
- zio-http integration (will live in zio-http repo)
- OpenAPI 3.0 compatibility
- AsyncAPI consideration

## Implementation Plan

### Phase 1: Project Setup
- Add `openapi` module to `build.sbt`
- Create directory structure
- Add dependency on `zio-blocks-schema`

### Phase 2: Core Metadata Types
- OpenAPI root object with smart constructor
- Info, Contact, License
- Server, ServerVariable
- Tag, ExternalDocumentation
- Reference, ReferenceOr[A]

### Phase 3: Schema Integration
- SchemaObject wrapping JsonSchema
- Discriminator, XML

### Phase 4: Path/Operation Objects
- Paths, PathItem
- Operation
- Parameter, Header

### Phase 5: Request/Response Objects
- RequestBody
- MediaType, Encoding
- Responses, Response
- Example, Link, Callback

### Phase 6: Components and Security
- Components
- SecurityScheme (APIKey, HTTP, OAuth2, OpenIdConnect, MutualTLS)
- OAuthFlows, OAuthFlow
- SecurityRequirement

### Phase 7: Integration and Polish
- `Schema[A].toOpenAPISchema` derivation
- Comprehensive round-trip tests
- Coverage and formatting

## Success Criteria

- [ ] All 30 OpenAPI 3.1 objects implemented
- [ ] JSON round-trip works for all types
- [ ] Extensions preserved on all extensible objects
- [ ] Smart constructors validate invariants
- [ ] TDD tests with >90% coverage
- [ ] Cross-platform (JVM, JS, Native) compilation
- [ ] Cross-Scala (2.13, 3.3) compilation
- [ ] Scaladoc on all public APIs

## Usage Example

```scala
import zio.blocks.openapi._
import zio.blocks.schema._

// Define your types
case class Pet(id: Long, name: String, tag: Option[String])
object Pet {
  implicit val schema: Schema[Pet] = Schema.derived
}

// Generate OpenAPI schema
val petSchema: SchemaObject = Schema[Pet].toOpenAPISchema

// Build OpenAPI spec
val api = OpenAPI(
  info = Info(title = "Pet Store", version = "1.0.0"),
  paths = Some(Map(
    "/pets" -> PathItem(
      get = Some(Operation(
        summary = Some("List all pets"),
        description = Some("Returns all pets from the system"),
        responses = Map(
          "200" -> ReferenceOr.Value(Response(
            description = "A list of pets",
            content = Map("application/json" -> MediaType(schema = Some(petSchema)))
          ))
        )
      ))
    )
  ))
)

// Serialize to JSON
val json: String = api.toJson
```

## Related

- JSON Schema 2020-12 implementation: #806
- zio-http OpenAPI: `zio.http.endpoint.openapi`
- OpenAPI 3.1 spec: https://spec.openapis.org/oas/v3.1.0

## Labels

- `enhancement`
- `openapi`
- `new-module`
