package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

object OpaqueTypeUnpackingSpec extends ZIOSpecDefault {

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
        val schema     = Schema.derived[User]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("id:String"),
          typeName.contains("name:String"),
          !typeName.contains("UserId")
        )
      },
      test("Age (opaque Int) is unpacked to Int in structural type") {
        val schema     = Schema.derived[Person]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("age:Int"),
          typeName.contains("name:String"),
          !typeName.contains("Age")
        )
      },
      test("Score (opaque Double) is unpacked to Double in structural type") {
        val schema     = Schema.derived[GameResult]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("score:Double"),
          typeName.contains("player:String"),
          !typeName.contains("Score")
        )
      }
    ),
    suite("Nested opaque types are unpacked recursively")(
      test("nested case class with opaque fields is fully unpacked") {
        val schema     = Schema.derived[NestedOpaque]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("score:Double"),
          typeName.contains("user:"),
          !typeName.contains("Score"),
          !typeName.contains("UserId")
        )
      }
    ),
    suite("Opaque types in collections are unpacked")(
      test("List[UserId] field appears in structural type") {
        val schema     = Schema.derived[ListOfOpaque]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("ids:List"),
          !typeName.contains("UserId")
        )
      },
      test("Option[UserId] field appears in structural type") {
        val schema     = Schema.derived[OptionalOpaque]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("maybeId:Option"),
          !typeName.contains("UserId")
        )
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
      test("structural schema of User has correct type name") {
        val schema     = Schema.derived[User]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("id:String"),
          typeName.contains("name:String")
        )
      }
    )
  )
}
