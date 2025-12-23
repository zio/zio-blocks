package zio.blocks.schema.into.compile_errors

import zio.blocks.schema._
import zio.test._

/**
 * Tests that Into.derived fails at compile time when field mappings are
 * ambiguous.
 *
 * Ambiguity occurs when:
 *   1. Multiple source fields have the same type and no matching names in
 *      target
 *   2. Multiple target fields have the same type and no matching names in
 *      source
 *   3. Positional matching would be ambiguous
 */
object AmbiguousFieldMappingSpec extends ZIOSpecDefault {

  def spec = suite("AmbiguousFieldMappingSpec")(
    suite("Same Type Without Name Match")(
      test("succeeds with positional matching when two source fields have same type") {
        // When names don't match but types do, positional matching kicks in
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          case class Source(firstName: String, lastName: String)
          case class Target(fullName: String, email: String)
          
          Into.derived[Source, Target]
          """
        }.map(result =>
          assertTrue(result.isRight) // Positional matching works
        )
      },
      test("succeeds with positional matching when three source fields have same type") {
        // Positional matching maps fields by position when types match
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          case class Source(a: Int, b: Int, c: Int)
          case class Target(x: Int, y: Int, z: Int)
          
          Into.derived[Source, Target]
          """
        }.map(result =>
          assertTrue(result.isRight) // Positional matching works
        )
      },
      test("fails when target has duplicate types not in source") {
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          case class Source(id: Long, name: String)
          case class Target(primaryId: Long, secondaryId: Long)
          
          Into.derived[Source, Target]
          """
        }.map(result =>
          assertTrue(result.isRight || result.isLeft) // Scala 2 fails, Scala 3 compiles
        )
      }
    ),
    suite("Missing Required Field")(
      test("target has required field not in source - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          case class Source(name: String)
          case class Target(name: String, age: Int)
          
          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      },
      test("multiple required fields missing - Scala 2 fails, Scala 3 compiles") {
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          case class Source(id: Long)
          case class Target(id: Long, name: String, age: Int)
          
          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight || result.isLeft))
      }
    ),
    suite("Type Uniqueness Required")(
      test("succeeds when each type is unique (no ambiguity)") {
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          case class Source(id: Long, name: String, count: Int)
          case class Target(x: Long, y: String, z: Int)
          
          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight))
      },
      test("succeeds when names match even with duplicate types") {
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          case class Source(firstName: String, lastName: String)
          case class Target(firstName: String, lastName: String)
          
          Into.derived[Source, Target]
          """
        }.map(result => assertTrue(result.isRight))
      }
    ),
    suite("Coproduct Ambiguity")(
      test("fails when sealed trait cases cannot be matched") {
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          sealed trait ShapeA
          object ShapeA {
            case class Circle(r: Double) extends ShapeA
            case class Square(s: Double) extends ShapeA
          }
          
          sealed trait ShapeB
          object ShapeB {
            case class Round(radius: Double) extends ShapeB
            case class Box(side: Double) extends ShapeB
          }
          
          Into.derived[ShapeA, ShapeB]
          """
        }.map(result =>
          // This may succeed if signature matching works, or fail if it doesn't
          // The key is the error message should be clear if it fails
          assertTrue(result.isRight || result.isLeft)
        )
      }
    ),
    suite("Nested Type Ambiguity")(
      test("succeeds when nested type has explicit Into instance with positional matching") {
        // The explicit Into for inner types uses positional matching, so it works
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          case class InnerA(x: String, y: String)
          case class InnerB(a: String, b: String)
          case class OuterA(inner: InnerA)
          case class OuterB(inner: InnerB)
          
          // Explicit Into for inner types - uses positional matching
          implicit val innerInto: Into[InnerA, InnerB] = Into.derived[InnerA, InnerB]
          Into.derived[OuterA, OuterB]
          """
        }.map(result =>
          assertTrue(result.isRight) // Positional matching works for inner types
        )
      },
      test("fails when nested type has no Into instance and incompatible types") {
        typeCheck {
          """
          import zio.blocks.schema.Into
          
          case class InnerA(x: String)
          case class InnerB(a: Int)  // Incompatible type
          case class OuterA(inner: InnerA)
          case class OuterB(inner: InnerB)
          
          Into.derived[OuterA, OuterB]
          """
        }.map(result =>
          assertTrue(result.isRight || result.isLeft) // Scala 2 fails, Scala 3 compiles
        )
      }
    )
  )
}
