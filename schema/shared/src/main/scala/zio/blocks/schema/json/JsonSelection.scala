package zio.blocks.schema.json

import zio.blocks.schema.{DynamicOptic, SchemaError}

/**
 * A selection of JSON values, resulting from a query or navigation operation.
 *
 * A `JsonSelection` wraps an `Either[SchemaError, Vector[Json]]`. It supports
 * fluent navigation and transformation of the selected values.
 */
final case class JsonSelection(toEither: Either[SchemaError, Vector[Json]]) { self =>

  // ===========================================================================
  // Transformations
  // ===========================================================================

  /**
   * Maps over the selected JSON values.
   */
  def map(f: Json => Json): JsonSelection =
    JsonSelection(toEither.map(_.map(f)))

  /**
   * FlatMaps over the selected JSON values.
   */
  def flatMap(f: Json => JsonSelection): JsonSelection =
    JsonSelection(
      toEither.flatMap { jsons =>
        jsons.foldLeft[Either[SchemaError, Vector[Json]]](Right(Vector.empty)) { (acc, json) =>
          for {
            vec <- acc
            res <- f(json).toEither
          } yield vec ++ res
        }
      }
    )

  /**
   * Filters the selected JSON values.
   */
  def filter(p: Json => Boolean): JsonSelection =
    JsonSelection(toEither.map(_.filter(p)))

  /**
   * Collects values using a partial function.
   */
  def collect(pf: PartialFunction[Json, Json]): JsonSelection =
    JsonSelection(toEither.map(_.collect(pf)))

  // ===========================================================================
  // Navigation
  // ===========================================================================

  /**
   * Selects values at the given index in any array in the selection.
   */
  def apply(index: Int): JsonSelection =
    flatMap {
      case Json.Array(elems) if index >= 0 && index < elems.length =>
        JsonSelection(Right(Vector(elems(index))))
      case _ =>
        JsonSelection(Right(Vector.empty))
    }

  /**
   * Selects values with the given key in any object in the selection.
   */
  def apply(key: String): JsonSelection =
    flatMap {
      case Json.Object(fields) =>
        // Find all fields with the matching key (though standard JSON usually has unique keys)
        val matches = fields.collect { case (k, v) if k == key => v }
        JsonSelection(Right(matches))
      case _ =>
        JsonSelection(Right(Vector.empty))
    }

  /**
   * Navigates using a DynamicOptic path.
   */
  def get(path: DynamicOptic): JsonSelection = flatMap(_.get(path))

  // ===========================================================================
  // Type Filters
  // ===========================================================================

  def objects: JsonSelection = filter(_.isObject)
  def arrays: JsonSelection  = filter(_.isArray)
  def strings: JsonSelection = filter(_.isString)
  def numbers: JsonSelection = filter(_.isNumber)
  def booleans: JsonSelection = filter(_.isBoolean)
  def nulls: JsonSelection   = filter(_.isNull)

  // ===========================================================================
  // State Checks
  // ===========================================================================

  def isEmpty: Boolean = toEither.fold(_ => true, _.isEmpty)

  def nonEmpty: Boolean = toEither.fold(_ => false, _.nonEmpty)

  def size: Int = toEither.fold(_ => 0, _.size)

  // ===========================================================================
  // Combination
  // ===========================================================================

  /**
   * Combines this selection with another, concatenating values or errors.
   */
  def ++(other: JsonSelection): JsonSelection =
    (toEither, other.toEither) match {
      case (Right(a), Right(b)) => JsonSelection(Right(a ++ b))
      case (Left(a), Left(b))   => JsonSelection(Left(SchemaError.expectationMismatch(List.empty[DynamicOptic.Node], s"${a.message}; ${b.message}"))) // Combine errors roughly
      case (Left(a), _)         => JsonSelection(Left(a))
      case (_, Left(b))         => JsonSelection(Left(b))
    }

  // ===========================================================================
  // Unsafe Operations
  // ===========================================================================

  def oneUnsafe: Json = one.fold(e => throw JsonError.fromSchemaError(e), identity)

  def firstUnsafe: Json = first.fold(e => throw JsonError.fromSchemaError(e), identity)

  // ===========================================================================
  // Terminal Operations
  // ===========================================================================

  /**
   * Returns the single selected value, or an error if there is not exactly one.
   */
  def one: Either[SchemaError, Json] =
    toEither.flatMap {
      case Vector(one) => Right(one)
      case Vector()    => Left(SchemaError.expectationMismatch(List.empty[DynamicOptic.Node], "Expected exactly one element, found none"))
      case _           => Left(SchemaError.expectationMismatch(List.empty[DynamicOptic.Node], "Expected exactly one element, found multiple"))
    }

  /**
   * Returns the first selected value, or an error if empty.
   */
  def first: Either[SchemaError, Json] =
    toEither.flatMap {
      case head +: _ => Right(head)
      case _         => Left(SchemaError.expectationMismatch(List.empty[DynamicOptic.Node], "Expected at least one element, found none"))
    }

  /**
   * Returns all selected values as a Vector.
   */
  def toArray: Either[SchemaError, Vector[Json]] = toEither
}

object JsonSelection {
  def empty: JsonSelection = JsonSelection(Right(Vector.empty))
  def apply(json: Json): JsonSelection = JsonSelection(Right(Vector(json)))

  /**
   * Creates a selection containing multiple values.
   */
  def fromVector(jsons: Vector[Json]): JsonSelection = JsonSelection(Right(jsons))

  /**
   * Creates a failed selection with the given error.
   */
  def fail(error: SchemaError): JsonSelection = JsonSelection(Left(error))

  /**
   * Creates a failed selection with the given message.
   */
  def fail(message: String): JsonSelection =
    JsonSelection(Left(SchemaError.expectationMismatch(List.empty[DynamicOptic.Node], message)))
}
