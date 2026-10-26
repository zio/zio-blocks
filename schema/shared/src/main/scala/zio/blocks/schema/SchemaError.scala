/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import scala.collection.immutable.ArraySeq
import scala.util.control.NoStackTrace

/**
 * A `SchemaError` reports why decoding, conversion or validation failed. It
 * holds one or more [[SchemaError.Single]] failures, each with the path
 * ([[DynamicOptic]]) of the place in the data where it happened.
 * `Schema#fromDynamicValue` collects the failures of all record fields in one
 * error, while the JSON codec stops at the first failure.
 *
 * It extends `Exception` without a stack trace, so a `Schema#transform`
 * function can throw it: the path of a failure says where it happened, not the
 * stack.
 *
 * {{{
 * val error = SchemaError.missingField(Nil, "title").atField("book") ++
 *   SchemaError.validationFailed("year must be positive").atField("year")
 *
 * val text = error.message
 * // Missing field 'title' at: .book
 * // year must be positive at: .year
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/schema-error/ SchemaError]]
 */
final case class SchemaError(errors: ::[SchemaError.Single]) extends Exception with NoStackTrace {

  /**
   * Returns an error with the failures of this error followed by those of
   * `other`.
   */
  def ++(other: SchemaError): SchemaError = SchemaError(new ::(errors.head, errors.tail ++ other.errors))

  override def getMessage: String = message

  /** The messages of all failures, one per line. */
  def message: String = errors
    .foldLeft(new java.lang.StringBuilder) {
      var lineFeed = false
      (sb, e) =>
        if (lineFeed) sb.append('\n')
        else lineFeed = true
        sb.append(e.message)
    }
    .toString

  /** Prepends a field access to the path of all errors in this SchemaError. */
  def atField(name: String): SchemaError = mapSource(path => DynamicOptic.root.field(name)(path))

  /** Prepends an index access to the path of all errors in this SchemaError. */
  def atIndex(index: Int): SchemaError = mapSource(path => DynamicOptic.root.at(index)(path))

  /** Prepends a case access to the path of all errors in this SchemaError. */
  def atCase(name: String): SchemaError = mapSource(path => DynamicOptic.root.caseOf(name)(path))

  /**
   * Prepends a map key access to the path of all errors in this SchemaError.
   */
  def atKey(key: DynamicValue): SchemaError =
    mapSource(path => new DynamicOptic(path.nodes.prepended(new DynamicOptic.Node.AtMapKey(key))))

  private[this] def mapSource(f: DynamicOptic => DynamicOptic): SchemaError =
    new SchemaError(new ::(SchemaError.remapSource(errors.head, f), errors.tail.map(SchemaError.remapSource(_, f))))
}

/**
 * Constructors of [[SchemaError]] values and the kinds of failure they hold.
 *
 * The constructors that take a `trace` expect its path nodes from the innermost
 * to the outermost, the order in which a decoder collects them. Pass `Nil` for
 * the root, and add the path with `atField` and the other `at*` methods of the
 * error.
 */
object SchemaError {

  /**
   * Creates a [[ConversionFailed]] error with the message `details` at the path
   * `trace`.
   */
  def conversionFailed(trace: List[DynamicOptic.Node], details: String): SchemaError =
    new SchemaError(new ::(ConversionFailed(toDynamicOptic(trace), details, None), Nil))

  /**
   * Creates a [[ConversionFailed]] error at the root that wraps `cause`. Its
   * message is `contextMessage` followed by the messages of `cause` under
   * `Caused by:`, and does not show the error's own path, even after `atField`.
   */
  def conversionFailed(contextMessage: String, cause: SchemaError): SchemaError =
    new SchemaError(
      new ::(
        ConversionFailed(
          DynamicOptic.root,
          contextMessage,
          Some(cause)
        ),
        Nil
      )
    )

  /**
   * Creates an [[ExpectationMismatch]] error, for a value of the wrong shape,
   * with the message `expectation` at the path `trace`.
   */
  def expectationMismatch(trace: List[DynamicOptic.Node], expectation: String): SchemaError =
    new SchemaError(new ::(new ExpectationMismatch(toDynamicOptic(trace), expectation), Nil))

  /**
   * Creates a [[MissingField]] error for the absent field `fieldName` of the
   * record at the path `trace`.
   */
  def missingField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError =
    new SchemaError(new ::(new MissingField(toDynamicOptic(trace), fieldName), Nil))

