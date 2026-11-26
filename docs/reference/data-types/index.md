---
id: index
title: "Types & Abstractions"
description: "Introduction to the Types & Abstractions blocks of ZIO Blocks: Chunk, Maybe, Combinators and TypeId."
---

## Introduction

The blocks in this group are small, general-purpose building blocks. Two of them offer alternatives to everyday standard-library types that allocate less, the third checks the composition of tuples, eithers and unions at compile time, and the fourth keeps a type's identity as a value at runtime.

## The Blocks

The group has four blocks.

| Block                          | Artifact                   | What It Is For                                                                                  |
| ------------------------------ | -------------------------- | ----------------------------------------------------------------------------------------------- |
| [Chunk](../chunk.md)           | `zio-blocks-chunk`         | High-performance immutable indexed sequences with zero-boxing builders                          |
| [Maybe](../maybe.md)           | `zio-blocks-maybe`         | Optional values that store a present reference as is, without a wrapper object (Scala 3)        |
| [Combinators](../combinators.md) | `zio-blocks-combinators` | Compile-time composition and decomposition of tuples, eithers, and unions                       |
| [TypeId](../typeid.md)         | `zio-blocks-typeid`        | Compile-time type identity with rich metadata                                                   |

## How the Blocks Differ

`Chunk` is an immutable, indexed sequence with a functional interface. It stays fast for element access and is lazy for expensive operations such as repeated concatenation.

`Maybe` is a low-allocation alternative to `Option`. On Scala 3 it is an opaque type, so a present reference is stored as it is rather than inside a wrapper object, and only `null` and nested `Maybe` values are wrapped in `Present`. On Scala 2.13 it is a sealed trait and allocates like `Option`.

`Combinators` provides compile-time typeclasses for composing and decomposing tuples, `Either` types and union types, which is useful for code that must work with many shapes of the same operation.

`TypeId` answers a narrower question: which type is this? Scala erases most type information at runtime, and `TypeId` captures names, type parameters, parent types and annotations as data, which suits registries, code generation and dispatching on a type that would otherwise be erased.

## Where to Start

Start with [Chunk](../chunk.md) for sequences. Use [Maybe](../maybe.md) where allocation matters and the code is on Scala 3, [Combinators](../combinators.md) when writing generic code over tuples or eithers, and [TypeId](../typeid.md) when a program needs a type's identity as a value rather than its structure.
