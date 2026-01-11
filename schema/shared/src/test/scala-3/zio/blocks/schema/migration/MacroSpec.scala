package zio.blocks.schema.migration

import zio.test.*
import zio.blocks.schema.*
import zio.blocks.schema.migration.macros.AccessorMacros

object MacroSpec extends ZIOSpecDefault {

  // টেস্ট করার জন্য একটি ডামি ডেটা স্ট্রাকচার
  case class Address(street: String, zip: Int)
  case class User(name: String, address: Address)

  def spec = suite("Scala 3 Macro Verification")(
    
    test("Macro should extract a simple field path") {
      // AccessorMacros.derive ল্যাম্বডা থেকে ToDynamicOptic তৈরি করবে
      val opticProvider = AccessorMacros.derive[User, String](_.name)
      val optic = opticProvider.apply()

      val expectedNodes = Vector(DynamicOptic.Node.Field("name"))
      assertTrue(optic.nodes == expectedNodes)
    },

    test("Macro should extract a nested field path") {
      val opticProvider = AccessorMacros.derive[User, String](_.address.street)
      val optic = opticProvider.apply()

      val expectedNodes = Vector(
        DynamicOptic.Node.Field("address"),
        DynamicOptic.Node.Field("street")
      )
      assertTrue(optic.nodes == expectedNodes)
    },

    test("Macro should correctly represent paths as string via DynamicOptic") {
      val opticProvider = AccessorMacros.derive[User, Int](_.address.zip)
      val optic = opticProvider.apply()

      // DynamicOptic-এর toString ভেরিফাই করা (যা আপনি আগে ইমপ্লিমেন্ট করেছিলেন)
      assertTrue(optic.toString == ".address.zip")
    }
  )
}