  /**
   * Creates a [[DuplicatedField]] error for the field `fieldName` that appears
   * more than once in the record at the path `trace`.
   */
  def duplicatedField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError =
    new SchemaError(new ::(new DuplicatedField(toDynamicOptic(trace), fieldName), Nil))

  /**
   * Creates an [[UnknownCase]] error for the case `caseName` that the variant
   * at the path `trace` does not have.
   */
  def unknownCase(trace: List[DynamicOptic.Node], caseName: String): SchemaError =
    new SchemaError(new ::(new UnknownCase(toDynamicOptic(trace), caseName), Nil))

  /**
   * Creates an error with the message `message` at the root path, for a value
   * that breaks a rule. It is the same as `conversionFailed(Nil, message)`.
   *
   * Throw it from the `to` function of `Schema#transform`, or from a smart
   * constructor that `to` calls, to reject a value while decoding. The decoder
   * keeps only the message, at a path that ends in `.wrapped`, the step into
   * the transformed value: `Schema#fromDynamicValue` returns it as a new
   * [[ConversionFailed]], and the JSON codec as a new [[ExpectationMismatch]].
   *
   * {{{
   * final case class Title(value: String)
   * object Title {
   *   def make(value: String): Title =
   *     if (value.trim.nonEmpty) Title(value)
   *     else throw SchemaError.validationFailed("title must not be blank")
   *
   *   implicit val schema: Schema[Title] = Schema[String].transform(make, _.value)
   * }
   *
   * val title = "\"Zionomicon\"".fromJson[Title] // Right(Title(Zionomicon))
   * val blank = "\"  \"".fromJson[Title]
   * // Left(zio.blocks.schema.SchemaError: title must not be blank at: .wrapped)
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/schema-error/ SchemaError]]
   */
  def validationFailed(message: String): SchemaError = conversionFailed(Nil, message)

  /**
   * Creates a [[Message]] error with the free-text message `details` at `path`,
   * the root by default.
   */
  def message(details: String, path: DynamicOptic = DynamicOptic.root): SchemaError =
    new SchemaError(new ::(Message(path, details), Nil))

  /**
   * Creates a [[Message]] error with the free-text message `details` at the
   * root path.
   */
  def apply(details: String): SchemaError = message(details)

  private[schema] def remapSource(single: Single, f: DynamicOptic => DynamicOptic): Single = single match {
    case e: ConversionFailed    => e.copy(source = f(e.source))
    case e: MissingField        => e.copy(source = f(e.source))
    case e: DuplicatedField     => e.copy(source = f(e.source))
    case e: ExpectationMismatch => e.copy(source = f(e.source))
    case e: UnknownCase         => e.copy(source = f(e.source))
    case e: Message             => e.copy(source = f(e.source))
    case e: MigrationError      => e.copy(source = f(e.source))
  }

  private[this] def toDynamicOptic(trace: List[DynamicOptic.Node]): DynamicOptic = {
    val nodes = trace.toArray
    reverse(nodes)
    new DynamicOptic(ArraySeq.unsafeWrapArray(nodes))
  }

  private[this] def reverse(nodes: Array[DynamicOptic.Node]): Unit =
    if (nodes.length > 1) {
      var idx1 = 0
      var idx2 = nodes.length - 1
      while (idx1 < idx2) {
        val node = nodes(idx1)
        nodes(idx1) = nodes(idx2)
        nodes(idx2) = node
        idx1 += 1
        idx2 -= 1
      }
    }

  /** One failure: a message and the path of the place where it happened. */
  sealed trait Single {

    /**
     * The text of this failure. Most kinds end it with ` at: ` and the path.
     */
    def message: String

    /** The path of the failing value, from the root of the data. */
    def source: DynamicOptic
  }

  /**
   * The failures of conversions such as `Into`. [[ConversionFailed]] is the
   * only one.
   */
  sealed trait IntoError extends Single {

    /** The path of the failing value, from the root of the data. */
    def source: DynamicOptic
  }

