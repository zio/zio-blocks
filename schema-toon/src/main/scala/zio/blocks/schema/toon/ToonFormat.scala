package zio.blocks.schema.toon

import zio.blocks.schema.codec.BinaryFormat

object ToonFormat extends BinaryFormat("text/toon", ToonBinaryCodecDeriver)
