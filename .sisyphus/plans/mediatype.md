# MediaType Micro Library

## TL;DR

> **Quick Summary**: Create a new zero-dependency media type module for zio-blocks, providing MIME type representation with ~10K predefined types (generated from mime-db), a compile-time validated string interpolator, and q-factor support.
> 
> **Deliverables**:
> - `MediaType` case class with full MIME type representation
> - Generated predefined types with camelCase names (from mime-db)
> - `mediaType"..."` string interpolator with compile-time validation
> - File extension lookup (`MediaType.forFileExtension`)
> - Generator tool in project/ directory
> 
> **Estimated Effort**: Large
> **Parallel Execution**: YES - 3 waves
> **Critical Path**: Task 1 → Task 2 → Task 4 → Task 6 → Task 7 → Task 8

---

## Context

### Original Request
Create a new micro lib inside of blocks that offers MIME type support called "media type". Based on zio-http's implementation but with key changes:
- Predefined media types should have names without backticks (camelCase)
- Keep the generator from zio-http
- String interpolator for compile-time validated media types that maps to predefined instances


### Interview Summary
**Key Discussions**:
- Module name: `mediatype` (package: `zio.blocks.mediatype`, artifact: `zio-blocks-mediatype`)
- Cross-platform: JVM + JS
- Interpolator syntax: `mediaType"..."` (explicit form)
- Unknown types in interpolator: ALLOWED (creates new MediaType at compile-time)
- Parameters in interpolator: Supported (e.g., `mediaType"text/html; charset=utf-8"`)

- Inline macros: No typelevel/literally dependency
- All ~10K types from mime-db

**Research Findings**:
- zio-http MediaType: mainType, subType, compressible, binary, fileExtensions, extensions, parameters
- Generator fetches from `https://raw.githubusercontent.com/jshttp/mime-db/master/db.json`
- zio-blocks uses crossProject with CrossType.Full for JVM+JS
- Test framework: ZIO Test with BaseSpec pattern
- Scala 3 macros: quoted expressions `'{...}`, Scala 2: blackbox.Context

### Metis Review
**Identified Gaps** (addressed):
- Macro portability Scala 2/3: Will implement separate source directories (`scala-2/`, `scala-3/`)
- Generator output bloat (~10K types): Acceptable per user decision, chunked into type groups
- Malformed interpolator input: Will fail at compile-time with clear error message
- Generator update strategy: Manual via explicit sbt command (not automated)

---

## Work Objectives

### Core Objective
Create a pure media type micro library with compile-time validated interpolators and comprehensive predefined types, following zio-blocks patterns.

### Concrete Deliverables
- `mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaType.scala`
- `mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaTypes.scala` (generated)
- `mediatype/shared/src/main/scala-3/zio/blocks/mediatype/MediaTypeLiteral.scala`
- `mediatype/shared/src/main/scala-2/zio/blocks/mediatype/MediaTypeLiteral.scala`
- `project/GenerateMediaTypes.scala` (generator tool)
- Tests for all functionality

### Definition of Done
- [ ] `sbt ++3.7.4; mediatypeJVM/test` passes
- [ ] `sbt ++2.13.x; mediatypeJVM/test` passes
- [ ] `sbt ++3.7.4; mediatypeJS/test` passes
- [ ] `mediaType"application/json"` compiles and returns predefined instance
- [ ] `mediaType"custom/type"` compiles and creates new instance
- [ ] `mediaType"invalid"` fails to compile with clear error
- [ ] All ~10K mime-db types generated with camelCase names
- [ ] `MediaType.forFileExtension("json")` returns `Some(MediaType.application.json)`

### Must Have
- MediaType case class with: mainType, subType, compressible, binary, fileExtensions, extensions, parameters
- Generated predefined types grouped by mainType (application, text, image, etc.)
- `mediaType"..."` interpolator with compile-time validation
- Interpolator supports parameters: `mediaType"text/html; charset=utf-8"`
- Unknown types allowed in interpolator
- `matches()` method for wildcard-aware matching
- `parse()` method for runtime parsing
- `forFileExtension()` method for file extension lookup
- Cross-platform JVM + JS support
- Scala 2.13 and Scala 3 support

