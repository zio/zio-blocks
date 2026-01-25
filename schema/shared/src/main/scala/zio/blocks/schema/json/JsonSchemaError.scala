package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic
import scala.util.control.NoStackTrace

/**
 * Error type for JSON Schema validation failures.
 *
 * Collects all validation errors found during schema validation, with detailed
 * path information for each error location.
 */
final case class JsonSchemaError(errors: ::[JsonSchemaError.Single]) extends Exception with NoStackTrace {

  def ++(other: JsonSchemaError): JsonSchemaError =
    JsonSchemaError(new ::(errors.head, errors.tail ++ other.errors))

  override def getMessage: String = message

  def message: String = errors
    .foldLeft(new java.lang.StringBuilder) {
      var lineFeed = false
      (sb, e) =>
        if (lineFeed) sb.append('\n')
        else lineFeed = true
        sb.append(e.message)
    }
    .toString

  /** Returns all error messages as a list. */
  def messages: List[String] = errors.map(_.message)

  /** Returns the first error. */
  def head: JsonSchemaError.Single = errors.head
}

object JsonSchemaError {

  /** Creates a single-error JsonSchemaError. */
  def apply(single: Single): JsonSchemaError = new JsonSchemaError(new ::(single, Nil))

  /** Creates from a list of errors. */
  def fromList(errors: List[Single]): Option[JsonSchemaError] = errors match {
    case head :: tail => Some(new JsonSchemaError(new ::(head, tail)))
    case Nil          => None
  }

  /** Creates from a non-empty collection. */
  def fromNonEmpty(head: Single, tail: Single*): JsonSchemaError =
    new JsonSchemaError(new ::(head, tail.toList))

  // ─────────────────────────────────────────────────────────────────────────
  // Error Types
  // ─────────────────────────────────────────────────────────────────────────

  sealed trait Single {
    def path: DynamicOptic
    def message: String
  }

  /** Type mismatch between expected and actual. */
  final case class TypeMismatch(
    path: DynamicOptic,
    expected: JsonType,
    actual: JsonType
  ) extends Single {
    def message: String = s"Type mismatch at $path: expected ${expected.name}, got ${actual.name}"
  }

  /** Constraint violation (generic). */
  final case class ConstraintViolation(
    path: DynamicOptic,
    constraint: String,
    value: String
  ) extends Single {
    def message: String = s"Constraint '$constraint' violated at $path for value: $value"
  }

  /** String pattern mismatch. */
  final case class PatternMismatch(
    path: DynamicOptic,
    pattern: String,
    value: String
  ) extends Single {
    def message: String = s"Pattern '$pattern' not matched at $path for value: \"$value\""
  }

  /** Required field missing. */
  final case class RequiredMissing(
    path: DynamicOptic,
    field: String
  ) extends Single {
    def message: String = s"Required field '$field' missing at $path"
  }

  /** Additional property not allowed. */
  final case class AdditionalPropertyNotAllowed(
    path: DynamicOptic,
    field: String
  ) extends Single {
    def message: String = s"Additional property '$field' not allowed at $path"
  }

  /** Composition keyword failed (allOf, anyOf, oneOf, not). */
  final case class CompositionFailed(
    path: DynamicOptic,
    keyword: String,
    details: String
  ) extends Single {
    def message: String = s"$keyword failed at $path: $details"
  }

  /** Reference could not be resolved. */
  final case class RefNotResolved(
    path: DynamicOptic,
    ref: String
  ) extends Single {
    def message: String = s"Reference '$ref' could not be resolved at $path"
  }

  /** Value not in enum. */
  final case class NotInEnum(
    path: DynamicOptic,
    value: Json,
    allowed: Vector[Json]
  ) extends Single {
    def message: String = s"Value at $path not in allowed enum values"
  }

  /** Const mismatch. */
  final case class ConstMismatch(
    path: DynamicOptic,
    expected: Json,
    actual: Json
  ) extends Single {
    def message: String = s"Value at $path does not match const"
  }

  /** Minimum constraint violated. */
  final case class MinimumViolated(
    path: DynamicOptic,
    minimum: BigDecimal,
    actual: BigDecimal,
    exclusive: Boolean
  ) extends Single {
    def message: String = {
      val op = if (exclusive) ">" else ">="
      s"Value $actual at $path must be $op $minimum"
    }
  }

  /** Maximum constraint violated. */
  final case class MaximumViolated(
    path: DynamicOptic,
    maximum: BigDecimal,
    actual: BigDecimal,
    exclusive: Boolean
  ) extends Single {
    def message: String = {
      val op = if (exclusive) "<" else "<="
      s"Value $actual at $path must be $op $maximum"
    }
  }

  /** MultipleOf constraint violated. */
  final case class MultipleOfViolated(
    path: DynamicOptic,
    multipleOf: BigDecimal,
    actual: BigDecimal
  ) extends Single {
    def message: String = s"Value $actual at $path must be a multiple of $multipleOf"
  }

  /** String length constraint violated. */
  final case class LengthViolated(
    path: DynamicOptic,
    constraint: String,
    expected: Int,
    actual: Int
  ) extends Single {
    def message: String = s"$constraint at $path: expected $expected, got $actual"
  }

  /** Array items count constraint violated. */
  final case class ItemsCountViolated(
    path: DynamicOptic,
    constraint: String,
    expected: Int,
    actual: Int
  ) extends Single {
    def message: String = s"$constraint at $path: expected $expected, got $actual items"
  }

  /** Object properties count constraint violated. */
  final case class PropertiesCountViolated(
    path: DynamicOptic,
    constraint: String,
    expected: Int,
    actual: Int
  ) extends Single {
    def message: String = s"$constraint at $path: expected $expected, got $actual properties"
  }

  /** Unique items constraint violated. */
  final case class UniqueItemsViolated(
    path: DynamicOptic,
    duplicateIndex: Int
  ) extends Single {
    def message: String = s"Array at $path must have unique items (duplicate at index $duplicateIndex)"
  }

  /** Format validation failed. */
  final case class FormatInvalid(
    path: DynamicOptic,
    format: String,
    value: String
  ) extends Single {
    def message: String = s"Value \"$value\" at $path is not a valid $format"
  }

  /** Schema parsing error. */
  final case class SchemaParseError(
    path: DynamicOptic,
    details: String
  ) extends Single {
    def message: String = s"Schema parse error at $path: $details"
  }

  /** Contains constraint not satisfied. */
  final case class ContainsNotSatisfied(
    path: DynamicOptic,
    minContains: Int,
    maxContains: Option[Int],
    actualCount: Int
  ) extends Single {
    def message: String = {
      val range = maxContains match {
        case Some(max) => s"between $minContains and $max"
        case None      => s"at least $minContains"
      }
      s"Array at $path must contain $range matching items, found $actualCount"
    }
  }

  /** Property names validation failed. */
  final case class PropertyNameInvalid(
    path: DynamicOptic,
    propertyName: String,
    details: String
  ) extends Single {
    def message: String = s"Property name '$propertyName' at $path is invalid: $details"
  }
}