  /**
   * A conversion or validation failed with the message `details`, optionally
   * caused by the failures in `cause`.
   *
   * Its message adds ` at: ` and the path when the path is not the root. When
   * `cause` is set, the message lists the messages of `cause` under
   * `Caused by:` instead, without the path.
   */
  case class ConversionFailed(
    source: DynamicOptic,
    details: String,
    cause: Option[SchemaError] = None
  ) extends IntoError {
    override def message: String =
      cause match {
        case Some(causeErr) =>
          val sb = new java.lang.StringBuilder(details)
          sb.append('\n')
          if (causeErr.errors.isEmpty) sb.append("<no further details>")
          else if (causeErr.errors.length == 1) sb.append("  Caused by: ").append(causeErr.errors.head.message)
          else {
            sb.append("  Caused by:\n")
            val start = sb.length
            causeErr.errors.foreach { e =>
              if (sb.length > start) sb.append('\n')
              sb.append("  - ").append(e.message)
            }
          }
          sb.toString
        case _ =>
          if (source.nodes.isEmpty) details
          else s"$details at: ${source.toScalaString}"
      }
  }

  /**
   * The record at `source` has no field `fieldName`. The message is
   * `Missing field '<fieldName>' at: <path>`.
   */
  case class MissingField(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Missing field '$fieldName' at: ${source.toScalaString}"
  }

  /** The field `fieldName` appears more than once in the record at `source`. */
  case class DuplicatedField(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Duplicated field '$fieldName' at: ${source.toScalaString}"
  }

  /**
   * The value at `source` has the wrong shape; `expectation` says what was
   * expected, as in `Expected a record`.
   */
  case class ExpectationMismatch(source: DynamicOptic, expectation: String) extends Single {
    override def message: String = s"$expectation at: ${source.toScalaString}"
  }

  /** The variant at `source` has no case named `caseName`. */
  case class UnknownCase(source: DynamicOptic, caseName: String) extends Single {
    override def message: String = s"Unknown case '$caseName' at: ${source.toScalaString}"
  }

  /**
   * A failure described by the free text `details`.
   *
   * Unlike the other kinds, its message renders the path with
   * `DynamicOptic#toString`, as in `.format<Ebook>` or `.~` for a wrapped
   * value, rather than with `DynamicOptic#toScalaString`, as in
   * `.format.when[Ebook]` or `.wrapped`.
   */
  case class Message(source: DynamicOptic, details: String) extends Single {
    override def message: String =
      if (source.nodes.isEmpty) details
      else s"$details at: ${source.toString}"
  }

  /** A step of a schema migration failed at `source`; `kind` says how. */
  case class MigrationError(source: DynamicOptic, kind: MigrationErrorKind) extends Single {
    override def message: String = kind match {
      case MigrationErrorKind.PathNotFound =>
        s"Path not found: ${source.toScalaString}"
      case MigrationErrorKind.TypeMismatch(expected, actual) =>
        s"Type mismatch at ${source.toScalaString}: expected $expected, got $actual"
      case MigrationErrorKind.MissingDefault(fieldName) =>
        s"Missing default value for field '$fieldName' at ${source.toScalaString}"
      case MigrationErrorKind.TransformFailed(reason) =>
        s"Transform failed at ${source.toScalaString}: $reason"
      case MigrationErrorKind.FieldNotFound(fieldName) =>
        s"Field '$fieldName' not found at ${source.toScalaString}"
      case MigrationErrorKind.FieldAlreadyExists(fieldName) =>
        s"Field '$fieldName' already exists at ${source.toScalaString}"
      case MigrationErrorKind.CaseNotFound(caseName) =>
        s"Case '$caseName' not found at ${source.toScalaString}"
      case MigrationErrorKind.InvalidValue(reason) =>
        s"Invalid value at ${source.toScalaString}: $reason"
      case MigrationErrorKind.MandateFailed(reason) =>
        s"Mandate failed at ${source.toScalaString}: $reason"
    }
  }

  /** The ways a migration step can fail. */
  sealed trait MigrationErrorKind

  /**
   * The kinds of [[MigrationError]]: `PathNotFound` (the path does not exist in
   * the value), `TypeMismatch` (the value has the type `actual` where
   * `expected` was required), `MissingDefault` (the field has no default
   * value), `TransformFailed` and `InvalidValue` (with a `reason`),
   * `FieldNotFound`, `FieldAlreadyExists`, `CaseNotFound`, and `MandateFailed`
   * (making an optional field mandatory failed; not raised by the built-in
   * migrations).
   */
  object MigrationErrorKind {