### Must NOT Have (Guardrails)
- NO Accept header parsing (HTTP-specific)
- NO Content-Type header formatting (HTTP-specific)
- NO Schema integration (Schema[MediaType])
- NO HTTP-specific functionality whatsoever
- NO runtime reflection or dynamic loading
- NO typelevel/literally dependency
- NO backticks in generated type names

---

## Verification Strategy

> **UNIVERSAL RULE: ZERO HUMAN INTERVENTION**
>
> ALL tasks in this plan MUST be verifiable WITHOUT any human action.

### Test Decision
- **Infrastructure exists**: YES (ZIO Test framework)
- **Automated tests**: TDD (test-driven development)
- **Framework**: ZIO Test with BaseSpec pattern

### If TDD Enabled

Each TODO follows RED-GREEN-REFACTOR:

**Task Structure:**
1. **RED**: Write failing test first
   - Test command: `sbt '++3.7.4; mediatypeJVM/test'`
   - Expected: FAIL (test exists, implementation doesn't)
2. **GREEN**: Implement minimum code to pass
   - Command: `sbt '++3.7.4; mediatypeJVM/test'`
   - Expected: PASS
3. **REFACTOR**: Clean up while keeping green

### Agent-Executed QA Scenarios (MANDATORY)

**Verification Commands:**
```bash
# Scala 3 JVM tests
sbt '++3.7.4; mediatypeJVM/test'

# Scala 2 JVM tests  
sbt '++2.13.16; mediatypeJVM/test'

# Scala 3 JS tests
sbt '++3.7.4; mediatypeJS/test'

# Generate media types
sbt generateMediaTypes

# Compile check (interpolator validation)
sbt '++3.7.4; mediatypeJVM/compile'
```

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately):
├── Task 1: Project setup (build.sbt, directory structure)
└── Task 3: Generator tool (project/GenerateMediaTypes.scala)

Wave 2 (After Wave 1):
├── Task 2: MediaType + MediaTypeWithQFactor case classes
├── Task 4: Generate predefined types (run generator)
├── Task 5: Runtime parsing (MediaType.parse)
└── Task 5b: File extension lookup (MediaType.forFileExtension)

Wave 3 (After Wave 2):
├── Task 6: String interpolator (Scala 3)
├── Task 7: String interpolator (Scala 2)
└── Task 8: Cross-platform + cross-Scala verification

Critical Path: Task 1 → Task 2 → Task 4 → Task 6 → Task 8
Parallel Speedup: ~40% faster than sequential
```

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|---------------------|
| 1 | None | 2, 3, 4, 5, 6, 7 | 3 |
| 2 | 1 | 4, 5, 6, 7 | 3 |
| 3 | 1 | 4 | 1, 2 |
| 4 | 2, 3 | 6, 7 | 5, 5b |
| 5 | 2 | 8 | 4, 5b |
| 5b | 4 | 8 | 5 |
| 6 | 4 | 8 | 7 |
| 7 | 4 | 8 | 6 |
| 8 | 5, 5b, 6, 7 | None | None (final) |

---

## TODOs

- [x] 1. Project Setup: Create mediatype module structure

  **What to do**:
  - Add `mediatype` crossProject to build.sbt following chunk/schema patterns
  - Configure crossProjectSettings, stdSettings, jsSettings, mimaSettings
  - Create directory structure:
    ```
    mediatype/
      shared/src/main/scala/zio/blocks/mediatype/
      shared/src/test/scala/zio/blocks/mediatype/
      shared/src/main/scala-2/zio/blocks/mediatype/
      shared/src/main/scala-3/zio/blocks/mediatype/
      jvm/src/main/scala/zio/blocks/mediatype/
      js/src/main/scala/zio/blocks/mediatype/
    ```
  - Add to root project aggregation
  - Create placeholder package object

  **Must NOT do**:
  - Add any dependencies beyond scala-library
  - Create HTTP-specific code

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Straightforward project scaffolding with clear patterns to follow
  - **Skills**: []
    - No special skills needed - standard sbt/Scala work

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 3)
  - **Blocks**: Tasks 2, 3, 4, 5, 6, 7
  - **Blocked By**: None

  **References**:
  - `build.sbt:chunk` - Cross-project pattern to follow
  - `project/BuildHelper.scala:crossProjectSettings` - Settings to apply
  - `chunk/shared/src/main/scala/zio/blocks/chunk/` - Package structure reference

  **Acceptance Criteria**:

  **TDD - Test Setup:**
  - [ ] Create `mediatype/shared/src/test/scala/zio/blocks/mediatype/MediaTypeBaseSpec.scala`
  - [ ] Create minimal placeholder test that asserts package exists

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: Module compiles successfully
    Tool: Bash (sbt)
    Preconditions: Build files updated
    Steps:
      1. sbt '++3.7.4; mediatypeJVM/compile'
      2. Assert: Exit code 0
      3. sbt '++3.7.4; mediatypeJS/compile'
      4. Assert: Exit code 0
    Expected Result: Both platforms compile
    Evidence: sbt output captured
  ```

  **Commit**: YES
  - Message: `feat(mediatype): add module structure and build configuration`
  - Files: `build.sbt`, `mediatype/**`

