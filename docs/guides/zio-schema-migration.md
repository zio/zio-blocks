# ZIO Schema Migration Guide

This guide explains how to use the Schema Migration System in ZIO Schema 2.

## Overview

The migration system allows you to define transformations between different versions of your schemas safely and efficiently. It provides utilities for structural migrations, upcasting, downcasting, and automated derivations.

## Usage

```scala
import zio.schema._
import zio.schema.migration._

// Define your schemas
val v1Schema = DeriveSchema.gen[Version1]
val v2Schema = DeriveSchema.gen[Version2]

// Create a migration
val migration = Migration.derive(v1Schema, v2Schema)
```

For more advanced usage and structural transformation details, please refer to the core reference.
