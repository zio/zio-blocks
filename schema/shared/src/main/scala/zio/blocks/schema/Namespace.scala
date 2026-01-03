package zio.blocks.schema

final case class Namespace(packages: Seq[String], values: Seq[String] = Nil) {
  val elements: Seq[String] = packages ++ values
}

object Namespace {
  private[schema] val empty: Namespace                    = new Namespace(Nil)
  private[schema] val javaTime: Namespace                 = new Namespace("java" :: "time" :: Nil)
  private[schema] val javaUtil: Namespace                 = new Namespace("java" :: "util" :: Nil)
  private[schema] val scala: Namespace                    = new Namespace("scala" :: Nil)
  private[schema] val scalaCollectionImmutable: Namespace = new Namespace("scala" :: "collection" :: "immutable" :: Nil)
  private[schema] val zioBlocksSchema: Namespace          = new Namespace("zio" :: "blocks" :: "schema" :: Nil)
}
