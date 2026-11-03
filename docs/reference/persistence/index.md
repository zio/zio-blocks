---
id: index
title: "Persistence"
description: "Introduction to the Persistence blocks of ZIO Blocks: the SQL module, its ZIO integration, data migration and event-sourced projections."
---

## Introduction

The blocks in this group store and evolve application data. They work from the data types the rest of the program already uses: the SQL module derives its codecs and repositories from a schema, and migrations are typed conversions between two such types. All four blocks require Scala 3.

## The Blocks

The group has four blocks.

| Block                                    | Artifact                      | What It Is For                                                                                    |
| ---------------------------------------- | ----------------------------- | ------------------------------------------------------------------------------------------------- |
| [SQL Module](../sql/index.md)            | `zio-blocks-sql`              | Type-safe JDBC wrapper with schema-derived codecs and a CRUD repository                           |
| [ZIO Integration](../sql-zio.md)         | `zio-blocks-sql-zio`          | ZIO integration with `ZIO.attemptBlocking` and `ZLayer`                                           |
| [Data Migration](../data-migration.md)   | `zio-blocks-data-migration`   | Typed, online database schema migrations in three execution models, with no hand-written SQL      |
| [Projection](../projection/index.md)     | `zio-blocks-projection`       | Event-sourced projections with per-entity SQLite storage                                          |

## How the Blocks Fit Together

The [SQL Module](../sql/index.md) is the foundation. Codecs map Scala types to database columns, fragments carry parameterized SQL, and repositories expose CRUD operations derived at compile time from a schema. [ZIO Integration](../sql-zio.md) is an adapter for that module: it lets a ZIO application use the same fragments, repositories and tables, and runs the JDBC calls off ZIO's main thread pool.

[Data Migration](../data-migration.md) builds on the SQL module's repositories and on [Migration](../schema/migration.md) for the typed row conversion, and it offers three execution models for evolving a schema online. [Projection](../projection/index.md) takes a different approach to storage: it turns an append-only event log into queryable read models that are described declaratively.

## Where to Start

Start with the [SQL Module](../sql/index.md). Add [ZIO Integration](../sql-zio.md) when the application uses ZIO, [Data Migration](../data-migration.md) when a schema has to change under load, and [Projection](../projection/index.md) when the source of truth is an event log.
