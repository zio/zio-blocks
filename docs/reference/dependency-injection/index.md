---
id: index
title: "Dependency Injection"
description: "Introduction to the Dependency Injection blocks of ZIO Blocks: Wire for constructing a service and its dependencies, and Context for type-indexed collections of values."
---

## Introduction

A service usually needs other services to work, and something has to decide how they are built and handed over. The blocks in this group describe that wiring with types, so that a missing dependency, or one of the wrong type, is a compile error rather than a failure at startup.

## The Blocks

The group has two blocks, which work at different points in an application's life.

| Block                                          | Artifact               | What It Is For                                                                    |
| ---------------------------------------------- | ---------------------- | --------------------------------------------------------------------------------- |
| [Wire](../resource-management/wire.md)         | `zio-blocks-scope`     | Compile-time safe recipes for constructing a service and its dependencies         |
| [Context](../context.md)                       | `zio-blocks-context`   | Type-indexed heterogeneous collections                                            |

## How the Blocks Differ

`Wire` is a recipe: it describes how to construct a service and what that service needs, and the compiler validates the whole dependency graph. It ships in the same artifact as the resource-management types, because a wire builds services that may hold resources, and the [Resource Management](../resource-management/index.md) page shows how wires, resources and scopes fit together.

`Context` is a container: an immutable collection that stores values of different types, indexed by their types. A phantom type parameter tracks which types are present, so looking up a value that is not there does not compile.

## Where to Start

Start with [Wire](../resource-management/wire.md) to assemble an application from its parts, and read [Context](../context.md) when a program needs to carry a set of services around by type.
