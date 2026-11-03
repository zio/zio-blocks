---
id: index
title: "Data Types"
description: "Introduction to the Data Types blocks of ZIO Blocks: Chunk, Maybe and Combinators."
---

## Introduction

The blocks in this group are small, general-purpose building blocks. Two of them offer alternatives to everyday standard-library types that allocate less, and the third checks the composition of tuples, eithers and unions at compile time.

## The Blocks

The group has three blocks.

| Block                          | Artifact                   | What It Is For                                                                                  |
| ------------------------------ | -------------------------- | ----------------------------------------------------------------------------------------------- |
| [Chunk](../chunk.md)           | `zio-blocks-chunk`         | High-performance immutable indexed sequences with zero-boxing builders                          |
| [Maybe](../maybe.md)           | `zio-blocks-maybe`         | Optional values that store a present reference as is, without a wrapper object (Scala 3)        |
| [Combinators](../combinators.md) | `zio-blocks-combinators` | Compile-time composition and decomposition of tuples, eithers, and unions                       |

## How the Blocks Differ

`Chunk` is an immutable, indexed sequence with a functional interface. It stays fast for element access and is lazy for expensive operations such as repeated concatenation.

`Maybe` is a low-allocation alternative to `Option`. On Scala 3 it is an opaque type, so a present value is stored as it is rather than inside a wrapper object.

`Combinators` provides compile-time typeclasses for composing and decomposing tuples, `Either` types and union types, which is useful for code that must work with many shapes of the same operation.

## Where to Start

Start with [Chunk](../chunk.md) for sequences. Use [Maybe](../maybe.md) where allocation matters and the code is on Scala 3, and [Combinators](../combinators.md) when writing generic code over tuples or eithers.
