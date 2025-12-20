package zio.blocks.schema

import zio.test._
import zio.blocks.schema.binding.StructuralValue

object StructuralSumTypeSpec extends ZIOSpecDefault {

  sealed trait Status
  case class Active(since: Int)       extends Status
  case class Inactive(reason: String) extends Status

  sealed trait Toggle
  case object On  extends Toggle
  case object Off extends Toggle

  enum Color {
    case Red, Green, Blue
  }

  enum Shape {
    case Circle(radius: Double)
    case Rectangle(width: Double, height: Double)
  }

  def spec = suite("StructuralSumTypeSpec")(
    test("derive structural schema for sum type") {
      // Direct derivation without derived instance logic (manual summon)
      val ts = ToStructural.derived[Status]

      val active = Active(2023)
      val s1     = ts.toStructural(active)
      // s1 should be backed by StructuralValue
      val since = s1.asInstanceOf[StructuralValue].selectDynamic("since")

      val inactive = Inactive("bored")
      val s2       = ts.toStructural(inactive)
      val reason   = s2.asInstanceOf[StructuralValue].selectDynamic("reason")

      assertTrue(since == 2023) && assertTrue(reason == "bored")
    },
    test("nested sum types in structural conversion") {
      sealed trait Inner
      case class InnerA(x: Int)    extends Inner
      case class InnerB(y: String) extends Inner

      case class Outer(label: String, inner: Inner)

      val ts = ToStructural.derived[Outer]

      val outer1 = Outer("first", InnerA(42))
      val outer2 = Outer("second", InnerB("hello"))

      val s1 = ts.toStructural(outer1)
      val s2 = ts.toStructural(outer2)

      val sv1      = s1.asInstanceOf[StructuralValue]
      val sv2      = s2.asInstanceOf[StructuralValue]
      val innerSv1 = sv1.selectDynamic("inner").asInstanceOf[StructuralValue]
      val innerSv2 = sv2.selectDynamic("inner").asInstanceOf[StructuralValue]

      assertTrue(sv1.selectDynamic("label") == "first") &&
      assertTrue(sv2.selectDynamic("label") == "second") &&
      assertTrue(innerSv1.selectDynamic("x") == 42) &&
      assertTrue(innerSv2.selectDynamic("y") == "hello")
    },
    test("sealed trait with case objects converts to structural") {
      val ts = ToStructural.derived[Toggle]

      val on  = ts.toStructural(On)
      val off = ts.toStructural(Off)

      // Case objects should produce empty StructuralValues
      val svOn  = on.asInstanceOf[StructuralValue]
      val svOff = off.asInstanceOf[StructuralValue]

      // They should be valid StructuralValue instances
      assertTrue(svOn != null) && assertTrue(svOff != null)
    },
    test("enum with simple cases converts to structural") {
      val ts = ToStructural.derived[Color]

      val red   = ts.toStructural(Color.Red)
      val green = ts.toStructural(Color.Green)
      val blue  = ts.toStructural(Color.Blue)

      // Simple enum cases should produce StructuralValues
      assertTrue(red.asInstanceOf[StructuralValue] != null) &&
      assertTrue(green.asInstanceOf[StructuralValue] != null) &&
      assertTrue(blue.asInstanceOf[StructuralValue] != null)
    },
    test("enum with parameterized cases converts to structural") {
      val ts = ToStructural.derived[Shape]

      val circle = Shape.Circle(5.0)
      val rect   = Shape.Rectangle(3.0, 4.0)

      val sCircle = ts.toStructural(circle)
      val sRect   = ts.toStructural(rect)

      val svCircle = sCircle.asInstanceOf[StructuralValue]
      val svRect   = sRect.asInstanceOf[StructuralValue]

      assertTrue(svCircle.selectDynamic("radius") == 5.0) &&
      assertTrue(svRect.selectDynamic("width") == 3.0) &&
      assertTrue(svRect.selectDynamic("height") == 4.0)
    }
  )
}
