package zio.blocks.schema.json

import scala.collection.immutable.{Map => ScalaMap, Vector}

/**
 * A simple JSON Abstract Data Type (ADT) for representing JSON values.
 *
 * This ADT provides a basic representation of JSON structures and is used as an
 * intermediate format in Schema-based JSON codec derivation. It does not
 * include advanced features like navigation, patching, merging, or parsing from
 * string format—these are considered part of a larger Json system.
 *
 * The primary purpose of this ADT is to serve as a bridge between Scala values
 * (via Schema and DynamicValue) and JSON representations.
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