---

- [x] 2. Implement MediaType case class

  **What to do**:
  - Write failing tests first for:
    - MediaType construction with all fields
    - MediaType.matches() wildcard logic
    - fullType computed property
  - Implement `MediaType` case class:
    ```scala
    final case class MediaType(
      mainType: String,
      subType: String,
      compressible: Boolean = false,
      binary: Boolean = false,
      fileExtensions: List[String] = Nil,
      extensions: Map[String, String] = Map.empty,
      parameters: Map[String, String] = Map.empty
    ) {
      val fullType: String = s"$mainType/$subType"
      def matches(other: MediaType, ignoreParameters: Boolean = false): Boolean = ...
    }
    ```

  **Must NOT do**:
  - Add HTTP header parsing logic
  - Add Schema derivation

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: Standard case class implementation with clear specifications
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO (depends on Task 1)
  - **Parallel Group**: Wave 2
  - **Blocks**: Tasks 4, 5, 6, 7
  - **Blocked By**: Task 1

  **References**:
  - zio-http `MediaType.scala` (researched) - Field structure and matches() logic
  - `chunk/shared/src/test/scala/zio/blocks/chunk/ChunkSpec.scala` - Test patterns

  **Acceptance Criteria**:

  **TDD:**
  - [ ] Test file: `mediatype/shared/src/test/scala/zio/blocks/mediatype/MediaTypeSpec.scala`
  - [ ] Tests cover: construction, fullType, matches() with wildcards, parameters
  - [ ] `sbt '++3.7.4; mediatypeJVM/test'` → PASS

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: MediaType construction and matching
    Tool: Bash (sbt)
    Preconditions: Task 1 complete
    Steps:
      1. Run: sbt '++3.7.4; mediatypeJVM/testOnly *MediaTypeSpec'
      2. Assert: Exit code 0
      3. Assert: Output contains "All tests passed"
    Expected Result: All MediaType tests pass
    Evidence: Test output captured

  Scenario: Wildcard matching works correctly
    Tool: Bash (sbt REPL verification via test)
    Preconditions: MediaType implemented
    Steps:
      1. Test asserts: MediaType("*", "*").matches(MediaType("text", "html")) == true
      2. Test asserts: MediaType("text", "*").matches(MediaType("text", "html")) == true
      3. Test asserts: MediaType("text", "html").matches(MediaType("text", "plain")) == false
    Expected Result: All wildcard scenarios covered in tests
    Evidence: Test output
  ```

  **Commit**: YES
  - Message: `feat(mediatype): implement MediaType case class`
  - Files: `mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaType.scala`, `*Spec.scala`

---

- [x] 3. Create Generator Tool

  **What to do**:
  - Create `project/GenerateMediaTypes.scala` following zio-http's pattern
  - Fetch mime-db JSON from GitHub
  - Parse and transform to Scala code with:
    - camelCase name transformation (form-data → formData)
    - Group by mainType (application, text, image, etc.)
    - Binary detection heuristics
  - Generate `MediaTypes.scala` with structure:
    ```scala
    private[mediatype] trait MediaTypes {
      lazy val allMediaTypes: List[MediaType] = ...
      lazy val any: MediaType = new MediaType("*", "*")
      
      object application {
        lazy val json: MediaType = new MediaType("application", "json", ...)
        lazy val formData: MediaType = new MediaType("application", "x-www-form-urlencoded", ...)
        // ...
      }
      object text { ... }
      object image { ... }
      // ...
    }
    ```
  - Add sbt command alias: `generateMediaTypes`
  - Handle name collisions (reserved words, duplicates)

  **Must NOT do**:
  - Use backticks in generated names
  - Generate HTTP-specific code
  - Add runtime dependencies

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Complex code generation with parsing, transformation, and edge case handling
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 1)
  - **Blocks**: Task 4
  - **Blocked By**: Task 1 (needs directory structure)

  **References**:
  - zio-http `GenerateMediaTypes.scala` (researched) - Generator structure and patterns
  - `project/BuildHelper.scala` - sbt plugin patterns in this project
  - https://raw.githubusercontent.com/jshttp/mime-db/master/db.json - Data source

  **Acceptance Criteria**:

  **TDD:**
  - [ ] Test name transformation logic: `form-data` → `formData`, `x-www-form-urlencoded` → `xWwwFormUrlencoded`
  - [ ] Test reserved word handling: if subtype is `type` → `type_` or similar
  - [ ] Generator runs without error: `sbt generateMediaTypes`

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: Generator produces valid Scala code
    Tool: Bash (sbt)
    Preconditions: Generator implemented
    Steps:
      1. Run: sbt generateMediaTypes
      2. Assert: Exit code 0
      3. Assert: File exists at mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaTypes.scala
      4. Run: sbt '++3.7.4; mediatypeJVM/compile'
      5. Assert: Generated code compiles without errors
    Expected Result: Generator creates valid, compilable Scala
    Evidence: File content sample, compile output

  Scenario: Generated names are camelCase without backticks
    Tool: Bash (grep verification)
    Preconditions: MediaTypes.scala generated
    Steps:
      1. grep -c '`' mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaTypes.scala
      2. Assert: Count is 0 (no backticks)
      3. grep 'lazy val formData' in file
      4. Assert: Found (camelCase used)
    Expected Result: No backticks, camelCase names
    Evidence: grep output
  ```

  **Commit**: YES
  - Message: `feat(mediatype): add GenerateMediaTypes tool in project/`
  - Files: `project/GenerateMediaTypes.scala`, `build.sbt` (alias)

---

- [x] 4. Generate Predefined MediaTypes

  **What to do**:
  - Run the generator: `sbt generateMediaTypes`
  - Verify generated file compiles
  - Create `MediaType` companion object that extends `MediaTypes` trait
  - Ensure all ~10K types are accessible: `MediaType.application.json`, etc.
  - Add test verifying count matches mime-db

  **Must NOT do**:
  - Manually edit generated file
  - Add types not in mime-db

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple execution of generator and verification
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Task 5)
  - **Blocks**: Tasks 6, 7
  - **Blocked By**: Tasks 2, 3

  **References**:
  - `project/GenerateMediaTypes.scala` - Generator to run
  - `mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaType.scala` - Companion object location

  **Acceptance Criteria**:

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: Generated types accessible and correct
    Tool: Bash (sbt)
    Preconditions: Generator run, code compiles
    Steps:
      1. Test asserts: MediaType.application.json.fullType == "application/json"
      2. Test asserts: MediaType.text.html.fullType == "text/html"
      3. Test asserts: MediaType.multipart.formData.fullType == "multipart/form-data"
      4. Run: sbt '++3.7.4; mediatypeJVM/testOnly *MediaTypesSpec'
      5. Assert: Exit code 0
    Expected Result: Predefined types work correctly
    Evidence: Test output

  Scenario: Type count matches mime-db
    Tool: Bash (sbt test)
    Preconditions: MediaTypes generated
    Steps:
      1. Test asserts: MediaType.allMediaTypes.size >= 1000 (sanity check)
      2. Test asserts: MediaType.allMediaTypes contains common types
    Expected Result: Large number of types present
    Evidence: Test output with count
  ```

  **Commit**: YES
  - Message: `feat(mediatype): generate predefined media types from mime-db`
  - Files: `mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaTypes.scala`

