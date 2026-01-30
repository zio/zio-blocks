package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/**
 * Tests for sealed trait to structural union type conversion (Scala 3 only).
 *
 * Per issue #517: Sealed traits convert to union types with Tag discriminators.
 * Example: sealed trait Result { Success, Failure } → Schema[{ type Tag =
 * "Success"; def value: Int } | { type Tag = "Failure"; def error: String }]
 */
object SealedTraitToUnionSpec extends SchemaBaseSpec {

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
    test("sealed trait structural has exact union type name") {
      typeCheck("""
        import zio.blocks.schema._
        sealed trait Result
        object Result {
          case class Success(value: Int) extends Result
          case class Failure(error: String) extends Result
        }
        val schema = Schema.derived[Result]
        val structural: Schema[{def Failure: {def error: String}} | {def Success: {def value: Int}}] = schema.structural
      """).map(result => assertTrue(result.isRight))
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
    test("sealed trait with case objects has exact structural type name") {
      typeCheck("""
        import zio.blocks.schema._
        sealed trait Status
        object Status {
          case object Active extends Status
          case object Inactive extends Status
        }
        val schema = Schema.derived[Status]
        val structural: Schema[{def Active: {}} | {def Inactive: {}}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    },
    test("three variant sealed trait structural has all cases") {
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
    },
    test("structural sealed trait encodes anonymous instance via toDynamicValue") {
      val schema     = Schema.derived[Result]
      val structural = schema.structural

      val successInstance: { def Success: { def value: Int } } = new {
        def Success: { def value: Int } = new { def value: Int = 42 }
      }
      val dynamic = structural.toDynamicValue(successInstance)

      assertTrue(dynamic match {
        case DynamicValue.Variant("Success", DynamicValue.Record(fields)) =>
          fields.toMap.get("value").contains(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        case _ => false
      })
    },
    test("structural sealed trait decodes from DynamicValue") {
      val schema     = Schema.derived[Result]
      val structural = schema.structural

      val dynamic = DynamicValue.Variant(
        "Success",
        DynamicValue.Record("value" -> DynamicValue.Primitive(PrimitiveValue.Int(100)))
      )

      val result = structural.fromDynamicValue(dynamic)
      assertTrue(result.isRight)
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
    },
    test("sealed trait converts to expected structural union type") {
      typeCheck("""
        import zio.blocks.schema._
        sealed trait Result
        object Result {
          case class Success(value: Int) extends Result
          case class Failure(error: String) extends Result
        }
        val schema = Schema.derived[Result]
        val structural: Schema[{def Failure: {def error: String}} | {def Success: {def value: Int}}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    },
    test("sealed trait with case objects converts to expected structural union type") {
      typeCheck("""
        import zio.blocks.schema._
        sealed trait Status
        object Status {
          case object Active extends Status
          case object Inactive extends Status
        }
        val schema = Schema.derived[Status]
        val structural: Schema[{def Active: {}} | {def Inactive: {}}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    }
  )
}
