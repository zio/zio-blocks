package zio.blocks.schema.msgpack

import zio.blocks.schema.codec.BinaryFormat

object MessagePackFormat extends BinaryFormat("application/msgpack", MessagePackBinaryCodecDeriver)
