package zio.blocks.schema.msgpack

import zio.blocks.schema.DynamicOptic

final case class MessagePackCodecError(spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false)
