Goal: Upgrade the existing `json` string interpolator so it supports fully type-safe interpolation in key, value, and string-literal contexts, with high-quality compile-time error messages. The implementation must be complete and production-ready, covering all stated requirements and avoiding mistakes made in earlier abandoned attempts.

INVESTIGATION STEP (MANDATORY)
Before writing any code:
- Visit and carefully read this page, including ALL comments and discussion:
  https://github.com/zio/zio-blocks/issues/801
- From that page, open and review any related CLOSED pull requests and their review comments.
- Understand:
  - what functionality was expected,
  - what approaches were tried,
  - why previous attempts were closed (e.g. incomplete coverage, regressions, weak typing rules, macro limitations, poor error messages, cross-platform failures, style issues).
- Keep these findings ONLY in your internal reasoning.
- Do NOT mention this issue, any PRs, or any links anywhere in the repository output.

ABSOLUTE RULES
- Do NOT reference any issue number, PR number, or link anywhere in:
  - branch names
  - commit messages
  - PR titles or descriptions
  - code comments
  - test names or descriptions
  - documentation
- Do NOT write phrases like “as discussed in the issue” or similar.
- Existing behavior must continue to work (no regressions).

REQUIRED FUNCTIONALITY

A) Key position interpolation
- Interpolation used in JSON object KEYS must accept ONLY “stringable” types.
- “Stringable” means the types defined in `PrimitiveType`.
- Any non-stringable type used as a key must fail at COMPILE TIME with a clear error message that includes:
  - context: key position
  - provided type
  - what is allowed and how to fix the error

B) Value position interpolation
- Interpolation used in JSON VALUES must accept any type `A` that has a `JsonEncoder[A]`.
- If no `JsonEncoder[A]` is available, compilation must fail with a clear error message that includes:
  - context: value position
  - provided type
  - requirement: `JsonEncoder[A]`
  - hint that the encoder can come from `JsonBinaryCodec` or be derived from `Schema[A]`

C) Interpolation inside JSON string literals
- Support interpolation inside JSON string values, for example:
  - `json"""{"id": "user-$userId"}"""`
- Only stringable (`PrimitiveType`) values are allowed inside string literals.
- Non-stringable types inside string literals must fail at COMPILE TIME with a clear error message that includes:
  - context: string literal
  - provided type
  - what is allowed and how to fix the error

D) Compile-time error quality
- All invalid interpolations must be rejected at compile time.
- Error messages must clearly state:
  - which context failed (key / value / string literal)
  - the provided type
  - the required constraint

FILES TO MODIFY
- The JSON interpolator macro implementation.
- Tests for the JSON interpolator.

TEST REQUIREMENTS
Extend:
- `schema/shared/src/test/scala/zio/blocks/schema/json/JsonInterpolatorSpec.scala`

Add tests that verify:
- All `PrimitiveType` stringable types work in KEY position.
- All `PrimitiveType` stringable types work inside STRING LITERALS.
- Any type with `JsonEncoder[A]` works in VALUE position.
  - Include examples using encoders derived from `Schema[A]`.
  - Include supported collections (e.g. List, Map) where applicable.
- Compile-time failure tests for:
  - non-stringable type used as a key
  - type without `JsonEncoder` used as a value
  - non-stringable type interpolated inside a string literal
- All existing interpolator tests continue to pass.

PLATFORM CONSTRAINTS
- Tests must pass on JVM, JS, and Native.
- If compile-time assertion helpers do not work on Native, follow existing project patterns for platform-specific or conditional tests.

STYLE & DESIGN CONSTRAINTS
- Follow existing macro style and project conventions.
- Prefer systematic logic based on `PrimitiveType` rather than ad-hoc type checks.
- Keep the change set focused: macro logic, tests, and minimal supporting helpers only.

DEFINITION OF DONE
- ✅ Key interpolation accepts exactly stringable `PrimitiveType` values
- ✅ Value interpolation accepts any `A` with a `JsonEncoder[A]`
- ✅ String-literal interpolation accepts only stringable values
- ✅ All invalid cases fail at compile time with clear diagnostics
- ✅ No regressions in existing behavior
- ✅ Test coverage is complete and passes on all supported platforms
- ✅ Known pitfalls from earlier closed attempts are avoided
