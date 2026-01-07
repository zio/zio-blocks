package golem.runtime

import zio.blocks.schema.Schema

final case class Sum(a: Int, b: Int)
object Sum { implicit val schema: Schema[Sum] = Schema.derived }
