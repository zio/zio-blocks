# Copilot Code Review Instructions

## Documentation Requirements

When reviewing a pull request, check whether it introduces a **new feature**, **new public API**, or **changes an existing public API**. If it does, verify that the PR also includes corresponding documentation updates under the `docs/` directory.

### What counts as a public API change

- Adding or removing a `public` trait, class, object, or type alias in any module's `src/main/scala/` directory (for example, `*/src/main/scala/`)
- Adding, removing, or changing the signature of a `public` method or value
- Adding a new data type, codec, optic, schema, or format
- Changing the behavior of an existing public method in a way that users would need to know about

### What documentation is expected

- **New data types or modules**: A new reference page under `docs/reference/` (e.g., `docs/reference/<type-name>.md`) and a sidebar entry in `docs/sidebars.js`
- **New methods or features on existing types**: Updates to the relevant existing reference page under `docs/`
- **Changed behavior or signatures**: Updates to any reference page that describes the affected API

### Scaladoc requirements

When a PR introduces a **new public API** or **changes the behavior of an existing public API**, every affected public declaration must have a Scaladoc comment. If it is missing, suggest adding one as a review comment on the relevant line.

#### What needs Scaladoc

- New `trait`, `class`, `object`, or `type` that is public
- New or signature-changed public `def` or `val`
- Behavior changes to an existing public method (update the existing Scaladoc)

#### What to include in the Scaladoc

- A concise one-line summary of what the declaration does
- `@param` tags for every parameter (methods and constructors)
- `@tparam` tags for every type parameter
- `@return` tag describing the return value (for non-`Unit` methods)
- `@throws` tags for any documented exceptions
- `@note` or `@see` tags when there are important caveats or related APIs

#### Usage examples in Scaladoc

For **non-obvious APIs** — methods with complex type signatures, implicit parameters, higher-order functions, macro-based APIs, or anything where correct usage is not immediately clear from the signature alone — the Scaladoc **must** include a short usage example inside a `{{{` … `}}}` block. The example should be minimal and self-contained.

An API is considered non-obvious if any of the following apply:
- It accepts or returns higher-kinded types, type lambdas, or path-dependent types
- It relies on implicit/given parameters or context bounds that the caller must provide
- It uses macros or compile-time derivation
- It has multiple overloads where choosing the right one is not straightforward
- The method name alone does not clearly convey what it does or how to call it

#### How to suggest

When Scaladoc is missing or incomplete, leave a **suggestion** comment on the line of the declaration with a ready-to-commit Scaladoc block. For example:

````suggestion
/** Decodes a value of type `A` from the given input bytes.
  *
  * @param input the raw byte array to decode
  * @tparam A    the target type
  * @return      a `DecodeResult` containing either the decoded value or an error
  *
  * Example:
  * {{{
  * val result = Codec.decode[Person](bytes)
  * result match {
  *   case DecodeResult.Success(person) => println(person.name)
  *   case DecodeResult.Failure(err)    => println(s"Failed: $$err")
  * }
  * }}}
  */
````

#### Exceptions

- Internal (`private`, `private[pkg]`, `protected`) declarations do not require Scaladoc.
- Test-only or build-config-only changes do not require Scaladoc.
- Trivially obvious accessor methods (e.g., `def name: String`) may use a single-line `/** ... */` comment.

### Review checklist

1. Scan the diff for new or modified `trait`, `class`, `object`, `def`, or `val` declarations that are public.
2. If a public declaration is missing Scaladoc or its Scaladoc does not reflect the new behavior, suggest a Scaladoc block as a review suggestion.
3. If public API surface has changed, check that `docs/` contains corresponding additions or updates.
4. If documentation is missing, request that the author add it before merging. Suggest which file under `docs/reference/` should be created or updated.
5. If the change is purely internal (private/package-private, test-only, build config), documentation and Scaladoc are not required.
