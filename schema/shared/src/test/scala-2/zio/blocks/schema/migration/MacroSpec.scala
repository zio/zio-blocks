package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema.migration.ToDynamicOptic

object MacroSpec extends ZIOSpecDefault {
  case class User(name: String)

  def spec = suite("Scala 2.13 Macro Verification")(
    test("Macro should extract path correctly") {
      // ফিক্স: ToDynamicOptic.derive কল করা হয়েছে
      val opticProvider = ToDynamicOptic.derive[User, String](_.name)
      val optic = opticProvider.apply()
      
      // optic ব্যবহার করার ফলে Unused ওয়ার্নিং আসবে না
      assertTrue(optic.nodes.nonEmpty)
    }
  )
}