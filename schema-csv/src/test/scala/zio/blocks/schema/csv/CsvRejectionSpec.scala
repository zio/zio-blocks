package zio.blocks.schema.csv

import zio.blocks.schema._
import zio.test._

object CsvRejectionSpec extends SchemaBaseSpec {

  sealed trait Shape
  case class Circle(radius: Double)                   extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape

  object Shape {
    implicit val schema: Schema[Shape] = Schema.derived
  }

  def spec = suite("CsvRejectionSpec")(
    test("variant types throw UnsupportedOperationException") {
      val result =
        try {
          Schema[Shape].derive(CsvFormat)
          false
        } catch {
          case e: UnsupportedOperationException =>
            e.getMessage.contains("variant/sum types")
        }
      assertTrue(result)
    },
    test("sequence fields throw UnsupportedOperationException") {
      val result =
        try {
          Schema[List[Int]].derive(CsvFormat)
          false
        } catch {
          case e: UnsupportedOperationException =>
            e.getMessage.contains("sequence")
        }
      assertTrue(result)
    },
    test("map fields throw UnsupportedOperationException") {
      val result =
        try {
          Schema[Map[String, Int]].derive(CsvFormat)
          false
        } catch {
          case e: UnsupportedOperationException =>
            e.getMessage.contains("map")
        }
      assertTrue(result)
    }
  )
}
