package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/** Tests for type name normalization in structural types. */
object TypeNameNormalizationSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  case class User(age: Int, name: String)
  type PersonLike = { def name: String; def age: Int }

  def spec: Spec[Any, Nothing] = suite("TypeNameNormalizationSpec")(
    test("type names are alphabetically sorted") {
      type Unsorted = { def z: Int; def a: String; def m: Boolean }
      val schema   = Schema.derived[Unsorted]
      val typeName = schema.reflect.typeName.name
      val aIdx     = typeName.indexOf("a:")
      val mIdx     = typeName.indexOf("m:")
      val zIdx     = typeName.indexOf("z:")
      assertTrue(aIdx < mIdx, mIdx < zIdx)
    },
    test("same structure produces same type name regardless of field order") {
      type Version1 = { def name: String; def age: Int }
      type Version2 = { def age: Int; def name: String }
      val schema1 = Schema.derived[Version1]
      val schema2 = Schema.derived[Version2]
      assertTrue(schema1.reflect.typeName.name == schema2.reflect.typeName.name)
    },
    test("nominal and direct structural produce same type name") {
      val nominalSchema    = Schema.derived[Person]
      val structuralSchema = nominalSchema.structural
      val directSchema     = Schema.derived[PersonLike]
      assertTrue(structuralSchema.reflect.typeName.name == directSchema.reflect.typeName.name)
    }
  )
}
