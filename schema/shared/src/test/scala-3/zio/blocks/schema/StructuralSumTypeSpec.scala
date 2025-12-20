package zio.blocks.schema

import zio.test._
import zio.blocks.schema.binding.StructuralValue

object StructuralSumTypeSpec extends ZIOSpecDefault {

  sealed trait Status
  case class Active(since: Int)       extends Status
  case class Inactive(reason: String) extends Status

  def spec = suite("StructuralSumTypeSpec")(
    test("derive structural schema for sum type") {
      // Direct derivation without derived instance logic (manual summon)
      val ts = ToStructural.derived[Status]

      val active = Active(2023)
      val s1     = ts.toStructural(active)
      // s1 should be backed by StructuralValue
      val since = s1.asInstanceOf[StructuralValue].selectDynamic("since")

      val inactive = Inactive("bored")
      val s2       = ts.toStructural(inactive)
      val reason   = s2.asInstanceOf[StructuralValue].selectDynamic("reason")

      assertTrue(since == 2023) && assertTrue(reason == "bored")
    }
  )
}