    /**
     * A sequence index on the action's path is out of range, or a map key on
     * the path is not in the map.
     */
    case object PathNotFound extends MigrationErrorKind

    /**
     * A step of the path, or the action, needs a value of another kind:
     * `expected` is `Record`, `Variant`, `Sequence` or `Map`, and `actual` is
     * the simple class name of the `DynamicValue` found.
     */
    case class TypeMismatch(expected: String, actual: String) extends MigrationErrorKind

    /**
     * The field `fieldName` has no default value. No built-in migration action
     * raises it.
     */
    case class MissingDefault(fieldName: String) extends MigrationErrorKind

    /**
     * An action cannot run, and `reason` says why: for example, its path does
     * not end where the action needs, its expression returns no value or more
     * than one, it is an irreversible action run in reverse, or it throws.
     */
    case class TransformFailed(reason: String) extends MigrationErrorKind

    /**
     * The record has no field `fieldName`: a field step of the path, or the
     * field that `DropField` or `RenameField` acts on.
     */
    case class FieldNotFound(fieldName: String) extends MigrationErrorKind

    /**
     * `AddField`, or `RenameField` to `fieldName`, finds a field with that name
     * in the record already.
     */
    case class FieldAlreadyExists(fieldName: String) extends MigrationErrorKind

    /**
     * A case step of the path for `caseName` meets a variant of another case.
     */
    case class CaseNotFound(caseName: String) extends MigrationErrorKind

    /** A map key step of the path holds `null` instead of a key. */
    case class InvalidValue(reason: String) extends MigrationErrorKind

    /**
     * Making an optional field mandatory failed. No built-in migration action
     * raises it: `MandateField` uses its default for `None`.
     */
    case class MandateFailed(reason: String) extends MigrationErrorKind
  }

  // Helper methods for creating migration errors
  /** Creates a [[MigrationError]] of kind `kind` at `path`. */
  def migrationError(path: DynamicOptic, kind: MigrationErrorKind): SchemaError =
    new SchemaError(new ::(MigrationError(path, kind), Nil))

  /** Creates a [[MigrationError]] of kind `PathNotFound` at `path`. */
  def pathNotFound(path: DynamicOptic): SchemaError =
    migrationError(path, MigrationErrorKind.PathNotFound)

  /** Creates a [[MigrationError]] of kind `TypeMismatch` at `path`. */
  def typeMismatch(path: DynamicOptic, expected: String, actual: String): SchemaError =
    migrationError(path, MigrationErrorKind.TypeMismatch(expected, actual))

  /** Creates a [[MigrationError]] of kind `MissingDefault` at `path`. */
  def missingDefault(path: DynamicOptic, fieldName: String): SchemaError =
    migrationError(path, MigrationErrorKind.MissingDefault(fieldName))

  /** Creates a [[MigrationError]] of kind `TransformFailed` at `path`. */
  def transformFailed(path: DynamicOptic, reason: String): SchemaError =
    migrationError(path, MigrationErrorKind.TransformFailed(reason))

  /** Creates a [[MigrationError]] of kind `FieldNotFound` at `path`. */
  def fieldNotFound(path: DynamicOptic, fieldName: String): SchemaError =
    migrationError(path, MigrationErrorKind.FieldNotFound(fieldName))

  /** Creates a [[MigrationError]] of kind `FieldAlreadyExists` at `path`. */
  def fieldAlreadyExists(path: DynamicOptic, fieldName: String): SchemaError =
    migrationError(path, MigrationErrorKind.FieldAlreadyExists(fieldName))

  /** Creates a [[MigrationError]] of kind `CaseNotFound` at `path`. */
  def caseNotFound(path: DynamicOptic, caseName: String): SchemaError =
    migrationError(path, MigrationErrorKind.CaseNotFound(caseName))

  /** Creates a [[MigrationError]] of kind `InvalidValue` at `path`. */
  def invalidValue(path: DynamicOptic, reason: String): SchemaError =
    migrationError(path, MigrationErrorKind.InvalidValue(reason))

  /** Creates a [[MigrationError]] of kind `MandateFailed` at `path`. */
  def mandateFailed(path: DynamicOptic, reason: String): SchemaError =
    migrationError(path, MigrationErrorKind.MandateFailed(reason))
}
