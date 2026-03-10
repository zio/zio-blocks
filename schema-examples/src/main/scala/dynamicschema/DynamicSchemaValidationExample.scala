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

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import util.ShowExpr.show

/**
 * DynamicSchema Reference — Validation
 *
 * Demonstrates check and conforms: validating DynamicValue instances against a
 * DynamicSchema derived from a typed Schema.
 *
 * Run with: sbt "schema-examples/runMain
 * dynamicschema.DynamicSchemaValidationExample"
 */
object DynamicSchemaValidationExample extends App {

  case class Address(street: String, city: String)
  case class Person(name: String, age: Int, address: Address)

  object Address { implicit val schema: Schema[Address] = Schema.derived[Address] }
  object Person  { implicit val schema: Schema[Person] = Schema.derived[Person]   }

  val dynSchema: DynamicSchema = Schema[Person].toDynamicSchema

  // ── A fully conforming value ──────────────────────────────────────────────

  val valid = DynamicValue.Record(
    Chunk(
      "name"    -> DynamicValue.string("Alice"),
      "age"     -> DynamicValue.int(30),
      "address" -> DynamicValue.Record(
        Chunk(
          "street" -> DynamicValue.string("1 Main St"),
          "city"   -> DynamicValue.string("Springfield")
        )
      )
    )
  )

  println("=== Valid value ===")
  show(dynSchema.check(valid))    // None — no error
  show(dynSchema.conforms(valid)) // true

  // ── Missing field ─────────────────────────────────────────────────────────

  val missingAge = DynamicValue.Record(
    Chunk(
      "name" -> DynamicValue.string("Bob"),
      // "age" is absent
      "address" -> DynamicValue.Record(
        Chunk(
          "street" -> DynamicValue.string("2 Oak Ave"),
          "city"   -> DynamicValue.string("Shelbyville")
        )
      )
    )
  )

  println("\n=== Missing field ===")
  show(dynSchema.check(missingAge))    // Some(SchemaError) — missing field "age"
  show(dynSchema.conforms(missingAge)) // false

  // ── Wrong primitive type ─────────────────────────────────────────────────

  val wrongType = DynamicValue.Record(
    Chunk(
      "name"    -> DynamicValue.string("Carol"),
      "age"     -> DynamicValue.string("not-an-int"), // should be Int
      "address" -> DynamicValue.Record(
        Chunk(
          "street" -> DynamicValue.string("3 Elm St"),
          "city"   -> DynamicValue.string("Capital City")
        )
      )
    )
  )

  println("\n=== Wrong primitive type ===")
  show(dynSchema.check(wrongType))    // Some(SchemaError) — type mismatch at "age"
  show(dynSchema.conforms(wrongType)) // false

  // ── Extra (unknown) field ────────────────────────────────────────────────

  val extraField = DynamicValue.Record(
    Chunk(
      "name"    -> DynamicValue.string("Dan"),
      "age"     -> DynamicValue.int(40),
      "address" -> DynamicValue.Record(
        Chunk(
          "street" -> DynamicValue.string("4 Pine Rd"),
          "city"   -> DynamicValue.string("Ogdenville")
        )
      ),
      "unknown" -> DynamicValue.string("extra") // not in schema
    )
  )

  println("\n=== Extra field ===")
  show(dynSchema.check(extraField))    // Some(SchemaError) — unknown field "unknown"
  show(dynSchema.conforms(extraField)) // false
}
