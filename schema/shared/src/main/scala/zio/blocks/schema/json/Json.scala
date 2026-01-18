package zio.blocks.schema.json

import zio.blocks.Chunk

sealed trait Json
object Json {
  final case class Obj(fields: Chunk[(String, Json)]) extends Json
  final case class Arr(elements: Chunk[Json])         extends Json
  final case class Str(value: String)                 extends Json
  final case class Num(value: String)                 extends Json
  final case class Bool(value: Boolean)               extends Json
  case object Null                                    extends Json
}