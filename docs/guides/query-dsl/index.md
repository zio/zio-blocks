---
id: index
title: "Query DSL"
description: "A type-safe query language built on reified optics, from expressions to a fluent SQL builder."
displayed_sidebar: guides
---

The Query DSL guides build one type-safe query language in four parts. You write a filter once as ordinary Scala, and the same expression can be evaluated in memory, turned into SQL, extended with new SQL features, and finally assembled into full statements.

Read the parts in order. Each one builds on the expressions and code from the one before.

1. [Expressions](../query-dsl-reified-optics.md) builds query expressions over your domain types with reified optics: comparisons, boolean logic, arithmetic and string operations. You evaluate them against in-memory data.
2. [SQL Generation](../query-dsl-sql.md) translates those expressions into `WHERE` clauses, `SELECT` statements and parameterized queries.
3. [Extending the Expression Language](../query-dsl-extending.md) adds SQL predicates the built-in language lacks, such as `IN`, `BETWEEN`, `IS NULL` and `LIKE`, plus aggregate functions and `CASE WHEN`.
4. [A Fluent SQL Builder](../query-dsl-fluent-builder.md) wraps everything in a builder for `SELECT`, `UPDATE`, `INSERT` and `DELETE` statements.

The reference pages for the optics and schema types the DSL uses are in the [reference](../../reference/index.md).
