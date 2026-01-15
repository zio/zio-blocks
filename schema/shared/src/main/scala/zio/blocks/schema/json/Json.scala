package zio.blocks.schema.json

import scala.collection.immutable.{Map => ScalaMap, Vector}

/**
 * Simple JSON ADT for representing JSON values
 */
sealed trait Json

object Json {
  final case class Object(fields: ScalaMap[Predef.String, Json]) extends Json

  final case class Array(elements: Vector[Json]) extends Json

  final case class String(value: Predef.String) extends Json

  final case class Number(value: BigDecimal) extends Json

  final case class Boolean(value: scala.Boolean) extends Json

  case object Null extends Json
}
