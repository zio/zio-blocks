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

import java.util.regex.Pattern

/**
 * Serializable AST for primitive-to-primitive value transformations used by
 * migrations. No user functions or closures; fully serializable for use in
 * registries and offline replay.
 */
sealed trait MigrationExpr {

  /**
   * Evaluates this expression with the given input (e.g. the value at a path or
   * the root context). Returns the resulting DynamicValue or a MigrationError.
   */
  def eval(input: DynamicValue): Either[MigrationError, DynamicValue]
}

object MigrationExpr {

  final case class Literal(value: DynamicValue) extends MigrationExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
  }

  case object IntToString extends MigrationExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      input match {
        case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(n.toString)))
        case other =>
          Left(MigrationError(s"IntToString: expected Int, got ${other.valueType}", DynamicOptic.root))
      }
  }

  case object StringToInt extends MigrationExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      input match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          scala.util.Try(s.toInt).toOption match {
            case Some(n) => Right(DynamicValue.Primitive(PrimitiveValue.Int(n)))
            case None    => Left(MigrationError(s"StringToInt: '$s' is not a valid integer", DynamicOptic.root))
          }
        case other =>
          Left(MigrationError(s"StringToInt: expected String, got ${other.valueType}", DynamicOptic.root))
      }
  }

  final case class StringConcat(separator: String) extends MigrationExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      input match {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.iterator.toMap
          (fieldMap.get("_left"), fieldMap.get("_right")) match {
            case (Some(l), Some(r)) =>
              (l, r) match {
                case (
                      DynamicValue.Primitive(PrimitiveValue.String(a)),
                      DynamicValue.Primitive(PrimitiveValue.String(b))
                    ) =>
                  Right(DynamicValue.Primitive(PrimitiveValue.String(a + separator + b)))
                case _ =>
                  Left(MigrationError("StringConcat: expected (String, String)", DynamicOptic.root))
              }
            case _ =>
              Left(MigrationError("StringConcat: expected Record(_left, _right)", DynamicOptic.root))
          }
        case other =>
          Left(MigrationError(s"StringConcat: expected Record, got ${other.valueType}", DynamicOptic.root))
      }
  }

  final case class StringSplitLeft(separator: String) extends MigrationExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      input match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          val part = s.split(Pattern.quote(separator), 2).head
          Right(DynamicValue.Primitive(PrimitiveValue.String(part)))
        case other =>
          Left(MigrationError(s"StringSplitLeft: expected String, got ${other.valueType}", DynamicOptic.root))
      }
  }

  final case class StringSplitRight(separator: String) extends MigrationExpr {
    def eval(input: DynamicValue): Either[MigrationError, DynamicValue] =
      input match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
          val parts = s.split(Pattern.quote(separator), 2)
          val part  = if (parts.length > 1) parts(1) else ""
          Right(DynamicValue.Primitive(PrimitiveValue.String(part)))
        case other =>
          Left(MigrationError(s"StringSplitRight: expected String, got ${other.valueType}", DynamicOptic.root))
      }
  }
}
