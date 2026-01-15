package zio.blocks.schema.toon.codec

import zio.blocks.schema.Reflect
import zio.blocks.schema.toon.ToonBinaryCodec

/**
 * Trait that provides codec derivation capability. This abstraction allows
 * passing the derive function in a way that's compatible with both Scala 2 and
 * Scala 3.
 */
private[toon] trait CodecDeriver {
  def derive[F[_, _], A](reflect: Reflect[F, A]): ToonBinaryCodec[A]
}
