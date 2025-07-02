package zio.blocks.schema

final case class Namespace(packages: Seq[String], values: Seq[String]) {
  val elements: Seq[String] = packages ++ values
}

object Namespace {
  private[schema] val javaLang: Namespace = new Namespace("java" :: "lang" :: Nil, Nil)
  private[schema] val javaTime: Namespace = new Namespace("java" :: "time" :: Nil, Nil)
  private[schema] val javaUtil: Namespace = new Namespace("java" :: "util" :: Nil, Nil)
  private[schema] val scala: Namespace    = new Namespace("scala" :: Nil, Nil)
  private[schema] val scalaCollectionImmutable: Namespace =
    new Namespace("scala" :: "collection" :: "immutable" :: Nil, Nil)
}
