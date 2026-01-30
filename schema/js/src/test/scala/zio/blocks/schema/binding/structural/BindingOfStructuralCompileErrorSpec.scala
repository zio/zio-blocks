package zio.blocks.schema.binding.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests that Binding.of for structural types fails at compile time on Scala.js.
 *
 * Structural types require reflection APIs that are only available on the JVM.
 */
object BindingOfStructuralCompileErrorSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("BindingOfStructuralCompileErrorSpec")(
    suite("Binding.of - Compile Error on JS for Structural Types")(
      test("simple structural type fails to compile") {
        typeCheck {
          """
          import zio.blocks.schema.binding.Binding

          type Person = { def name: String; def age: Int }
          Binding.of[Person]
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            result.swap.exists(msg =>
              msg.toLowerCase.contains("jvm") ||
                msg.toLowerCase.contains("reflection") ||
                msg.toLowerCase.contains("structural")
            )
          )
        }
      },
      test("single field structural type fails to compile") {
        typeCheck {
          """
          import zio.blocks.schema.binding.Binding

          Binding.of[{ def value: Int }]
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            result.swap.exists(msg =>
              msg.toLowerCase.contains("jvm") ||
                msg.toLowerCase.contains("reflection") ||
                msg.toLowerCase.contains("structural")
            )
          )
        }
      }
    )
  )
}
