package golem.runtime.rpc

import zio.blocks.schema.Schema

final case class Point(x: Int, y: Int)
object Point { implicit val schema: Schema[Point] = Schema.derived }

final case class Labels(values: Map[String, Int])
object Labels { implicit val schema: Schema[Labels] = Schema.derived }

sealed trait Color
object Color {
  case object Red  extends Color
  case object Blue extends Color
  implicit val schema: Schema[Color] = Schema.derived
}