---

- [x] 5. Implement Runtime Parsing

  **What to do**:
  - Write failing tests for parse scenarios
  - Implement `MediaType.parse(s: String): Either[String, MediaType]`:
    - Parse format: `mainType/subType[; param=value]*`
    - Look up in predefined types first (return exact instance)
    - Create new instance for unknown types
    - Handle parameter parsing
  - Implement `MediaType.unsafeFromString(s: String): MediaType`

  **Must NOT do**:
  - Add HTTP header parsing (just raw MIME string)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: Standard parsing logic with clear format
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Task 4)
  - **Blocks**: Task 8
  - **Blocked By**: Task 2

  **References**:
  - zio-http `MediaType.forContentType` (researched) - Parsing approach
  - `mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaType.scala` - Where to add

  **Acceptance Criteria**:

  **TDD:**
  - [ ] Test: `MediaType.parse("application/json")` → `Right(MediaType.application.json)`
  - [ ] Test: `MediaType.parse("text/html; charset=utf-8")` → Right with parameters
  - [ ] Test: `MediaType.parse("custom/type")` → Right (new instance)
  - [ ] Test: `MediaType.parse("invalid")` → Left with error message
  - [ ] `sbt '++3.7.4; mediatypeJVM/testOnly *MediaTypeParseSpec'` → PASS

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: Parse known types returns predefined instance
    Tool: Bash (sbt test)
    Preconditions: MediaType.parse implemented
    Steps:
      1. Test asserts: MediaType.parse("application/json") eq Right(MediaType.application.json) (reference equality)
      2. Run: sbt '++3.7.4; mediatypeJVM/testOnly *MediaTypeParseSpec'
      3. Assert: Exit code 0
    Expected Result: Known types return predefined instances
    Evidence: Test output

  Scenario: Parse with parameters
    Tool: Bash (sbt test)
    Preconditions: Parameter parsing implemented
    Steps:
      1. Test asserts: MediaType.parse("text/html; charset=utf-8").map(_.parameters) == Right(Map("charset" -> "utf-8"))
    Expected Result: Parameters correctly parsed
    Evidence: Test output
  ```

  **Commit**: YES
  - Message: `feat(mediatype): implement MediaType.parse for runtime parsing`
  - Files: `MediaType.scala`, `MediaTypeParseSpec.scala`

---

- [ ] 5b. Implement File Extension Lookup

  **What to do**:
  - Write failing tests for file extension lookup
  - Implement `MediaType.forFileExtension(ext: String): Option[MediaType]`:
    - Look up extension in generated extension map
    - Return predefined MediaType instance if found
    - Return None for unknown extensions
  - Build extension map from generated types (using fileExtensions field)
  - Prefer text media types for overlapping extensions (e.g., "js" → text/javascript over application/javascript)

  **Must NOT do**:
  - Add filesystem operations
  - Guess media types not in the database

  **Recommended Agent Profile**:
  - **Category**: `unspecified-low`
    - Reason: Standard lookup implementation with clear requirements
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Task 5)
  - **Blocks**: Task 8
  - **Blocked By**: Task 4 (needs generated types with fileExtensions)

  **References**:
  - zio-http `MediaType.forFileExtension` (researched) - Lookup approach
  - `mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaTypes.scala` - Where extension map is generated

  **Acceptance Criteria**:

  **TDD:**
  - [ ] Test: `MediaType.forFileExtension("json")` → `Some(MediaType.application.json)`
  - [ ] Test: `MediaType.forFileExtension("html")` → `Some(MediaType.text.html)`
  - [ ] Test: `MediaType.forFileExtension("png")` → `Some(MediaType.image.png)`
  - [ ] Test: `MediaType.forFileExtension("unknown123")` → `None`
  - [ ] Test: `MediaType.forFileExtension("js")` → `Some(MediaType.text.javascript)` (prefers text)
  - [ ] `sbt '++3.7.4; mediatypeJVM/testOnly *FileExtensionSpec'` → PASS

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: Common file extensions resolve correctly
    Tool: Bash (sbt test)
    Preconditions: Extension lookup implemented
    Steps:
      1. Test asserts: MediaType.forFileExtension("json") == Some(MediaType.application.json)
      2. Test asserts: MediaType.forFileExtension("css") == Some(MediaType.text.css)
      3. Test asserts: MediaType.forFileExtension("jpg") == Some(MediaType.image.jpeg)
      4. Run: sbt '++3.7.4; mediatypeJVM/testOnly *FileExtensionSpec'
      5. Assert: Exit code 0
    Expected Result: All common extensions resolve
    Evidence: Test output

  Scenario: Unknown extensions return None
    Tool: Bash (sbt test)
    Preconditions: Extension lookup implemented
    Steps:
      1. Test asserts: MediaType.forFileExtension("xyz123") == None
      2. Test asserts: MediaType.forFileExtension("") == None
    Expected Result: Unknown extensions return None
    Evidence: Test output
  ```

  **Commit**: YES
  - Message: `feat(mediatype): implement MediaType.forFileExtension lookup`
  - Files: `MediaType.scala`, `FileExtensionSpec.scala`

