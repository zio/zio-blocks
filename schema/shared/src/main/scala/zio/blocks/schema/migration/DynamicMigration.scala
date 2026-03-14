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

package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue, PrimitiveValue}

/**
 * Single migration step at a path. Each action has an algebraic [[reverse]] so
 * that [[DynamicMigration.reverse]] is computed without runtime code
 * generation.
 */
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {
  // --- Record actions ---
  final case class AddField(at: DynamicOptic, default: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }
  final case class DropField(at: DynamicOptic, defaultForReverse: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }
  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction = {
      val oldName = DynamicOptic.terminalName(at)
      val newPath = DynamicOptic.replaceTerminal(at, to)
      Rename(newPath, oldName)
    }
  }
  final case class TransformValue(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, transform.inverse)
  }
  final case class Mandate(at: DynamicOptic, default: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }
  final case class Optionalize(at: DynamicOptic) extends MigrationAction {
    def reverse: MigrationAction =
      throw new UnsupportedOperationException(
        "Optionalize.reverse requires an explicit default from the builder; it is mathematically lossy otherwise."
      )
  }
  final case class ChangeType(at: DynamicOptic, converter: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, converter.inverse)
  }

  // --- Enum actions ---
  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }
  final case class TransformCase(at: DynamicOptic, actions: Vector[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, actions.reverse.map(_.reverse))
  }

  // --- Collection / map actions ---
  final case class TransformElements(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, transform.inverse)
  }
  final case class TransformKeys(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform.inverse)
  }
  final case class TransformValues(at: DynamicOptic, transform: DynamicSchemaExpr) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform.inverse)
  }
}

