package zio.blocks.template

object FlawTest10 {
  import zio.blocks.template._
  val condition = true
  val d         = button.when(condition)(disabled)
}
