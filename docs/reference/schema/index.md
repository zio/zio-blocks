---
id: schema-index
title: "ZIO Blocks Schema"
---

## Introduction

ZIO Blocks Schema is the core type system and serialization framework that provides reified structural metadata for Scala data types. It enables type-safe schema definition, validation, optics-based data access, multi-format serialization, and runtime introspection — all derived from a single `Schema` definition.

**Related Types:**

- [`Allows`](./allows.md) — Compile-time capability token proving a type satisfies a structural grammar
- [`Binding`](./binding.md) — Operational machinery for constructing and deconstructing values
- [`Codec`](./codec.md) — Base abstraction for encoding and decoding values between formats
- [`DynamicOptic`](./dynamic-optic.md) — Runtime path through nested data structures
- [`DynamicSchema`](./dynamic-schema.md) — Type-erased schema container for serialization and transport
- [`DynamicValue`](./dynamic-value.md) — Schema-less, dynamically-typed representation of any structured value
- [`Formats`](./formats.md) — Unified abstraction bundling serialization and deserialization for a specific format
- [`Json`](./json.md) — Algebraic data type for representing and manipulating JSON values
- [`JsonDiffer`](./json-differ.md) — Diff algorithm computing minimal patches between JSON values
- [`JsonPatch`](./json-patch.md) — Composable patch operations transforming one JSON value into another
- [`JSON Schema`](./json-schema.md) — First-class support for JSON Schema 2020-12
- [`Lazy`](./lazy.md) — Deferred computation with monadic abstraction, memoization, and stack-safe evaluation
- [`Modifier`](./modifier.md) — Mechanism to attach metadata and configuration to schema elements
- [`Optics`](./optics.md) — Reflective optics for type-safe, composable access to nested data structures
- [`Patch`](./patch.md) — Type-safe, serializable transformations of data structures
- [`Reflect`](./reflect.md) — Foundational data structure containing reified structural information
- [`Registers`](./registers.md) — Register-based design for zero-allocation construction and deconstruction
- [`Schema`](./schema.md) — Primary data type containing reified structure of a Scala data type
- [`SchemaError`](./schema-error.md) — Structured error type for schema operations
- [`SchemaExpr`](./schema-expr.md) — Schema-aware expressions for evaluation and query language translation
- [`Structural Types`](./structural-types.md) — Duck typing with ZIO Blocks schemas
- [`Syntax`](./syntax.md) — Extension methods for fluent JSON encoding/decoding and patching
- [`Type Class Derivation`](./type-class-derivation.md) — Automatic generation of type class instances from schemas
- [`Validation`](./validation.md) — Declarative constraints on primitive values
- [`Xml`](./xml.md) — Type-safe, immutable representation of XML document structures

## Overview

The ZIO Blocks Schema module revolves around the `Schema` and `Reflect` types, which capture the complete structural description of Scala data types at runtime. From a single schema definition, you get automatic derivation of codecs (`Codec`, `Formats`), type-safe data access (`Optics`, `DynamicOptic`), structural validation (`Validation`, `SchemaError`), patching (`Patch`, `JsonPatch`), and serialization to multiple formats (`Json`, `Xml`, `JSON Schema`).

The type system is powered by a register-based architecture (`Registers`) that eliminates boxing overhead, and supports both compile-time (`Allows`) and runtime (`DynamicValue`, `DynamicSchema`) type manipulation. The `Deriver` system (`Type Class Derivation`) enables automatic generation of any type class instance from schema metadata.