/**
 * Fully serializable migration: a sequence of actions to apply to a
 * [[DynamicValue]]. [[apply]] is the interpreter; [[reverse]] is computed
 * algebraically from each action's [[MigrationAction.reverse]].
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(v => applyAction(v, action.at, action))
    }

  private def applyAction(
    current: DynamicValue,
    optic: DynamicOptic,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] =
    optic match {
      case DynamicOptic.Field(name, Some(nextOptic)) =>
        current match {
          case r: DynamicValue.Record =>
            recordField(r, name) match {
              case Some(child) =>
                applyAction(child, nextOptic, action).map { newChild =>
                  DynamicValue.Record(recordUpdated(r.fields, name, newChild))
                }
              case None =>
                Left(MigrationError(optic, s"Path failure: Field '$name' not found in record"))
            }
          case _ =>
            Left(
              MigrationError(
                optic,
                s"Path failure: Expected Record at field '$name', but found ${current.getClass.getSimpleName}"
              )
            )
        }

      case DynamicOptic.Field(name, None) =>
        current match {
          case r: DynamicValue.Record =>
            applyTerminalRecordAction(r.fields, name, action, optic)
          case _ =>
            Left(MigrationError(optic, s"Action failure: Expected Record to apply action at field '$name'"))
        }

      case DynamicOptic.Case(caseName, Some(nextOptic)) =>
        current match {
          case v: DynamicValue.Variant if v.caseNameValue == caseName =>
            applyAction(v.value, nextOptic, action).map { newInner =>
              DynamicValue.Variant(v.caseNameValue, newInner)
            }
          case _: DynamicValue.Variant =>
            Right(current) // different case — pass through unchanged
          case _ =>
            Left(
              MigrationError(
                optic,
                s"Path failure: Expected Variant at case '$caseName', but found ${current.getClass.getSimpleName}"
              )
            )
        }

      case DynamicOptic.Case(caseName, None) =>
        current match {
          case v: DynamicValue.Variant if v.caseNameValue == caseName =>
            applyTerminalVariantAction(v, action, optic)
          case _: DynamicValue.Variant =>
            Right(current) // different case — pass through unchanged
          case _ =>
            Left(MigrationError(optic, s"Action failure: Expected Variant to apply action at case '$caseName'"))
        }

      case _ =>
        Left(MigrationError(optic, s"Unsupported optic traversal: ${optic.getClass.getSimpleName}"))
    }

  private def applyTerminalRecordAction(
    fields: Chunk[(String, DynamicValue)],
    fieldName: String,
    action: MigrationAction,
    optic: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    action match {
      case MigrationAction.AddField(_, default) =>
        evalExpr(default, None, optic).map { v =>
          DynamicValue.Record(recordSetField(fields, fieldName, v))
        }

      case MigrationAction.DropField(_, _) =>
        Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))

      case MigrationAction.Rename(_, toName) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(v) =>
            Right(
              DynamicValue.Record(
                recordSetField(fields.filterNot(_._1 == fieldName), toName, v)
              )
            )
          case None =>
            Left(MigrationError(optic, s"Rename failed: Field '$fieldName' not found"))
        }

      case MigrationAction.TransformValue(_, expr) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(v) =>
            evalExpr(expr, Some(v), optic).map { newV =>
              DynamicValue.Record(recordUpdated(fields, fieldName, newV))
            }
          case None =>
            Left(MigrationError(optic, s"TransformValue failed: Field '$fieldName' not found"))
        }

      case MigrationAction.ChangeType(_, expr) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(v) =>
            evalExpr(expr, Some(v), optic).map { newV =>
              DynamicValue.Record(recordUpdated(fields, fieldName, newV))
            }
          case None =>
            Left(MigrationError(optic, s"ChangeType failed: Field '$fieldName' not found"))
        }

      case MigrationAction.Mandate(_, defaultExpr) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(DynamicValue.Variant("Some", v)) =>
            Right(DynamicValue.Record(recordUpdated(fields, fieldName, v)))
          case Some(DynamicValue.Variant("None", _)) =>
            evalExpr(defaultExpr, None, optic).map { v =>
              DynamicValue.Record(recordUpdated(fields, fieldName, v))
            }
          case Some(_) =>
            Left(MigrationError(optic, s"Mandate failed: Field '$fieldName' is not an Option"))
          case None =>
            Left(MigrationError(optic, s"Mandate failed: Field '$fieldName' not found"))
        }

      case MigrationAction.Optionalize(_) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(v) =>
            Right(
              DynamicValue.Record(
                recordUpdated(fields, fieldName, DynamicValue.Variant("Some", v))
              )
            )
          case None =>
            Left(MigrationError(optic, s"Optionalize failed: Field '$fieldName' not found"))
        }

      case MigrationAction.RenameCase(_, from, to) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(v: DynamicValue.Variant) if v.caseNameValue == from =>
            Right(DynamicValue.Record(recordUpdated(fields, fieldName, DynamicValue.Variant(to, v.value))))
          case Some(_: DynamicValue.Variant) =>
            Right(DynamicValue.Record(fields)) // different case, no-op
          case Some(_) =>
            Left(MigrationError(optic, s"RenameCase failed: Field '$fieldName' is not a Variant"))
          case None =>
            Left(MigrationError(optic, s"RenameCase failed: Field '$fieldName' not found"))
        }

      case MigrationAction.TransformCase(_, subActions) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(v: DynamicValue.Variant) =>
            DynamicMigration(subActions).apply(v.value).map { newInner =>
              DynamicValue.Record(recordUpdated(fields, fieldName, DynamicValue.Variant(v.caseNameValue, newInner)))
            }
          case Some(_) =>
            Left(MigrationError(optic, s"TransformCase failed: Field '$fieldName' is not a Variant"))
          case None =>
            Left(MigrationError(optic, s"TransformCase failed: Field '$fieldName' not found"))
        }

      case MigrationAction.TransformElements(_, expr) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(seq: DynamicValue.Sequence) =>
            seq.elements
              .foldLeft[Either[MigrationError, Chunk[DynamicValue]]](Right(Chunk.empty)) { (acc, elem) =>
                acc.flatMap(chunk => evalExpr(expr, Some(elem), optic).map(v => chunk :+ v))
              }
              .map(newElems => DynamicValue.Record(recordUpdated(fields, fieldName, DynamicValue.Sequence(newElems))))
          case Some(_) =>
            Left(MigrationError(optic, s"TransformElements failed: Field '$fieldName' is not a Sequence"))
          case None =>
            Left(MigrationError(optic, s"TransformElements failed: Field '$fieldName' not found"))
        }

      case MigrationAction.TransformKeys(_, expr) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(m: DynamicValue.Map) =>
            m.entries
              .foldLeft[Either[MigrationError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                (acc, entry) =>
                  acc.flatMap(chunk => evalExpr(expr, Some(entry._1), optic).map(newK => chunk :+ ((newK, entry._2))))
              }
              .map(newEntries => DynamicValue.Record(recordUpdated(fields, fieldName, DynamicValue.Map(newEntries))))
          case Some(_) =>
            Left(MigrationError(optic, s"TransformKeys failed: Field '$fieldName' is not a Map"))
          case None =>
            Left(MigrationError(optic, s"TransformKeys failed: Field '$fieldName' not found"))
        }

      case MigrationAction.TransformValues(_, expr) =>
        recordFieldFromChunk(fields, fieldName) match {
          case Some(m: DynamicValue.Map) =>
            m.entries
              .foldLeft[Either[MigrationError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                (acc, entry) =>
                  acc.flatMap(chunk => evalExpr(expr, Some(entry._2), optic).map(newV => chunk :+ ((entry._1, newV))))
              }
              .map(newEntries => DynamicValue.Record(recordUpdated(fields, fieldName, DynamicValue.Map(newEntries))))
          case Some(_) =>
            Left(MigrationError(optic, s"TransformValues failed: Field '$fieldName' is not a Map"))
          case None =>
            Left(MigrationError(optic, s"TransformValues failed: Field '$fieldName' not found"))
        }
    }

  private def applyTerminalVariantAction(
    variant: DynamicValue.Variant,
    action: MigrationAction,
    optic: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    action match {
      case MigrationAction.TransformCase(_, subActions) =>
        DynamicMigration(subActions).apply(variant.value).map { newInner =>
          DynamicValue.Variant(variant.caseNameValue, newInner)
        }
      case _ =>
        Left(
          MigrationError(
            optic,
            s"Unsupported action on Variant case '${variant.caseNameValue}': ${action.getClass.getSimpleName}"
          )
        )
    }

  private def recordField(r: DynamicValue.Record, name: String): Option[DynamicValue] =
    recordFieldFromChunk(r.fields, name)

  private def recordFieldFromChunk(
    fields: Chunk[(String, DynamicValue)],
    name: String
  ): Option[DynamicValue] =
    fields.find(_._1 == name).map(_._2)

  private def recordUpdated(
    fields: Chunk[(String, DynamicValue)],
    name: String,
    newValue: DynamicValue
  ): Chunk[(String, DynamicValue)] = {
    val idx = fields.indexWhere(_._1 == name)
    if (idx >= 0) fields.updated(idx, (name, newValue))
    else fields :+ (name, newValue)
  }

  private def recordSetField(
    fields: Chunk[(String, DynamicValue)],
    name: String,
    value: DynamicValue
  ): Chunk[(String, DynamicValue)] = {
    val idx = fields.indexWhere(_._1 == name)
    if (idx >= 0) fields.updated(idx, (name, value))
    else fields :+ (name, value)
  }

  private def evalExpr(
    expr: DynamicSchemaExpr,
    context: Option[DynamicValue],
    optic: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    expr match {
      case DynamicSchemaExpr.Literal(v) =>
        Right(v)
      case DynamicSchemaExpr.BiTransform(forward, _) =>
        evalExpr(forward, context, optic)
      case DynamicSchemaExpr.Fail(reason) =>
        Left(MigrationError(optic, reason))
      case DynamicSchemaExpr.DefaultValue =>
        Left(
          MigrationError(
            optic,
            "DefaultValue is a reverse-operation sentinel and cannot be evaluated directly. " +
              "Provide a DynamicSchemaExpr.Literal when calling addField."
          )
        )
      case DynamicSchemaExpr.ConvertPrimitive(_, toTypeId) =>
        context match {
          case Some(DynamicValue.Primitive(pv)) => convertPrimitive(pv, toTypeId, optic)
          case Some(other)                      =>
            Left(
              MigrationError(
                optic,
                s"ConvertPrimitive: expected a Primitive value, got ${other.getClass.getSimpleName}"
              )
            )
          case None =>
            Left(MigrationError(optic, "ConvertPrimitive: no source value to convert"))
        }
    }

  private def convertPrimitive(
    from: PrimitiveValue,
    toTypeId: scala.Predef.String,
    optic: DynamicOptic
  ): Either[MigrationError, DynamicValue] = {
    def prim(pv: PrimitiveValue): Either[MigrationError, DynamicValue]       = Right(DynamicValue.Primitive(pv))
    def fail(msg: scala.Predef.String): Either[MigrationError, DynamicValue] =
      Left(MigrationError(optic, msg))
    def parse[T <: PrimitiveValue](s: scala.Predef.String, target: scala.Predef.String)(
      f: => T
    ): Either[MigrationError, DynamicValue] =
      scala.util.Try(f).fold(e => fail(s"Cannot parse '$s' as $target: ${e.getMessage}"), prim)

    (from, toTypeId) match {
      // identity
      case (pv, t) if pv.getClass.getSimpleName == t => prim(pv)

      // numeric widening (lossless)
      case (PrimitiveValue.Byte(v), "Short")        => prim(PrimitiveValue.Short(v.toShort))
      case (PrimitiveValue.Byte(v), "Int")          => prim(PrimitiveValue.Int(v.toInt))
      case (PrimitiveValue.Byte(v), "Long")         => prim(PrimitiveValue.Long(v.toLong))
      case (PrimitiveValue.Byte(v), "Float")        => prim(PrimitiveValue.Float(v.toFloat))
      case (PrimitiveValue.Byte(v), "Double")       => prim(PrimitiveValue.Double(v.toDouble))
      case (PrimitiveValue.Byte(v), "BigInt")       => prim(PrimitiveValue.BigInt(scala.BigInt(v.toInt)))
      case (PrimitiveValue.Byte(v), "BigDecimal")   => prim(PrimitiveValue.BigDecimal(scala.BigDecimal(v.toInt)))
      case (PrimitiveValue.Short(v), "Int")         => prim(PrimitiveValue.Int(v.toInt))
      case (PrimitiveValue.Short(v), "Long")        => prim(PrimitiveValue.Long(v.toLong))
      case (PrimitiveValue.Short(v), "Float")       => prim(PrimitiveValue.Float(v.toFloat))
      case (PrimitiveValue.Short(v), "Double")      => prim(PrimitiveValue.Double(v.toDouble))
      case (PrimitiveValue.Short(v), "BigInt")      => prim(PrimitiveValue.BigInt(scala.BigInt(v.toInt)))
      case (PrimitiveValue.Short(v), "BigDecimal")  => prim(PrimitiveValue.BigDecimal(scala.BigDecimal(v.toInt)))
      case (PrimitiveValue.Int(v), "Long")          => prim(PrimitiveValue.Long(v.toLong))
      case (PrimitiveValue.Int(v), "Float")         => prim(PrimitiveValue.Float(v.toFloat))
      case (PrimitiveValue.Int(v), "Double")        => prim(PrimitiveValue.Double(v.toDouble))
      case (PrimitiveValue.Int(v), "BigInt")        => prim(PrimitiveValue.BigInt(scala.BigInt(v)))
      case (PrimitiveValue.Int(v), "BigDecimal")    => prim(PrimitiveValue.BigDecimal(scala.BigDecimal(v)))
      case (PrimitiveValue.Long(v), "Float")        => prim(PrimitiveValue.Float(v.toFloat))
      case (PrimitiveValue.Long(v), "Double")       => prim(PrimitiveValue.Double(v.toDouble))
      case (PrimitiveValue.Long(v), "BigInt")       => prim(PrimitiveValue.BigInt(scala.BigInt(v)))
      case (PrimitiveValue.Long(v), "BigDecimal")   => prim(PrimitiveValue.BigDecimal(scala.BigDecimal(v)))
      case (PrimitiveValue.Float(v), "Double")      => prim(PrimitiveValue.Double(v.toDouble))
      case (PrimitiveValue.Float(v), "BigDecimal")  => prim(PrimitiveValue.BigDecimal(scala.BigDecimal(v.toDouble)))
      case (PrimitiveValue.Double(v), "BigDecimal") => prim(PrimitiveValue.BigDecimal(scala.BigDecimal(v)))
      case (PrimitiveValue.BigInt(v), "BigDecimal") => prim(PrimitiveValue.BigDecimal(scala.BigDecimal(v)))

      // char <-> int
      case (PrimitiveValue.Char(v), "Int") => prim(PrimitiveValue.Int(v.toInt))
      case (PrimitiveValue.Int(v), "Char") => prim(PrimitiveValue.Char(v.toChar))

      // any numeric -> String
      case (PrimitiveValue.Boolean(v), "String")    => prim(PrimitiveValue.String(v.toString))
      case (PrimitiveValue.Byte(v), "String")       => prim(PrimitiveValue.String(v.toString))
      case (PrimitiveValue.Short(v), "String")      => prim(PrimitiveValue.String(v.toString))
      case (PrimitiveValue.Int(v), "String")        => prim(PrimitiveValue.String(v.toString))
      case (PrimitiveValue.Long(v), "String")       => prim(PrimitiveValue.String(v.toString))
      case (PrimitiveValue.Float(v), "String")      => prim(PrimitiveValue.String(v.toString))
      case (PrimitiveValue.Double(v), "String")     => prim(PrimitiveValue.String(v.toString))
      case (PrimitiveValue.Char(v), "String")       => prim(PrimitiveValue.String(v.toString))
      case (PrimitiveValue.BigInt(v), "String")     => prim(PrimitiveValue.String(v.toString))
      case (PrimitiveValue.BigDecimal(v), "String") => prim(PrimitiveValue.String(v.toString))

      // String -> numeric (parsing; may fail at runtime)
      case (PrimitiveValue.String(s), "Boolean")    => parse(s, "Boolean")(PrimitiveValue.Boolean(s.toBoolean))
      case (PrimitiveValue.String(s), "Byte")       => parse(s, "Byte")(PrimitiveValue.Byte(s.toByte))
      case (PrimitiveValue.String(s), "Short")      => parse(s, "Short")(PrimitiveValue.Short(s.toShort))
      case (PrimitiveValue.String(s), "Int")        => parse(s, "Int")(PrimitiveValue.Int(s.toInt))
      case (PrimitiveValue.String(s), "Long")       => parse(s, "Long")(PrimitiveValue.Long(s.toLong))
      case (PrimitiveValue.String(s), "Float")      => parse(s, "Float")(PrimitiveValue.Float(s.toFloat))
      case (PrimitiveValue.String(s), "Double")     => parse(s, "Double")(PrimitiveValue.Double(s.toDouble))
      case (PrimitiveValue.String(s), "BigInt")     => parse(s, "BigInt")(PrimitiveValue.BigInt(scala.BigInt(s)))
      case (PrimitiveValue.String(s), "BigDecimal") =>
        parse(s, "BigDecimal")(PrimitiveValue.BigDecimal(scala.BigDecimal(s)))
      case (PrimitiveValue.String(s), "Char") =>
        if (s.length == 1) prim(PrimitiveValue.Char(s.charAt(0)))
        else fail(s"Cannot convert String '$s' to Char: expected exactly one character")

      case _ =>
        fail(s"No conversion defined from ${from.getClass.getSimpleName} to $toTypeId")
    }
  }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(this.actions.reverse.map(_.reverse))
}
