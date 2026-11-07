---
id: index
title: "Tooling & Codegen"
description: "Introduction to the Tooling & Codegen blocks of ZIO Blocks: Code Generation, Docs (Markdown) and Smithy."
---

## Introduction

The blocks in this group are for programs that read or write text formats and source code instead of serving users: generators, documentation tools and API model processors.

## The Blocks

The group has three blocks, each aimed at a different format.

| Block                                  | Artifact               | What It Is For                                                                  |
| -------------------------------------- | ---------------------- | ------------------------------------------------------------------------------- |
| [Code Generation](../codegen/index.md) | `zio-blocks-codegen`   | Generic Scala code generation IR and emitter                                    |
| [Docs](../docs.md)                     | `zio-blocks-markdown`  | GitHub Flavored Markdown parsing, rendering, and programmatic construction      |
| [Smithy](../smithy.md)                 | `zio-blocks-smithy`    | Smithy IDL parser and AST library for API modeling                              |

## How the Blocks Differ

`Code Generation` provides an intermediate representation of Scala code, such as files, case classes, sealed traits and methods, and a pure emitter that turns it into formatted Scala source.

`Docs` is the Markdown library: an immutable model of a document, a strict parser, renderers for Markdown, HTML and the terminal, and a string interpolator validated at compile time. Its artifact is `zio-blocks-markdown`.

`Smithy` parses Smithy 2.0 text into a data structure, lets a program query the shapes in a model, and prints a model back as valid Smithy.

## Where to Start

Start with [Code Generation](../codegen/index.md) to emit Scala source from a model, and [Smithy](../smithy.md) when the input is an API model. Read [Docs](../docs.md) to parse or build Markdown documents.
