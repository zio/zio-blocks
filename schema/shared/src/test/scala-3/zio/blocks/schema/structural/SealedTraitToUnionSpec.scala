package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for sealed trait to structural union type conversion (Scala 3 only).
 *
 * Per issue #517: Sealed traits convert to union types with Tag discriminators.
 * Example: sealed trait Result { Success, Failure } → Schema[{ type Tag =
 * "Success"; def value: Int } | { type Tag = "Failure"; def error: String }]
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
    suite("Structural Conversion")(
      test("sealed trait converts to structural union") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural
        assertTrue(structural != null)
      },
      test("structural sealed trait schema is a Variant") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural
        val isVariant  = (structural.reflect: @unchecked) match {
          case _: Reflect.Variant[_, _] => true
          case _                        => false
        }
        assertTrue(isVariant)
      },
      test("structural sealed trait preserves case count") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural
        val caseCount  = (structural.reflect: @unchecked) match {
          case v: Reflect.Variant[_, _] => v.cases.size
          case _                        => -1
        }
        assertTrue(caseCount == 2)
      },
      test("structural sealed trait has exact union type name") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural
        val typeName   = structural.reflect.typeName.name
        assertTrue(typeName == """{Tag:"Success",value:Int}|{Tag:"Failure",error:String}""")
      },
      test("sealed trait with case objects converts to structural") {
        val schema     = Schema.derived[Status]
        val structural = schema.structural
        val caseCount  = (structural.reflect: @unchecked) match {
          case v: Reflect.Variant[_, _] => v.cases.size
          case _                        => -1
        }
        assertTrue(caseCount == 2)
      },
      test("three variant sealed trait structural preserves all cases") {
        val schema     = Schema.derived[Animal]
        val structural = schema.structural
        val caseNames  = (structural.reflect: @unchecked) match {
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
    suite("Structural Schema Behavior")(
      test("structural sealed trait encodes via DynamicValue") {
        val schema        = Schema.derived[Result]
        val structural    = schema.structural
        val value: Result = Result.Success(42)

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(value)

        assertTrue(dynamic match {
          case DynamicValue.Variant("Success", DynamicValue.Record(fields)) =>
            val fieldMap = fields.toMap
            fieldMap.get("value").contains(DynamicValue.Primitive(PrimitiveValue.Int(42)))
          case _ => false
        })
      },
      test("structural sealed trait decodes from DynamicValue") {
        val schema     = Schema.derived[Result]
        val structural = schema.structural

        val dynamic = DynamicValue.Variant(
          "Success",
          DynamicValue.Record(Vector("value" -> DynamicValue.Primitive(PrimitiveValue.Int(100))))
        )

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val result        = structuralAny.fromDynamicValue(dynamic)

        assertTrue(result match {
          case Right(recovered) =>
            val r = recovered.asInstanceOf[Result]
            r == Result.Success(100)
          case _ => false
        })
      },
      test("structural sealed trait round-trips through DynamicValue") {
        val schema        = Schema.derived[Result]
        val structural    = schema.structural
        val value: Result = Result.Failure("error message")

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(value)
        val result        = structuralAny.fromDynamicValue(dynamic)

        assertTrue(result match {
          case Right(recovered) =>
            val r = recovered.asInstanceOf[Result]
            r == value
          case _ => false
        })
      },
      test("structural case object round-trips correctly") {
        val schema        = Schema.derived[Status]
        val structural    = schema.structural
        val value: Status = Status.Active

        val structuralAny = structural.asInstanceOf[Schema[Any]]
        val dynamic       = structuralAny.toDynamicValue(value)
        val result        = structuralAny.fromDynamicValue(dynamic)

        assertTrue(result match {
          case Right(recovered) =>
            val s = recovered.asInstanceOf[Status]
            s == value
          case _ => false
        })
      },
      test("all structural animal variants round-trip correctly") {
        val schema     = Schema.derived[Animal]
        val structural = schema.structural

        val dog: Animal  = Animal.Dog("Rex", "German Shepherd")
        val cat: Animal  = Animal.Cat("Whiskers", true)
        val bird: Animal = Animal.Bird("Tweety", true)

        val structuralAny = structural.asInstanceOf[Schema[Any]]

        val dogResult  = structuralAny.fromDynamicValue(structuralAny.toDynamicValue(dog)).map(_.asInstanceOf[Animal])
        val catResult  = structuralAny.fromDynamicValue(structuralAny.toDynamicValue(cat)).map(_.asInstanceOf[Animal])
        val birdResult = structuralAny.fromDynamicValue(structuralAny.toDynamicValue(bird)).map(_.asInstanceOf[Animal])

        assertTrue(
          dogResult == Right(dog),
          catResult == Right(cat),
          birdResult == Right(bird)
        )
      },
      test("structural animal variants preserve field information") {
        val schema     = Schema.derived[Animal]
        val structural = schema.structural

        val dogFields = (structural.reflect: @unchecked) match {
          case v: Reflect.Variant[_, _] =>
            v.cases.find(_.name == "Dog").flatMap { c =>
              (c.value: @unchecked) match {
                case r: Reflect.Record[_, _] => Some(r.fields.map(_.name).toSet)
                case _                       => None
              }
            }
          case _ => None
        }
        assertTrue(
          dogFields.isDefined,
          dogFields.get.contains("name"),
          dogFields.get.contains("breed")
        )
      }
    )
  )
}
