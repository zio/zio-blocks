package zio.blocks.schema

final case class Namespace(packages: Seq[String], values: Seq[String]) {
  val elements: Seq[String] = packages ++ values
}
