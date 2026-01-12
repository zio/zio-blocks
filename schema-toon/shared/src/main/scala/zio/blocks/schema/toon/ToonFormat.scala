package zio.blocks.schema.toon

import zio.blocks.schema.codec.BinaryFormat

/**
 * The TOON format for ZIO Schema 2.
 *
 * TOON (Token-Oriented Object Notation) is a compact serialization format
 * optimized for LLM token efficiency, achieving 30-60% reduction vs JSON.
 */
object ToonFormat extends BinaryFormat("application/toon", ToonBinaryCodecDeriver)
