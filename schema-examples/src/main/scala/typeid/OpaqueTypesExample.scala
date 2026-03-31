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

package typeid

import zio.blocks.typeid._
import util.ShowExpr.show

/**
 * Opaque Types Example
 *
 * Demonstrates how TypeId preserves the semantic distinction of opaque types,
 * enabling runtime type safety that pure Scala reflection cannot provide.
 *
 * Opaque types allow you to create distinct types that share the same
 * representation at runtime. TypeId captures this distinction, making it
 * possible to:
 *   - Distinguish between different opaque types wrapping the same base type
 *   - Build type-indexed registries that respect opaque type boundaries
 *   - Implement runtime validators specific to each opaque type
 *
 * Run with: sbt "schema-examples/runMain typeid.OpaqueTypesExample"
 */

object OpaqueTypesExample extends App {

  println("═══════════════════════════════════════════════════════════════")
  println("Opaque Types with TypeId")
  println("═══════════════════════════════════════════════════════════════\n")

  // Define domain-specific opaque types wrapping String
  // In a real application, these would enforce different validation rules
  opaque type UserId       = String
  opaque type Email        = String
  opaque type SessionToken = String

  println("--- Opaque Type Definitions ---\n")
  println("opaque type UserId = String")
  println("opaque type Email = String")
  println("opaque type SessionToken = String\n")

  // Derive TypeIds for each opaque type
  val userIdType       = TypeId.of[UserId]
  val emailType        = TypeId.of[Email]
  val sessionTokenType = TypeId.of[SessionToken]
  val stringType       = TypeId.string

  println("--- TypeId Derivation ---\n")

  println("TypeId.of[UserId].name")
  show(userIdType.name)

  println("TypeId.of[Email].name")
  show(emailType.name)

  println("TypeId.of[SessionToken].name")
  show(sessionTokenType.name)

  println("\n--- Opaque Types vs Base Type ---\n")

  // Key insight: opaque types are distinct from their representation type
  println("TypeId.of[UserId].isEquivalentTo(TypeId.string)")
  show(userIdType.isEquivalentTo(stringType))

  println("TypeId.of[Email].isEquivalentTo(TypeId.string)")
  show(emailType.isEquivalentTo(stringType))

  println("TypeId.of[SessionToken].isEquivalentTo(TypeId.string)")
  show(sessionTokenType.isEquivalentTo(stringType))

  println("\n--- Opaque Types are Distinct from Each Other ---\n")

  println("TypeId.of[UserId].isEquivalentTo(TypeId.of[Email])")
  show(userIdType.isEquivalentTo(emailType))

  println("TypeId.of[Email].isEquivalentTo(TypeId.of[SessionToken])")
  show(emailType.isEquivalentTo(sessionTokenType))

  println("TypeId.of[UserId].isEquivalentTo(TypeId.of[SessionToken])")
  show(userIdType.isEquivalentTo(sessionTokenType))

  println("\n--- Real-World Use Case: Type-Safe Registry ---\n")

  // Define validators for each opaque type
  trait Validator {
    def validate(value: String): Boolean
    def errorMessage: String
  }

  val userIdValidator = new Validator {
    def validate(value: String): Boolean = value.nonEmpty && value.forall(_.isDigit)
    def errorMessage                     = "UserId must be non-empty digits"
  }

  val emailValidator = new Validator {
    def validate(value: String): Boolean = value.contains("@") && value.contains(".")
    def errorMessage                     = "Email must contain @ and ."
  }

  val sessionTokenValidator = new Validator {
    def validate(value: String): Boolean = value.length >= 32
    def errorMessage                     = "SessionToken must be at least 32 characters"
  }

  // Build a type-indexed registry of validators
  // This demonstrates the power of TypeId: we can safely dispatch
  // to different validators based on opaque type identity
  val validatorRegistry: Map[TypeId.Erased, Validator] = Map(
    TypeId.of[UserId].erased       -> userIdValidator,
    TypeId.of[Email].erased        -> emailValidator,
    TypeId.of[SessionToken].erased -> sessionTokenValidator
  )

  println("Built validator registry keyed by opaque type")
  println("Validators can enforce different validation rules per type\n")

  // Demonstrate validation dispatch
  def validateString(value: String, typeId: TypeId[_]): Boolean =
    validatorRegistry
      .get(typeId.erased)
      .map(_.validate(value))
      .getOrElse {
        println(s"No validator found for type: ${typeId.fullName}")
        false
      }

  def getValidationError(typeId: TypeId[_]): String =
    validatorRegistry
      .get(typeId.erased)
      .map(_.errorMessage)
      .getOrElse("Unknown validator")

  println("--- Validation Examples ---\n")

  val testUserId = "12345"
  val testEmail  = "user@example.com"
  val testToken  = "a" * 32

  println(s"Validating UserId: '$testUserId'")
  if (validateString(testUserId, TypeId.of[UserId])) {
    println("✓ Valid UserId\n")
  } else {
    println(s"✗ Invalid: ${getValidationError(TypeId.of[UserId])}\n")
  }

  println(s"Validating Email: '$testEmail'")
  if (validateString(testEmail, TypeId.of[Email])) {
    println("✓ Valid Email\n")
  } else {
    println(s"✗ Invalid: ${getValidationError(TypeId.of[Email])}\n")
  }

  println(s"Validating SessionToken: '$testToken'")
  if (validateString(testToken, TypeId.of[SessionToken])) {
    println("✓ Valid SessionToken\n")
  } else {
    println(s"✗ Invalid: ${getValidationError(TypeId.of[SessionToken])}\n")
  }

  println("--- What Pure Scala Cannot Do ---\n")

  println("With pure Scala reflection:")
  println("- classOf[UserId] == classOf[String]  (erased at runtime)")
  println("- classOf[Email] == classOf[String]   (erased at runtime)")
  println("- You cannot distinguish opaque types from their base type\n")

  println("With TypeId:")
  println("- TypeId.of[UserId] != TypeId.of[String]  (preserved)")
  println("- TypeId.of[Email] != TypeId.of[String]   (preserved)")
  println("- You can build type-safe validators and registries\n")

  println("═══════════════════════════════════════════════════════════════")
}
