/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dynamicschema

import zio.blocks.schema._
import util.ShowExpr.show

/**
 * DynamicSchema Reference — Serialization
 *
 * Demonstrates converting a DynamicSchema to a DynamicValue for storage or
 * transmission, and reconstructing it on the other side.
 *
 * Run with: sbt "schema-examples/runMain
 * dynamicschema.DynamicSchemaSerializationExample"
 */
object DynamicSchemaSerializationExample extends App {

  case class Product(id: Long, name: String, price: Double)
  object Product { implicit val schema: Schema[Product] = Schema.derived[Product] }

  // ── Producer side ─────────────────────────────────────────────────────────
  // Strip runtime bindings and convert to a storable DynamicValue blob.
  // The blob contains only field names, type names, and annotations — no closures.

  val original: DynamicSchema = Schema[Product].toDynamicSchema
  val blob: DynamicValue      = DynamicSchema.toDynamicValue(original)

  println("=== Serialized schema ===")
  show(blob.valueType)       // "Record" — the DynamicValue encoding of the schema
  show(original.typeId.name) // "Product"

  // ── Consumer side ─────────────────────────────────────────────────────────
  // Restore the schema from the stored blob.

  val restored: DynamicSchema = DynamicSchema.fromDynamicValue(blob)

  println("\n=== Restored schema ===")
  show(restored.typeId.name)                    // "Product" — type identity is preserved
  show(restored.reflect.getClass.getSimpleName) // reflects the Reflect.Record shape

  // ── Verify structural equivalence ─────────────────────────────────────────
  // Re-serialize and compare the blobs to show the round-trip is lossless.

  val blob2: DynamicValue = DynamicSchema.toDynamicValue(restored)
  show(blob == blob2) // true — round-trip is lossless
}