---

- [ ] 6. Implement String Interpolator (Scala 3)

  **What to do**:
  - Write failing compile-time tests
  - Create `mediatype/shared/src/main/scala-3/zio/blocks/mediatype/MediaTypeLiteral.scala`:
    ```scala
    import scala.quoted.*
    
    extension (inline ctx: StringContext)
      inline def mediaType(inline args: Any*): MediaType =
        ${MediaTypeLiteral.apply('ctx, 'args)}
    
    object MediaTypeLiteral {
      def apply(ctx: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[MediaType] = ...
    }
    ```
  - Compile-time validation:
    - Must have format `mainType/subType[; params]*`
    - Map to predefined instance if exists
    - Create new instance if unknown but valid format
    - Fail compilation if malformed
  - Support parameters: `mediaType"text/html; charset=utf-8"`

  **Must NOT do**:
  - Allow variable interpolation (only literals)
  - Add runtime overhead for known types

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
    - Reason: Scala 3 macro implementation requires deep metaprogramming knowledge
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Task 7)
  - **Blocks**: Task 8
  - **Blocked By**: Task 4

  **References**:
  - Research findings: Scala 3 quoted expressions pattern
  - `typeid/shared/src/main/scala-3/` - Scala 3 specific code location in this project
  - http4s URI literal (researched) - Interpolator pattern

  **Acceptance Criteria**:

  **TDD:**
  - [ ] Test: `mediaType"application/json"` compiles to `MediaType.application.json`
  - [ ] Test: `mediaType"custom/unknown"` compiles to new MediaType
  - [ ] Test: `mediaType"text/html; charset=utf-8"` compiles with parameters
  - [ ] Test: `mediaType"invalid"` fails to compile with EXPLICIT error message check
  - [ ] Test: `mediaType"no-slash"` fails with error containing "must contain '/'"
  - [ ] Test: `mediaType""` fails with error containing "empty" or similar
  - [ ] `sbt '++3.7.4; mediatypeJVM/testOnly *InterpolatorSpec'` → PASS

  **Negative Test Pattern (MANDATORY):**
  Use `typeCheck` or `typeCheckErrors` to verify error messages explicitly:
  ```scala
  // Scala 3 pattern using typeCheckErrors
  test("invalid media type produces clear error") {
    val errors = typeCheckErrors("""mediaType"invalid"""")
    assertTrue(errors.exists(_.message.contains("must contain '/'")))
  }
  
  // Or using assertDoesNotCompile with message check
  test("empty string rejected with clear message") {
    val result = typeCheck("""mediaType""""")
    assertTrue(result.isLeft)
    assertTrue(result.left.get.contains("empty"))
  }
  ```
  
  **Error messages MUST be user-friendly:**
  - `mediaType"invalid"` → "Invalid media type: must contain '/' separator"
  - `mediaType""` → "Invalid media type: cannot be empty"
  - `mediaType"foo/"` → "Invalid media type: subtype cannot be empty"
  - `mediaType"/bar"` → "Invalid media type: main type cannot be empty"

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: Interpolator maps to predefined instances
    Tool: Bash (sbt test)
    Preconditions: Scala 3 interpolator implemented
    Steps:
      1. Test uses: val x = mediaType"application/json"
      2. Test asserts: x eq MediaType.application.json (reference equality)
      3. Run: sbt '++3.7.4; mediatypeJVM/testOnly *InterpolatorSpec'
      4. Assert: Exit code 0
    Expected Result: Interpolator returns predefined instances
    Evidence: Test output

  Scenario: Malformed input fails compilation with clear error messages
    Tool: Bash (sbt)
    Preconditions: Interpolator validation implemented
    Steps:
      1. Test uses typeCheckErrors to capture compile error
      2. Test asserts error message contains expected substring
      3. Test covers: missing slash, empty string, empty main type, empty subtype
      4. Run: sbt '++3.7.4; mediatypeJVM/testOnly *InterpolatorSpec'
      5. Assert: Exit code 0 (tests verify compile failure WITH message check)
    Expected Result: Invalid inputs rejected with helpful error messages
    Evidence: Test output showing error message assertions pass
  ```

  **Commit**: YES
  - Message: `feat(mediatype): implement mediaType string interpolator for Scala 3`
  - Files: `scala-3/MediaTypeLiteral.scala`, `InterpolatorSpec.scala`

---

- [ ] 7. Implement String Interpolator (Scala 2.13)

  **What to do**:
  - Write failing tests (same as Scala 3 but different implementation)
  - Create `mediatype/shared/src/main/scala-2/zio/blocks/mediatype/MediaTypeLiteral.scala`:
    ```scala
    import scala.reflect.macros.blackbox.Context
    
    object MediaTypeLiteral {
      def apply(c: Context)(args: c.Expr[Any]*): c.Expr[MediaType] = ...
    }
    
    implicit class MediaTypeStringContext(val sc: StringContext) extends AnyVal {
      def mediaType(args: Any*): MediaType = macro MediaTypeLiteral.apply
    }
    ```
  - Same validation logic as Scala 3
  - Same compile-time behavior
  - Same error messages as Scala 3

  **Must NOT do**:
  - Differ in behavior from Scala 3 version
  - Differ in error messages from Scala 3 version

  **Recommended Agent Profile**:
  - **Category**: `ultrabrain`
    - Reason: Scala 2 macro implementation with blackbox context
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Task 6)
  - **Blocks**: Task 8
  - **Blocked By**: Task 4

  **References**:
  - Research findings: Scala 2 blackbox.Context pattern
  - http4s Scala 2 literal macros (researched)
  - `schema/shared/src/main/scala-2/` - Scala 2 specific code location

  **Acceptance Criteria**:

  **TDD:**
  - [ ] Test: `mediaType"application/json"` compiles to `MediaType.application.json`
  - [ ] Test: `mediaType"custom/unknown"` compiles to new MediaType
  - [ ] Test: `mediaType"text/html; charset=utf-8"` compiles with parameters
  - [ ] Test: `mediaType"invalid"` fails with error containing "must contain '/'"
  - [ ] Test: `mediaType""` fails with error containing "empty"
  - [ ] `sbt '++2.13.16; mediatypeJVM/testOnly *InterpolatorSpec'` → PASS

  **Negative Test Pattern (MANDATORY - same as Scala 3):**
  Use `typeCheck` or compile-time error checking to verify error messages:
  ```scala
  // Scala 2 pattern
  test("invalid media type produces clear error") {
    val result = typeCheck("""mediaType"invalid"""")
    assert(result.isLeft)
    assert(result.left.get.contains("must contain '/'"))
  }
  ```
  
  **Error messages MUST match Scala 3 exactly:**
  - `mediaType"invalid"` → "Invalid media type: must contain '/' separator"
  - `mediaType""` → "Invalid media type: cannot be empty"
  - `mediaType"foo/"` → "Invalid media type: subtype cannot be empty"
  - `mediaType"/bar"` → "Invalid media type: main type cannot be empty"

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: Scala 2 interpolator behaves identically to Scala 3
    Tool: Bash (sbt)
    Preconditions: Scala 2 interpolator implemented
    Steps:
      1. Run: sbt '++2.13.16; mediatypeJVM/testOnly *InterpolatorSpec'
      2. Assert: Exit code 0
      3. Assert: Same test cases pass as Scala 3
      4. Assert: Error messages match Scala 3 exactly
    Expected Result: Identical behavior and error messages across Scala versions
    Evidence: Test output showing error message assertions pass
  ```

  **Commit**: YES
  - Message: `feat(mediatype): implement mediaType string interpolator for Scala 2`
  - Files: `scala-2/MediaTypeLiteral.scala`

---

- [ ] 8. Cross-Platform and Cross-Scala Verification

  **What to do**:
  - Run full test suite on all platforms and Scala versions
  - Fix any platform-specific issues
  - Ensure JS bundle size is reasonable
  - Final cleanup and formatting

  **Must NOT do**:
  - Skip any platform/version combination
  - Leave failing tests

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Verification and minor fixes
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO (final verification)
  - **Parallel Group**: Wave 3 (sequential after 6, 7)
  - **Blocks**: None (final task)
  - **Blocked By**: Tasks 5, 6, 7

  **References**:
  - `build.sbt` - Cross-build configuration
  - AGENTS.md - Verify workflow instructions

  **Acceptance Criteria**:

  **Agent-Executed QA Scenarios:**

  ```
  Scenario: All platforms and Scala versions pass
    Tool: Bash (sbt)
    Preconditions: All previous tasks complete
    Steps:
      1. sbt '++3.7.4; mediatypeJVM/test'
      2. Assert: Exit code 0
      3. sbt '++2.13.16; mediatypeJVM/test'
      4. Assert: Exit code 0
      5. sbt '++3.7.4; mediatypeJS/test'
      6. Assert: Exit code 0
      7. sbt '++2.13.16; mediatypeJS/test'
      8. Assert: Exit code 0
    Expected Result: All 4 combinations pass
    Evidence: Test output for each

  Scenario: Code is formatted
    Tool: Bash (sbt)
    Preconditions: All tests pass
    Steps:
      1. sbt '++3.7.4; mediatypeJVM/scalafmt; mediatypeJVM/Test/scalafmt'
      2. sbt '++3.7.4; mediatypeJS/scalafmt; mediatypeJS/Test/scalafmt'
    Expected Result: Code formatted
    Evidence: sbt output
  ```

  **Commit**: YES
  - Message: `chore(mediatype): cross-platform verification and formatting`
  - Files: Any formatting changes

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1 | `feat(mediatype): add module structure and build configuration` | build.sbt, mediatype/** | compile |
| 2 | `feat(mediatype): implement MediaType case class` | MediaType.scala, *Spec.scala | test |
| 3 | `feat(mediatype): add GenerateMediaTypes tool in project/` | project/GenerateMediaTypes.scala | generateMediaTypes |
| 4 | `feat(mediatype): generate predefined media types from mime-db` | MediaTypes.scala | compile + test |
| 5 | `feat(mediatype): implement MediaType.parse for runtime parsing` | MediaType.scala | test |
| 5b | `feat(mediatype): implement MediaType.forFileExtension lookup` | MediaType.scala | test |
| 6 | `feat(mediatype): implement mt string interpolator for Scala 3` | scala-3/MediaTypeLiteral.scala | test |
| 7 | `feat(mediatype): implement mt string interpolator for Scala 2` | scala-2/MediaTypeLiteral.scala | test |
| 8 | `chore(mediatype): cross-platform verification and formatting` | * | full test |

---

## Success Criteria

### Verification Commands
```bash
# Full verification suite
sbt '++3.7.4; mediatypeJVM/test'      # Expected: All tests pass
sbt '++2.13.16; mediatypeJVM/test'    # Expected: All tests pass
sbt '++3.7.4; mediatypeJS/test'       # Expected: All tests pass
sbt '++2.13.16; mediatypeJS/test'     # Expected: All tests pass

# Interpolator works
# In test: mediaType"application/json" eq MediaType.application.json

# Generator works
sbt generateMediaTypes                 # Expected: MediaTypes.scala generated
```

### Final Checklist
- [ ] All "Must Have" present
- [ ] All "Must NOT Have" absent
- [ ] All 4 platform/version combinations pass tests
- [ ] No backticks in generated code
- [ ] Interpolator maps to predefined instances
- [ ] Unknown types allowed in interpolator
- [ ] Parameters supported in interpolator
- [ ] File extension lookup works for common extensions
