package zio.blocks.schema.json

class JsonReaderException private[json] (msg: String, cause: Throwable)
    extends RuntimeException(msg, cause, true, false)
