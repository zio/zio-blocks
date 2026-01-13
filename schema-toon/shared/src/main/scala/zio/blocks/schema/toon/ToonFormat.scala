package zio.blocks.schema.toon

import zio.blocks.schema.codec.BinaryFormat

/**
 * Binary format definition for TOON (Token-Oriented Object Notation).
 *
 * TOON is a compact, human-readable serialization format optimized for LLM
 * prompts. It achieves 30-60% token reduction compared to JSON while preserving
 * the same data model.
 *
 * == Usage ==
 *
 * {{{
 * import zio.blocks.schema._
 * import zio.blocks.schema.toon._
 *
 * case class User(id: Int, name: String)
 * object User {
 *   implicit val schema: Schema[User] = DeriveSchema.gen[User]
 * }
 *
 * // Get codec for a type with Schema
 * val codec = ToonFormat.deriver.derive[User]
 *
 * // Or use extension method
 * val toon = ToonFormat.codec[User]
 * }}}
 *
 * @see
 *   [[https://github.com/toon-format/spec TOON Specification]]
 * @see
 *   [[ToonBinaryCodecDeriver]] for derivation configuration options
 */
object ToonFormat extends BinaryFormat[ToonBinaryCodec]("text/toon", ToonBinaryCodecDeriver)
