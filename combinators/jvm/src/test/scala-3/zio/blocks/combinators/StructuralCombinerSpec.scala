package zio.blocks.combinators

import zio.test._
import scala.compiletime.testing.typeCheckErrors

object StructuralCombinerSpec extends ZIOSpecDefault {

  def spec = suite("StructuralCombiner")(
    suite("basic combining functionality")(
      test("combines two objects and returns intersection type") {
        type A = { def foo: String }
        type B = { def bar: Int }

        val a: A = new { def foo = "test" }
        val b: B = new { def bar = 123 }

        val combined: A & B = StructuralCombiner.combine(a, b)

        assertTrue(combined != null)
      },
      test("combines objects with multiple fields") {
        type Person  = { def name: String; def age: Int }
        type Address = { def street: String; def city: String }

        val person: Person = new {
          def name = "Bob"
          def age  = 30
        }
        val address: Address = new {
          def street = "123 Main St"
          def city   = "Springfield"
        }

        val combined: Person & Address = StructuralCombiner.combine(person, address)

        assertTrue(combined != null)
      }
    ),
    suite("negative compile tests - conflicting fields")(
      test("conflicting fields with same name and type should not compile") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.StructuralCombiner
          type A = { def foo: Int }
          type B = { def foo: Int }
          val a: A = new { def foo = 1 }
          val b: B = new { def foo = 2 }
          val x: A & B = StructuralCombiner.combine(a, b)
        """)
        assertTrue(errors.nonEmpty)
      },
      test("conflicting fields with different types should not compile") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.StructuralCombiner
          type A = { def bar: Int }
          type B = { def bar: String }
          val a: A = new { def bar = 1 }
          val b: B = new { def bar = "x" }
          val x: A & B = StructuralCombiner.combine(a, b)
        """)
        assertTrue(errors.nonEmpty)
      },
      test("multiple conflicting members should be reported") {
        val errors = typeCheckErrors("""
          import zio.blocks.combinators.StructuralCombiner
          type A = { def foo: Int; def bar: String }
          type B = { def foo: Boolean; def bar: String; def baz: Double }
          val a: A = new { def foo = 1; def bar = "a" }
          val b: B = new { def foo = true; def bar = "b"; def baz = 3.14 }
          val x: A & B = StructuralCombiner.combine(a, b)
        """)
        assertTrue(errors.nonEmpty)
      }
    ),
    suite("edge cases")(
      test("combining empty structural types") {
        type Empty1 = {}
        type Empty2 = {}

        val a: Empty1 = new {}
        val b: Empty2 = new {}

        val combined: Empty1 & Empty2 = StructuralCombiner.combine(a, b)

        assertTrue(combined != null)
      },
      test("type correctness for intersection") {
        type A = { def x: Int }
        type B = { def y: String }

        val a: A = new { def x = 1 }
        val b: B = new { def y = "test" }

        val combined: A & B = StructuralCombiner.combine(a, b)

        assertTrue(combined != null)
      }
    )
  )
}
