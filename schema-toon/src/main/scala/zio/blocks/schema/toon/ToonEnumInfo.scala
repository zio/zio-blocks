package zio.blocks.schema.toon

import zio.blocks.schema.binding.{Constructor, Discriminator}

import scala.annotation.tailrec

private[toon] sealed trait ToonEnumInfo

private[toon] final class ToonEnumLeafInfo(val name: String, val constructor: Constructor[?]) extends ToonEnumInfo

private[toon] final class ToonEnumNodeInfo(discr: Discriminator[?], children: Array[ToonEnumInfo])
    extends ToonEnumInfo {
  @tailrec
  def discriminate(x: Any): String = children(discr.asInstanceOf[Discriminator[Any]].discriminate(x)) match {
    case leaf: ToonEnumLeafInfo => leaf.name
    case node: ToonEnumNodeInfo => node.discriminate(x)
  }
}
