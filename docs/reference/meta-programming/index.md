---
id: index
title: "Meta Programming"
description: "Introduction to the Meta Programming blocks of ZIO Blocks: Schema, which describes data types as values, and TypeId, which keeps type identity at runtime."
---

## Introduction

Scala erases most type information at runtime, so a `List[Int]` and a `List[String]` look the same to the JVM. The blocks in this group turn type information into ordinary values that a program can inspect, store and act on. This lets a library derive serializers, validators and accessors from the shape of a type instead of requiring them to be written by hand.

## The Blocks

The group has two blocks, and [Schema](../schema/index.md) is the one most programs start with.

| Block                          | Artifact              | What It Is For                                                                           |
| ------------------------------ | --------------------- | ---------------------------------------------------------------------------------------- |
| [Schema](../schema/index.md)   | `zio-blocks-schema`   | Type-safe schemas with automatic codec, optic, and validator derivation                  |
| [TypeId](../typeid.md)         | `zio-blocks-typeid`   | Compile-time type identity with rich metadata                                            |

## How the Blocks Differ

`Schema` describes the structure of a data type, and one schema drives JSON support, validation and optics. Further formats such as Avro, BSON, CSV, MessagePack, Thrift, TOON, XML and YAML are separate modules listed on the [Schema](../schema/index.md) page.

`TypeId` answers a narrower question: which type is this? It captures names, type parameters, parent types and annotations as data, which suits registries, code generation and dispatching on a type that would otherwise be erased.

## Where to Start

Start with [Schema](../schema/index.md) to define a data type once and derive codecs from it. Reach for [TypeId](../typeid.md) when a program needs a type's identity as a value rather than its structure. The [overview](../../index.md) compares every block in one table.
