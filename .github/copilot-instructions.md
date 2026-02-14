# Copilot Code Review Instructions

## Documentation Requirements

When reviewing a pull request, check whether it introduces a **new feature**, **new public API**, or **changes an existing public API**. If it does, verify that the PR also includes corresponding documentation updates under the `docs/` directory.

### What counts as a public API change

- Adding or removing a `public` trait, class, object, or type alias in any module under `src/main/scala/`
- Adding, removing, or changing the signature of a `public` method or value
- Adding a new data type, codec, optic, schema, or format
- Changing the behavior of an existing public method in a way that users would need to know about

### What documentation is expected

- **New data types or modules**: A new reference page under `docs/reference/` (e.g., `docs/reference/<type-name>.md`) and a sidebar entry in `docs/sidebars.js`
- **New methods or features on existing types**: Updates to the relevant existing reference page under `docs/`
- **Changed behavior or signatures**: Updates to any reference page that describes the affected API

### Review checklist

1. Scan the diff for new or modified `trait`, `class`, `object`, `def`, or `val` declarations that are public.
2. If public API surface has changed, check that `docs/` contains corresponding additions or updates.
3. If documentation is missing, request that the author add it before merging. Suggest which file under `docs/reference/` should be created or updated.
4. If the change is purely internal (private/package-private, test-only, build config), documentation is not required.
