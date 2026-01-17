package zio.blocks.schema.msgpack

import zio.blocks.schema.DynamicOptic

final class MessagePackCodecError(
  var spans: List[DynamicOptic.Node],
  message: String
) extends RuntimeException(message)
