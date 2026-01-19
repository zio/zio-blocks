package zio.blocks.schema.toon

import zio.blocks.schema.binding.Discriminator

import scala.annotation.tailrec

private[toon] sealed trait ToonCaseInfo

private[toon] final class ToonCaseLeafInfo(val name: String, val codec: ToonBinaryCodec[?]) extends ToonCaseInfo

private[toon] final class ToonCaseNodeInfo(discr: Discriminator[?], children: Array[ToonCaseInfo])
    extends ToonCaseInfo {
  @tailrec
  def discriminate(x: Any): ToonCaseLeafInfo = children(discr.asInstanceOf[Discriminator[Any]].discriminate(x)) match {
    case leaf: ToonCaseLeafInfo => leaf
    case node: ToonCaseNodeInfo => node.discriminate(x)
  }
}

private[toon] final class ToonDiscriminatorFieldInfo(val name: String, val value: String)
