package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for sealed trait schema derivation and structural conversion (Scala 3
 * only).
 *
 * In Scala 3, sealed traits can be converted to structural types using union
 * types.
 */
object SealedTraitToUnionSpec extends ZIOSpecDefault {

  sealed trait Result
  object Result {
    case class Success(value: Int)    extends Result
    case class Failure(error: String) extends Result
  }

  sealed trait Status
  object Status {
    case object Active   extends Status
    case object Inactive extends Status
  }

  sealed trait Animal
  object Animal {
    case class Dog(name: String, breed: String)    extends Animal
    case class Cat(name: String, indoor: Boolean)  extends Animal
    case class Bird(name: String, canFly: Boolean) extends Animal
  }

  def spec = suite("SealedTraitToUnionSpec")(
    suite("Schema Derivation")(
      test("sealed trait with case classes derives schema") {
        val schema = Schema.derived[Result]
        assertTrue(schema != null)
      },
      test("sealed trait with case objects derives schema") {
        val schema = Schema.derived[Status]
        assertTrue(schema != null)
      },
      test("sealed trait with multiple variants derives schema") {
        val schema = Schema.derived[Animal]
        assertTrue(schema != null)
      }
    ),
    suite("Schema Structure")(
      test("sealed trait schema is a Variant") {
        val schema    = Schema.derived[Result]
        val isVariant = schema.reflect match {
          case _: Reflect.Variant[_, _] => true
          case _                        => false
        }
        assertTrue(isVariant)
      },
      test("sealed trait has correct number of cases") {
        val schema    = Schema.derived[Result]
        val caseCount = schema.reflect match {
          case v: Reflect.Variant[_, _] => v.cases.size
          case _                        => -1
        }
        assertTrue(caseCount == 2)
      },
      test("sealed trait case names are correct") {
        val schema    = Schema.derived[Result]
        val caseNames = schema.reflect match {
          case v: Reflect.Variant[_, _] => v.cases.map(_.name).toSet
          case _                        => Set.empty[String]
        }
        assertTrue(
          caseNames.contains("Success"),
          caseNames.contains("Failure")
        )
      },
      test("three variant sealed trait has correct cases") {
        val schema    = Schema.derived[Animal]
        val caseNames = schema.reflect match {
          case v: Reflect.Variant[_, _] => v.cases.map(_.name).toSet
          case _                        => Set.empty[String]
        }
        assertTrue(
          caseNames.size == 3,
          caseNames.contains("Dog"),
          caseNames.contains("Cat"),
          caseNames.contains("Bird")
        )
      }
    ),
    suite("DynamicValue Round-Trip")(
      test("Success case round-trips correctly") {
        val schema        = Schema.derived[Result]
        val value: Result = Result.Success(42)

        val dynamic = schema.toDynamicValue(value)
        val result  = schema.fromDynamicValue(dynamic)

        assertTrue(result == Right(value))
      },
      test("Failure case round-trips correctly") {
        val schema        = Schema.derived[Result]
        val value: Result = Result.Failure("error message")

        val dynamic = schema.toDynamicValue(value)
        val result  = schema.fromDynamicValue(dynamic)

        assertTrue(result == Right(value))
      },
      test("case object round-trips correctly") {
        val schema        = Schema.derived[Status]
        val value: Status = Status.Active

        val dynamic = schema.toDynamicValue(value)
        val result  = schema.fromDynamicValue(dynamic)

        assertTrue(result == Right(value))
      },
      test("all Animal variants round-trip correctly") {
        val schema = Schema.derived[Animal]

        val dog: Animal  = Animal.Dog("Rex", "German Shepherd")
        val cat: Animal  = Animal.Cat("Whiskers", true)
        val bird: Animal = Animal.Bird("Tweety", true)

        val dogResult  = schema.fromDynamicValue(schema.toDynamicValue(dog))
        val catResult  = schema.fromDynamicValue(schema.toDynamicValue(cat))
        val birdResult = schema.fromDynamicValue(schema.toDynamicValue(bird))

        assertTrue(
          dogResult == Right(dog),
          catResult == Right(cat),
          birdResult == Right(bird)
        )
      }
    ),
    suite("DynamicValue Structure")(
      test("sealed trait produces Variant DynamicValue") {
        val schema        = Schema.derived[Result]
        val value: Result = Result.Success(42)

        val dynamic = schema.toDynamicValue(value)

        val isVariant = dynamic match {
          case DynamicValue.Variant(_, _) => true
          case _                          => false
        }
        assertTrue(isVariant)
      },
      test("Variant DynamicValue has correct case name") {
        val schema        = Schema.derived[Result]
        val value: Result = Result.Success(42)

        val dynamic = schema.toDynamicValue(value)

        val caseName = dynamic match {
          case DynamicValue.Variant(name, _) => Some(name)
          case _                             => None
        }
        assertTrue(caseName == Some("Success"))
      },
      test("Variant DynamicValue contains case data") {
        val schema        = Schema.derived[Result]
        val value: Result = Result.Success(42)

        val dynamic = schema.toDynamicValue(value)

        val hasCorrectData = dynamic match {
          case DynamicValue.Variant("Success", DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            fieldMap.get("value").contains(DynamicValue.Primitive(PrimitiveValue.Int(42)))
          case _ => false
        }
        assertTrue(hasCorrectData)
      }
    )
  )
}
