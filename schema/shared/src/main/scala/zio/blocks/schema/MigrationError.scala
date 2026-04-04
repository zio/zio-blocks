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

import zio.blocks.chunk.Chunk

/**
 * An error that occurs during migration execution.
 */
sealed trait MigrationError extends Product with Serializable {

  /** A human-readable message describing the error. */
  def message: String
}

object MigrationError {

  final case class NotFound(
    path: String,
    details: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Value not found: $details"
      else s"Value not found at '$path': $details"
  }

  final case class TypeMismatch(
    path: String,
    expected: String,
    actual: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Type mismatch: expected $expected but found $actual"
      else s"Type mismatch at '$path': expected $expected but found $actual"
  }

  final case class MissingField(
    path: String,
    fieldName: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Missing field '$fieldName'"
      else s"Missing field '$fieldName' at '$path'"
  }

  final case class UnknownCase(
    path: String,
    caseName: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Unknown case '$caseName'"
      else s"Unknown case '$caseName' at '$path'"
  }

  final case class TransformFailed(
    path: String,
    details: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Transform failed: $details"
      else s"Transform failed at '$path': $details"
  }

  final case class IndexOutOfBounds(
    path: String,
    index: Int,
    size: Int
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Index $index out of bounds (size: $size)"
      else s"Index $index out of bounds (size: $size) at '$path'"
  }

  final case class KeyNotFound(
    path: String,
    key: DynamicValue
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Key not found: $key"
      else s"Key not found at '$path': $key"
  }

  final case class DefaultFailed(
    path: String,
    details: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Failed to compute default value: $details"
      else s"Failed to compute default value at '$path': $details"
  }

  final case class InvalidAction(
    path: String,
    action: String,
    reason: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Invalid action '$action': $reason"
      else s"Invalid action '$action' at '$path': $reason"
  }

  final case class Multiple(
    errors: Chunk[MigrationError]
  ) extends MigrationError {
    require(errors.nonEmpty, "Multiple errors must contain at least one error")

    def message: String = {
      val sb = new StringBuilder
      errors.zipWithIndex.foreach { case (e, idx) =>
        if (idx > 0) sb.append('\n')
        sb.append(e.message)
      }
      sb.toString()
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Smart Constructors
  // ═══════════════════════════════════════════════════════════════════════════════

  def notFound(details: String): MigrationError =
    NotFound("", details)

  def notFound(path: String, details: String): MigrationError =
    NotFound(path, details)

  def notFound(at: DynamicOptic, details: String): MigrationError =
    NotFound(at.toScalaString, details)

  def typeMismatch(expected: String, actual: String): MigrationError =
    TypeMismatch("", expected, actual)

  def typeMismatch(path: String, expected: String, actual: String): MigrationError =
    TypeMismatch(path, expected, actual)

  def typeMismatch(at: DynamicOptic, expected: String, actual: String): MigrationError =
    TypeMismatch(at.toScalaString, expected, actual)

  def missingField(fieldName: String): MigrationError =
    MissingField("", fieldName)

  def missingField(path: String, fieldName: String): MigrationError =
    MissingField(path, fieldName)

  def unknownCase(caseName: String): MigrationError =
    UnknownCase("", caseName)

  def unknownCase(path: String, caseName: String): MigrationError =
    UnknownCase(path, caseName)

  def unknownCase(at: DynamicOptic, caseName: String): MigrationError =
    UnknownCase(at.toScalaString, caseName)

  def transformFailed(details: String): MigrationError =
    TransformFailed("", details)

  def transformFailed(path: String, details: String): MigrationError =
    TransformFailed(path, details)

  def transformFailed(at: DynamicOptic, details: String): MigrationError =
    TransformFailed(at.toScalaString, details)

  def indexOutOfBounds(index: Int, size: Int): MigrationError =
    IndexOutOfBounds("", index, size)

  def indexOutOfBounds(path: String, index: Int, size: Int): MigrationError =
    IndexOutOfBounds(path, index, size)

  def keyNotFound(key: DynamicValue): MigrationError =
    KeyNotFound("", key)

  def keyNotFound(path: String, key: DynamicValue): MigrationError =
    KeyNotFound(path, key)

  def defaultFailed(details: String): MigrationError =
    DefaultFailed("", details)

  def defaultFailed(path: String, details: String): MigrationError =
    DefaultFailed(path, details)

  def invalidAction(action: String, reason: String): MigrationError =
    InvalidAction("", action, reason)

  def invalidAction(path: String, action: String, reason: String): MigrationError =
    InvalidAction(path, action, reason)

  def multiple(errors: Chunk[MigrationError]): MigrationError =
    if (errors.isEmpty) notFound("No errors")
    else if (errors.length == 1) errors.head
    else Multiple(errors)

  def multiple(errors: MigrationError*): MigrationError =
    multiple(Chunk.from(errors))

  def aggregate(errors: Chunk[MigrationError]): Option[MigrationError] =
    if (errors.isEmpty) None
    else if (errors.length == 1) Some(errors.head)
    else Some(Multiple(errors))

  def aggregate(errors: MigrationError*): Option[MigrationError] =
    aggregate(Chunk.from(errors))

  def fromSchemaError(schemaError: SchemaError): MigrationError =
    TransformFailed("", schemaError.message)
}
