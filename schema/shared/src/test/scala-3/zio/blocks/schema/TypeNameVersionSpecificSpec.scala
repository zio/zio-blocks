package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object TypeNameVersionSpecificSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("TypeNameVersionSpecificSpec")(
    suite("TypeName.derived Scala 3 specific")(
      test("derives TypeName for union type") {
        val typeName = TypeName.derived[Int | String]
        assert(typeName.name)(equalTo("|")) &&
        // Union types are sorted by fullName for consistent ordering across macro contexts
        assert(typeName.params.map(_.name))(equalTo(Seq("String", "Int")))
      },
      test("derives TypeName for complex union type") {
        val typeName = TypeName.derived[Int | String | Boolean]
        assert(typeName.name)(equalTo("|")) &&
        // Union types are sorted by fullName for consistent ordering across macro contexts
        assert(typeName.params.map(_.name))(equalTo(Seq("String", "Boolean", "Int")))
      },
      test("derives TypeName for enum value") {
        enum Color { case Red, Green, Blue }
        val typeName = TypeName.derived[Color.Red.type]
        assert(typeName.name)(equalTo("Red"))
      },
      test("derives TypeName for enum type") {
        enum Status { case Active, Inactive }
        val typeName = TypeName.derived[Status]
        assert(typeName.name)(equalTo("Status"))
      },
      test("derives TypeName for parametric enum") {
        enum Container[+A] {
          case Empty
          case Full(value: A)
        }
        val typeName = TypeName.derived[Container[Int]]
        assert(typeName.name)(equalTo("Container")) &&
        assert(typeName.params.map(_.name))(equalTo(Seq("Int")))
      },
      test("derives TypeName for generic tuple (*:)") {
        val typeName = TypeName.derived[Int *: String *: EmptyTuple]
        assert(typeName.name)(equalTo("*:")) &&
        assert(typeName.params.head.name)(equalTo("Int")) &&
        assert(typeName.params(1).name)(equalTo("String"))
      },
      test("derives TypeName for EmptyTuple") {
        val typeName = TypeName.derived[EmptyTuple]
        assert(typeName.name)(equalTo("EmptyTuple"))
      },
      test("derives TypeName for larger generic tuple") {
        val typeName = TypeName.derived[Int *: Long *: String *: Boolean *: EmptyTuple]
        assert(typeName.name)(equalTo("*:")) &&
        assert(typeName.params.head.name)(equalTo("Int"))
      },
      test("derives TypeName for nested union in Option") {
        val typeName = TypeName.derived[Option[Int | String]]
        assert(typeName.name)(equalTo("Option")) &&
        assert(typeName.params.head.name)(equalTo("|"))
      },

      test("derives TypeName for nested class inside object inside class") {
        val typeName = TypeName.derived[OuterForTypeName.Middle.Deepest]
        assert(typeName.name)(equalTo("Deepest")) &&
        assert(typeName.namespace.values)(equalTo(Seq("TypeNameVersionSpecificSpec", "OuterForTypeName", "Middle")))
      },
      test("derives TypeName for Module types (object types)") {
        val typeName = TypeName.derived[ModuleTypeTest.type]
        assert(typeName.name)(equalTo("ModuleTypeTest"))
      },
      test("handles recursive type references gracefully") {
        val typeName = TypeName.derived[RecursiveType]
        assert(typeName.name)(equalTo("RecursiveType"))
      }
    )
  )

  object OuterForTypeName {
    object Middle {
      case class Deepest(x: Int)
    }
  }

  object ModuleTypeTest

  case class RecursiveType(value: Int, next: Option[RecursiveType])
}
