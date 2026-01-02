package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for type name normalization in structural types.
 */
object TypeNameNormalizationSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  case class User(age: Int, name: String)

  type PersonLike = { def name: String; def age: Int }

  def spec = suite("TypeNameNormalizationSpec")(
    test("type names are alphabetically sorted") {
      type Unsorted = { def z: Int; def a: String; def m: Boolean }
      val schema = Schema.derived[Unsorted]

      val typeName = schema.reflect.typeName.name
      val aIdx     = typeName.indexOf("a:")
      val mIdx     = typeName.indexOf("m:")
      val zIdx     = typeName.indexOf("z:")

      assertTrue(
        aIdx < mIdx,
        mIdx < zIdx
      )
    },
    test("same structure produces same type name") {
      type Version1 = { def name: String; def age: Int }
      type Version2 = { def age: Int; def name: String }

      val schema1 = Schema.derived[Version1]
      val schema2 = Schema.derived[Version2]

      assertTrue(
        schema1.reflect.typeName.name == schema2.reflect.typeName.name
      )
    },
    test("type name format is ordered alphabetically") {
      val schema   = Schema.derived[PersonLike]
      val typeName = schema.reflect.typeName.name

      assertTrue(typeName == "{age:Int,name:String}")
    },
    test("nominal and direct structural produce same type name") {
      val nominalSchema    = Schema.derived[Person]
      val structuralSchema = nominalSchema.structural
      val directSchema     = Schema.derived[PersonLike]

      assertTrue(
        structuralSchema.reflect.typeName.name == directSchema.reflect.typeName.name
      )
    },
    test("different field order case classes produce same structural type name") {
      val personSchema = Schema.derived[Person]
      val userSchema   = Schema.derived[User]

      val personStructural = personSchema.structural
      val userStructural   = userSchema.structural

      assertTrue(
        personStructural.reflect.typeName.name == userStructural.reflect.typeName.name
      )
    },
    test("type name has no whitespace") {
      val schema   = Schema.derived[PersonLike]
      val typeName = schema.reflect.typeName.name

      assertTrue(!typeName.contains(" "))
    },
    test("type name uses simple type names for primitives") {
      val schema   = Schema.derived[PersonLike]
      val typeName = schema.reflect.typeName.name

      assertTrue(
        typeName.contains("Int"),
        !typeName.contains("scala.Int")
      )
    }
  )
}
