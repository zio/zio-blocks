package zio.blocks.schema

final case class Namespace(packages: Seq[String], values: Seq[String] = Nil) {
  val elements: Seq[String] = packages ++ values
}

object Namespace {
  val javaTime: Namespace                 = new Namespace("java" :: "time" :: Nil)
  val javaUtil: Namespace                 = new Namespace("java" :: "util" :: Nil)
  val scala: Namespace                    = new Namespace("scala" :: Nil)
  val scalaCollectionImmutable: Namespace = new Namespace("scala" :: "collection" :: "immutable" :: Nil)
  val zioBlocksSchema: Namespace          = new Namespace("zio" :: "blocks" :: "schema" :: Nil)
}
