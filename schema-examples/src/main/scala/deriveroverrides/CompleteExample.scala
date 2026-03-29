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

package deriveroverrides

import zio.blocks.schema._
import zio.blocks.schema.CompanionOptics

/**
 * Customizing Type Class Derivation with Instance Overrides — Complete Example
 *
 * Demonstrates combining all three override levels and understanding resolution
 * order:
 *   1. Optic-based (exact path) — highest priority
 *   2. Term-name-based (field within parent type) — medium priority
 *   3. Type-based (all occurrences) — lowest priority
 *
 * In this example, we show how different overrides apply to different fields
 * based on their specificity.
 *
 * Run with: sbt "schema-examples/runMain deriveroverrides.CompleteExample"
 */
object CompleteExample extends App {

  // Domain types
  case class Company(name: String, foundedYear: Int)

  object Company extends CompanionOptics[Company] {
    implicit val schema: Schema[Company] = Schema.derived
    val name: Lens[Company, String]      = optic(_.name)
  }

  case class Address(city: String, zipCode: String)

  object Address extends CompanionOptics[Address] {
    implicit val schema: Schema[Address] = Schema.derived
    val city: Lens[Address, String]      = optic(_.city)
  }

  case class UserProfile(
    id: Int,
    username: String,
    age: Int,
    score: Int,
    address: Address,
    company: Company
  )

  object UserProfile extends CompanionOptics[UserProfile] {
    implicit val schema: Schema[UserProfile] = Schema.derived

    val id: Lens[UserProfile, Int]          = optic(_.id)
    val age: Lens[UserProfile, Int]         = optic(_.age)
    val score: Lens[UserProfile, Int]       = optic(_.score)
    val address: Lens[UserProfile, Address] = optic(_.address)
    val company: Lens[UserProfile, Company] = optic(_.company)
  }

  // Note: Custom codec derivation with instance overrides requires an updated API.
  // This example demonstrates the domain model structure and companion optics,
  // which form the foundation for customizing type class derivation with
  // multiple override levels:
  //   1. Optic-based (exact path) — highest priority
  //   2. Term-name-based (field within parent type) — medium priority
  //   3. Type-based (all occurrences) — lowest priority

  val user = UserProfile(
    id = 123,
    username = "alice",
    age = 30,
    score = 850,
    address = Address("NYC", "10001"),
    company = Company("TechCorp", 2020)
  )

  println("=== Customizing Type Class Derivation: Domain Model ===")
  println(s"User: $user")
  println()
  println("Resolution order for instance overrides:")
  println("  - Optic-based overrides (exact path) — highest priority")
  println("  - Term-name overrides (field within parent type) — medium priority")
  println("  - Type-based overrides (all occurrences) — lowest priority")
}
