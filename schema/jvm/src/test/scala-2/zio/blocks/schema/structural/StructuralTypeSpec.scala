package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Scala 2 pure structural type derivation (JVM only).
 */
object StructuralTypeSpec extends ZIOSpecDefault {

  type PersonLike = { def name: String; def age: Int }
  type PointLike  = { def x: Int; def y: Int }

  def spec = suite("StructuralTypeSpec")(
    test("structural type round-trips through DynamicValue") {
      val schema  = Schema.derived[PersonLike]
      val dynamic = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        )
      )
      val result = schema.fromDynamicValue(dynamic)
      result match {
        case Right(person) =>
          val backToDynamic = schema.toDynamicValue(person)
          backToDynamic match {
            case rec: DynamicValue.Record =>
              val expected = dynamic.asInstanceOf[DynamicValue.Record]
              assertTrue(
                rec.fields.toSet == expected.fields.toSet,
                person.name == "Alice",
                person.age == 30
              )
            case _ => assertTrue(false) ?? "Expected DynamicValue.Record"
          }
        case Left(err) =>
          assertTrue(false) ?? s"fromDynamicValue failed: $err"
      }
    },
    test("structural type with primitives round-trips") {
      val schema  = Schema.derived[PointLike]
      val dynamic = DynamicValue.Record(
        Vector(
          "x" -> DynamicValue.Primitive(PrimitiveValue.Int(100)),
          "y" -> DynamicValue.Primitive(PrimitiveValue.Int(200))
        )
      )
      val result = schema.fromDynamicValue(dynamic)
      result match {
        case Right(point) =>
          val backToDynamic = schema.toDynamicValue(point)
          backToDynamic match {
            case rec: DynamicValue.Record =>
              val expected = dynamic.asInstanceOf[DynamicValue.Record]
              assertTrue(
                rec.fields.toSet == expected.fields.toSet,
                point.x == 100,
                point.y == 200
              )
            case _ => assertTrue(false) ?? "Expected DynamicValue.Record"
          }
        case Left(err) =>
          assertTrue(false) ?? s"fromDynamicValue failed: $err"
      }
    }
  )
}
