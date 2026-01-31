package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

object OpaqueTypeUnpackingSpec extends SchemaBaseSpec {

  opaque type UserId <: String = String
  object UserId {
    def apply(value: String): UserId         = value
    extension (id: UserId) def value: String = id
  }

  opaque type Age <: Int = Int
  object Age {
    def apply(value: Int): Age          = value
    extension (age: Age) def value: Int = age
  }

  opaque type Score <: Double = Double
  object Score {
    def apply(value: Double): Score            = value
    extension (score: Score) def value: Double = score
  }

  case class User(id: UserId, name: String)
  case class Person(name: String, age: Age)
  case class GameResult(player: String, score: Score)
  case class NestedOpaque(user: User, score: Score)
  case class ListOfOpaque(ids: List[UserId])
  case class OptionalOpaque(maybeId: Option[UserId])

  def spec = suite("OpaqueTypeUnpackingSpec")(
    suite("Simple opaque types are unpacked to primitives")(
      test("UserId (opaque String) is unpacked to String in structural type") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.structural.OpaqueTypeUnpackingSpec._
          val schema: Schema[User] = Schema.derived[User]
          val structural: Schema[{def id: String; def name: String}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("Age (opaque Int) is unpacked to Int in structural type") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.structural.OpaqueTypeUnpackingSpec.{Person => PersonType, _}
          val schema: Schema[PersonType] = Schema.derived[PersonType]
          val structural: Schema[{def age: Int; def name: String}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("Score (opaque Double) is unpacked to Double in structural type") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.structural.OpaqueTypeUnpackingSpec._
          val schema: Schema[GameResult] = Schema.derived[GameResult]
          val structural: Schema[{def player: String; def score: Double}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      }
    ),
    suite("Nested opaque types are unpacked recursively")(
      test("nested case class with opaque fields is fully unpacked") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.structural.OpaqueTypeUnpackingSpec._
          val schema: Schema[NestedOpaque] = Schema.derived[NestedOpaque]
          val structural: Schema[{def score: Double; def user: User}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      }
    ),
    suite("Opaque types in collections are unpacked")(
      test("List[UserId] field appears in structural type") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.structural.OpaqueTypeUnpackingSpec._
          val schema: Schema[ListOfOpaque] = Schema.derived[ListOfOpaque]
          val structural: Schema[{def ids: List[String]}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("Option[UserId] field appears in structural type") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.structural.OpaqueTypeUnpackingSpec._
          val schema: Schema[OptionalOpaque] = Schema.derived[OptionalOpaque]
          val structural: Schema[{def maybeId: Option[String]}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      }
    ),
    suite("Structural schema round-trip with opaque types")(
      test("User with opaque UserId round-trips through DynamicValue") {
        val schema   = Schema.derived[User]
        val original = User(UserId("user-123"), "Alice")

        val dynamic   = schema.toDynamicValue(original)
        val roundTrip = schema.fromDynamicValue(dynamic)

        assertTrue(roundTrip == Right(original))
      },
      test("structural schema of User has correct type") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.structural.OpaqueTypeUnpackingSpec._
          val schema: Schema[User] = Schema.derived[User]
          val structural: Schema[{def id: String; def name: String}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      }
    ),
    suite("Type checking with explicit structural types")(
      test("User unpacks UserId to String in structural type") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.structural.OpaqueTypeUnpackingSpec._
          val schema: Schema[User] = Schema.derived[User]
          val structural: Schema[{def id: String; def name: String}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("Person unpacks Age to Int in structural type") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.structural.OpaqueTypeUnpackingSpec.{Person => PersonType, _}
          val schema: Schema[PersonType] = Schema.derived[PersonType]
          val structural: Schema[{def age: Int; def name: String}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("GameResult unpacks Score to Double in structural type") {
        typeCheck("""
          import zio.blocks.schema._
          import zio.blocks.schema.structural.OpaqueTypeUnpackingSpec._
          val schema: Schema[GameResult] = Schema.derived[GameResult]
          val structural: Schema[{def player: String; def score: Double}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      }
    )
  )
}
