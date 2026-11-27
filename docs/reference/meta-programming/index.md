---
id: index
title: "Schemas & Serialization"
description: "Introduction to the Schemas & Serialization blocks of ZIO Blocks: Schema, which describes data types as values, and the codecs that read and write them in further formats."
---

## Introduction

Scala erases most type information at runtime, so a `List[Int]` and a `List[String]` look the same to the JVM. The blocks in this group turn the structure of a data type into an ordinary value, a schema, that a program can inspect, store and act on. This lets a library derive serializers, validators and accessors from the shape of a type instead of requiring them to be written by hand.

## The Blocks

[Schema](../schema/index.md) is the block most programs start with. JSON support is built into it; each codec block adds one more format.

| Block                                                         | Artifact                        | What It Is For                                                          |
| ------------------------------------------------------------- | ------------------------------- | ----------------------------------------------------------------------- |
| [Schema](../schema/index.md)                                  | `zio-blocks-schema`             | Type-safe schemas with automatic codec, optic, and validator derivation |
| [Avro Codec](../schema/built-in-codecs/avro.md)               | `zio-blocks-schema-avro`        | Apache Avro binary serialization with automatic schema generation       |
| [BSON Codec](../schema/built-in-codecs/bson.md)               | `zio-blocks-schema-bson`        | MongoDB-compatible BSON serialization with native type support          |
| [CSV Codec](../schema/built-in-codecs/csv.md)                 | `zio-blocks-schema-csv`         | RFC 4180-compliant CSV serialization                                    |
| [MessagePack Codec](../schema/built-in-codecs/messagepack.md) | `zio-blocks-schema-messagepack` | Compact binary serialization with optimized streaming                   |
| [Thrift Codec](../schema/built-in-codecs/thrift.md)           | `zio-blocks-schema-thrift`      | Apache Thrift binary serialization with TBinaryProtocol                 |
| [TOON Codec](../schema/built-in-codecs/toon.md)               | `zio-blocks-schema-toon`        | Token-oriented notation 30–60% smaller than JSON, tuned for LLM prompts |
| [XML Codec](../schema/built-in-codecs/xml.md)                 | `zio-blocks-schema-xml`         | Zero-dependency XML serialization with fluent navigation and patching   |
| [YAML Codec](../schema/built-in-codecs/yaml.md)               | `zio-blocks-schema-yaml`        | Human-readable YAML serialization with JSON interop                     |

## How the Blocks Differ

`Schema` describes the structure of a data type, and one schema drives JSON support, validation and optics. The codecs are separate modules, each with its own artifact, and each reads and writes one format from the same schema, so adding a format does not mean writing a second description of the type. The [Schema](../schema/index.md) page documents them.

A program that needs a type's identity rather than its structure, for a registry or for dispatching on an erased type, uses [TypeId](../typeid.md), one of the [Types & Abstractions](../data-types/index.md) blocks.

## Where to Start

Start with [Schema](../schema/index.md) to define a data type once and derive codecs from it, then add the codec module for each format beyond JSON. The [overview](../../index.md) compares every block in one table.
