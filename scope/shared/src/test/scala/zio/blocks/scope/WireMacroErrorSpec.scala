package zio.blocks.scope

import zio.test._

/**
 * Tests that Scope macros produce correct compile-time error messages.
 *
 * Uses ZIO Test's typeCheck macro to verify that:
 *   1. Invalid usages fail to compile
 *   2. Error messages contain expected content (type names, hints, etc.)
 *
 * Note: Error message formatting (colors, box-drawing) varies between Scala
 * versions and terminals. These tests focus on semantic content.
 */
object WireMacroErrorSpec extends ZIOSpecDefault {

  // Test fixtures - traits and classes for triggering errors
  trait DatabaseTrait {
    def query(): String
  }

  abstract class AbstractService

  // For subtype conflict testing
  trait Animal
  trait Dog extends Animal

  def spec = suite("Wire macro errors")(
    suite("NotAClass errors")(
      test("shared[T] for trait fails with helpful message") {
        typeCheck("""
          import zio.blocks.scope._
          trait MyTrait
          shared[MyTrait]
        """).map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.contains("Scope Error") && msg.contains("not a class"))
          )
        }
      },
      test("unique[T] for trait fails with helpful message") {
        typeCheck("""
          import zio.blocks.scope._
          trait MyTrait
          unique[MyTrait]
        """).map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.contains("Scope Error") && msg.contains("not a class"))
          )
        }
      },
      test("error mentions providing Wireable as alternative") {
        typeCheck("""
          import zio.blocks.scope._
          trait MyTrait
          shared[MyTrait]
        """).map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.contains("Wireable"))
          )
        }
      },
      test("error for abstract class shows correct message") {
        typeCheck("""
          import zio.blocks.scope._
          abstract class MyAbstract
          shared[MyAbstract]
        """).map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.contains("Scope Error") && msg.contains("not a class"))
          )
        }
      }
    ),
    suite("SubtypeConflict errors")(
      test("subtype conflict is detected for class with related type params") {
        typeCheck("""
          import zio.blocks.scope._
          import java.io.{InputStream, FileInputStream}

          class BadService(input: InputStream, file: FileInputStream)
          shared[BadService]
        """).map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg =>
              msg.contains("subtype") ||
                msg.contains("Dependency type conflict") ||
                msg.contains("FileInputStream") ||
                msg.contains("InputStream")
            )
          )
        }
      },
      test("subtype conflict error suggests wrapping types") {
        typeCheck("""
          import zio.blocks.scope._
          import java.io.{InputStream, FileInputStream}

          class BadService(input: InputStream, file: FileInputStream)
          shared[BadService]
        """).map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg =>
              msg.contains("wrap") ||
                msg.contains("wrapper") ||
                msg.contains("distinct") ||
                msg.contains("subtype")
            )
          )
        }
      }
    ),

    suite("error message format")(
      test("error contains Scope Error header") {
        typeCheck("""
          import zio.blocks.scope._
          trait SomeTrait
          shared[SomeTrait]
        """).map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.contains("Scope Error"))
          )
        }
      },
      test("error contains type name in message") {
        typeCheck("""
          import zio.blocks.scope._
          trait SpecificTraitName
          shared[SpecificTraitName]
        """).map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.contains("SpecificTraitName"))
          )
        }
      },
      test("error has box-drawing characters for visual formatting") {
        typeCheck("""
          import zio.blocks.scope._
          trait FormattedError
          shared[FormattedError]
        """).map { result =>
          assertTrue(
            result.isLeft,
            result.left.exists(msg => msg.contains("──") && msg.contains("Scope Error"))
          )
        }
      }
    )
  )
}
