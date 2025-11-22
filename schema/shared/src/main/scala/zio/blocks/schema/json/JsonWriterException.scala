package zio.blocks.schema.json

class JsonWriterException private[json] (msg: String, cause: Throwable)
    extends RuntimeException(msg, cause, true, false)
