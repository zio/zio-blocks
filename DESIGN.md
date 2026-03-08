# ZIO Schema 2.0 Migration System: Design Proposal

## 1. Vision
To provide a pure, algebraic, and 100% serializable framework for structural schema evolution in the ZIO ecosystem. The system represents transformations as first-class data, enabling schema evolution without requiring runtime representations of legacy data types.

## 2. Core Architectural Pillars

### A. DynamicMigration (The Serializable Core)
*   **Pure Data:** An untyped ADT (`sealed trait MigrationAction`) that operates on `DynamicValue`.
*   **Zero Logic:** No user-defined functions or closures allowed in the core.
*   **Storage-Ready:** Can be serialized to JSON/Protobuf for registry storage and offline DML generation.

### B. Path-Based Optics
*   **DynamicOptic:** All transformations target specific locations within nested structures using serializable optics.
*   **Deep Nesting:** Support for targeting fields at any level (e.g., `.user.address.zip`).

### C. MigrationBuilder (The Typed User API)
*   **Fluent DSL:** A type-safe API for users to define migrations (e.g., `Migration.builder[V1, V2].rename(_.name, "fullName").build`).
*   **Macro Validation:** The `.build` method performs a compile-time "Coverage Check" to ensure all target fields are accounted for.

## 3. The 12 Migration Actions
1.  **AddField:** Insert new field with default value.
2.  **DropField:** Remove existing field.
3.  **RenameField:** Map old field name to new field name.
4.  **TransformValue:** Reversible primitive transformations.
5.  **AddCase:** New enum case support.
6.  **DropCase:** Remove enum case.
7.  **RenameCase:** Map enum case names.
8.  **MakeOptional:** Required -> Optional.
9.  **MakeRequired:** Optional -> Required (requires default).
10. **TransformElements:** For Collection types.
11. **TransformKeys:** For Map types.
12. **TransformValues:** For Map types.

## 4. Competitive Edge: Refiner-Enhanced Verification
Unlike standard implementations, our approach includes an AI-derived **Verification Suite**. We use our proprietary 4-tier "Refiner" engine to analyze legacy schemas and automatically suggest the most logical migration path, which the user then confirms via the DSL.
