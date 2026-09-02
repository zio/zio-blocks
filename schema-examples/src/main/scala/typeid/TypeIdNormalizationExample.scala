package typeid

import zio.blocks.typeid._
import zio.sbt.ExprEval.show

/**
 * TypeId Normalization Example
 *
 * Demonstrates type alias handling, normalization, and type-indexed registries
 * using erased TypeIds.
 *
 * Run with: sbt "schema-examples/runMain typeid.TypeIdNormalizationExample"
 */

object TypeIdNormalizationExample extends App {

  println("═══════════════════════════════════════════════════════════════")
  println("TypeId Normalization Example")
  println("═══════════════════════════════════════════════════════════════\n")

  // Define some type aliases
  type UserId = Long
  type Email  = String
  type Age    = Int

  println("--- Type Aliases ---\n")

  // Create TypeIds for the aliases
  val userIdAlias = TypeId.alias[UserId](
    name = "UserId",
    owner = Owner.Root,
    typeParams = Nil,
    aliased = TypeRepr.Ref(TypeId.long)
  )

  show(userIdAlias.name)

  show(userIdAlias.fullName)

  println("\n--- Normalization ---\n")

  // Normalize the alias to its underlying type
  val normalized = TypeId.normalize(userIdAlias)

  show(normalized.name)

  show(normalized.fullName)

  println("\n--- Equality with Normalization ---\n")

  // Structural equality (not considering aliases)
  val anotherUserIdAlias = TypeId.alias[UserId](
    name = "UserId",
    owner = Owner.Root,
    typeParams = Nil,
    aliased = TypeRepr.Ref(TypeId.long)
  )

  show(userIdAlias == anotherUserIdAlias)

  show(TypeId.normalize(userIdAlias) == TypeId.long)

  println("\n--- Erased TypeIds for Registries ---\n")

  // Erased TypeIds are useful for type-indexed maps
  val intErased: TypeId.Erased     = TypeId.int.erased
  val stringErased: TypeId.Erased  = TypeId.string.erased
  val listIntErased: TypeId.Erased = TypeId.of[List[Int]].erased

  show(intErased)

  show(stringErased)

  show(listIntErased)

  println("\n--- Type Registry Using Erased TypeIds ---\n")

  // Build a type registry
  val registry: Map[TypeId.Erased, String] = Map(
    TypeId.int.erased           -> "Integer type",
    TypeId.string.erased        -> "String type",
    TypeId.long.erased          -> "Long type",
    TypeId.of[List[Int]].erased -> "List of integers"
  )

  show(registry.get(TypeId.int.erased))

  show(registry.get(TypeId.string.erased))

  show(registry.get(TypeId.of[List[Int]].erased))

  println("\n--- Querying the Registry ---\n")

  // Look up types in the registry
  val intType    = TypeId.of[Int].erased
  val doubleType = TypeId.of[Double].erased

  show(registry.get(intType))

  show(registry.get(doubleType))

  println("\n═══════════════════════════════════════════════════════════════")
}
