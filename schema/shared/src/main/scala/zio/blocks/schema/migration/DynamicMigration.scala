package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * A fully serializable, untyped migration that operates on [[DynamicValue]].
 *
 * A `DynamicMigration` is a pure data structure — a sequence of
 * [[MigrationAction]]s that describe how to structurally transform one
 * DynamicValue into another. Because it contains no closures or runtime
 * reflection, it can be serialized, stored in a registry, or used to generate
 * SQL DDL, offline transforms, and similar artifacts.
 *
 * Actions are applied sequentially; each action's output feeds into the next.
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to a [[DynamicValue]], producing a transformed value
   * or a [[MigrationError]] if something went wrong.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current = value
    var idx     = 0
    while (idx < actions.length) {
      DynamicMigration.applyAction(current, actions(idx)) match {
        case Right(next) => current = next
        case left        => return left
      }
      idx += 1
    }
    Right(current)
  }

  /** Sequential composition — apply `this` first, then `that`. */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(this.actions ++ that.actions)

  /** Structural inverse: reverses the action list and inverts each action. */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.reverseIterator.map(_.reverse).toVector)

  def isEmpty: Boolean = actions.isEmpty
}

object DynamicMigration {

  val empty: DynamicMigration = DynamicMigration(Vector.empty)

  // ── Action application ─────────────────────────────────────────────

  private[migration] def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = action match {
    case a: MigrationAction.AddField         => applyAddField(value, a)
    case a: MigrationAction.DropField        => applyDropField(value, a)
    case a: MigrationAction.RenameField      => applyRenameField(value, a)
    case a: MigrationAction.TransformValue   => applyTransformValue(value, a)
    case a: MigrationAction.Mandate          => applyMandate(value, a)
    case a: MigrationAction.Optionalize      => applyOptionalize(value, a)
    case a: MigrationAction.ChangeFieldType  => applyChangeFieldType(value, a)
    case a: MigrationAction.RenameCase       => applyRenameCase(value, a)
    case a: MigrationAction.TransformCase    => applyTransformCase(value, a)
    case a: MigrationAction.TransformElements => applyTransformElements(value, a)
    case a: MigrationAction.TransformKeys    => applyTransformKeys(value, a)
    case a: MigrationAction.TransformValues  => applyTransformValues(value, a)
  }

  // ── Record actions ─────────────────────────────────────────────────

  private def applyAddField(
    value: DynamicValue,
    action: MigrationAction.AddField
  ): Either[MigrationError, DynamicValue] =
    navigateAndModifyRecord(value, action.at) { record =>
      val existing = record.fields
      var idx      = 0
      var duplicate = false
      while (idx < existing.length && !duplicate) {
        if (existing(idx)._1 == action.fieldName) duplicate = true
        idx += 1
      }
      if (duplicate) Left(MigrationError.atPath(s"Field '${action.fieldName}' already exists", action.at))
      else Right(DynamicValue.Record(existing :+ ((action.fieldName, action.default))))
    }

  private def applyDropField(
    value: DynamicValue,
    action: MigrationAction.DropField
  ): Either[MigrationError, DynamicValue] =
    navigateAndModifyRecord(value, action.at) { record =>
      val existing = record.fields
      val buf   = Vector.newBuilder[(String, DynamicValue)]
      var found = false
      var idx   = 0
      while (idx < existing.length) {
        val pair = existing(idx)
        if (pair._1 == action.fieldName) found = true
        else buf += pair
        idx += 1
      }
      if (!found) Left(MigrationError.atPath(s"Field '${action.fieldName}' not found", action.at))
      else Right(DynamicValue.Record(Chunk.from(buf.result())))
    }

  private def applyRenameField(
    value: DynamicValue,
    action: MigrationAction.RenameField
  ): Either[MigrationError, DynamicValue] =
    navigateAndModifyRecord(value, action.at) { record =>
      val existing = record.fields
      val updated  = new Array[(String, DynamicValue)](existing.length)
      var found    = false
      var idx      = 0
      while (idx < existing.length) {
        val pair = existing(idx)
        if (pair._1 == action.from) {
          updated(idx) = (action.to, pair._2)
          found = true
        } else {
          updated(idx) = pair
        }
        idx += 1
      }
      if (!found) Left(MigrationError.atPath(s"Field '${action.from}' not found for rename", action.at))
      else Right(DynamicValue.Record(Chunk.fromArray(updated)))
    }

  private def applyTransformValue(
    value: DynamicValue,
    action: MigrationAction.TransformValue
  ): Either[MigrationError, DynamicValue] = {
    // For constant replacement: the transform IS the new value
    val path = action.at
    if (path.nodes.isEmpty) Right(action.transform)
    else {
      val modified = value.modify(path)(_ => action.transform)
      Right(modified)
    }
  }

  private def applyMandate(
    value: DynamicValue,
    action: MigrationAction.Mandate
  ): Either[MigrationError, DynamicValue] =
    navigateAndModifyRecord(value, action.at) { record =>
      val existing = record.fields
      val updated  = new Array[(String, DynamicValue)](existing.length)
      var found    = false
      var idx      = 0
      while (idx < existing.length) {
        val pair = existing(idx)
        if (pair._1 == action.fieldName) {
          found = true
          pair._2 match {
            case DynamicValue.Null =>
              updated(idx) = (pair._1, action.default)
            // None is Variant("None", Record()) in ZIO Schema
            case DynamicValue.Variant("None", _) =>
              updated(idx) = (pair._1, action.default)
            // Some is Variant("Some", Record(("value", inner)))
            case DynamicValue.Variant("Some", rec: DynamicValue.Record) =>
              val inner = rec.fields.find(_._1 == "value").map(_._2).getOrElse(rec)
              updated(idx) = (pair._1, inner)
            case other =>
              updated(idx) = (pair._1, other)
          }
        } else {
          updated(idx) = pair
        }
        idx += 1
      }
      if (!found) Left(MigrationError.atPath(s"Field '${action.fieldName}' not found for mandate", action.at))
      else Right(DynamicValue.Record(Chunk.fromArray(updated)))
    }

  private def applyOptionalize(
    value: DynamicValue,
    action: MigrationAction.Optionalize
  ): Either[MigrationError, DynamicValue] =
    navigateAndModifyRecord(value, action.at) { record =>
      val existing = record.fields
      val updated  = new Array[(String, DynamicValue)](existing.length)
      var found    = false
      var idx      = 0
      while (idx < existing.length) {
        val pair = existing(idx)
        if (pair._1 == action.fieldName) {
          found = true
          pair._2 match {
            case DynamicValue.Null =>
              updated(idx) = (pair._1, DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty)))
            case inner =>
              val wrapped = DynamicValue.Record(Chunk.single(("value", inner)))
              updated(idx) = (pair._1, DynamicValue.Variant("Some", wrapped))
          }
        } else {
          updated(idx) = pair
        }
        idx += 1
      }
      if (!found) Left(MigrationError.atPath(s"Field '${action.fieldName}' not found for optionalize", action.at))
      else Right(DynamicValue.Record(Chunk.fromArray(updated)))
    }

  private def applyChangeFieldType(
    value: DynamicValue,
    action: MigrationAction.ChangeFieldType
  ): Either[MigrationError, DynamicValue] =
    navigateAndModifyRecord(value, action.at) { record =>
      val existing           = record.fields
      val updated            = new Array[(String, DynamicValue)](existing.length)
      var found              = false
      var conversionError: MigrationError = null
      var idx                = 0
      while (idx < existing.length && (conversionError eq null)) {
        val pair = existing(idx)
        if (pair._1 == action.fieldName) {
          found = true
          convertPrimitive(pair._2, action.converter) match {
            case Right(converted) => updated(idx) = (pair._1, converted)
            case Left(err)        => conversionError = err
          }
        } else {
          updated(idx) = pair
        }
        idx += 1
      }
      if (conversionError ne null) Left(conversionError)
      else if (!found) Left(MigrationError.atPath(s"Field '${action.fieldName}' not found for type change", action.at))
      else Right(DynamicValue.Record(Chunk.fromArray(updated)))
    }

  // ── Enum actions ───────────────────────────────────────────────────

  private def applyRenameCase(
    value: DynamicValue,
    action: MigrationAction.RenameCase
  ): Either[MigrationError, DynamicValue] = value match {
    case v: DynamicValue.Variant if action.at.nodes.isEmpty =>
      if (v.caseNameValue == action.from)
        Right(DynamicValue.Variant(action.to, v.value))
      else
        Right(v) // different case, leave unchanged
    case _ if action.at.nodes.nonEmpty =>
      val modified = value.modify(action.at) {
        case v: DynamicValue.Variant if v.caseNameValue == action.from =>
          DynamicValue.Variant(action.to, v.value)
        case other => other
      }
      Right(modified)
    case _ =>
      Left(MigrationError.atPath("Expected Variant for case rename", action.at))
  }

  private def applyTransformCase(
    value: DynamicValue,
    action: MigrationAction.TransformCase
  ): Either[MigrationError, DynamicValue] = {
    def transformPayload(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
      case v: DynamicValue.Variant if v.caseNameValue == action.caseName =>
        val sub = new DynamicMigration(action.actions)
        sub(v.value).map(transformed => DynamicValue.Variant(v.caseNameValue, transformed))
      case _ => Right(dv) // not the target case, leave alone
    }

    if (action.at.nodes.isEmpty) transformPayload(value)
    else {
      var error: MigrationError = null
      val modified = value.modify(action.at) { dv =>
        transformPayload(dv) match {
          case Right(r) => r
          case Left(e)  => error = e; dv
        }
      }
      if (error ne null) Left(error) else Right(modified)
    }
  }

  // ── Collection actions ─────────────────────────────────────────────

  private def applyTransformElements(
    value: DynamicValue,
    action: MigrationAction.TransformElements
  ): Either[MigrationError, DynamicValue] = {
    val sub = new DynamicMigration(action.actions)

    def transformSeq(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
      case seq: DynamicValue.Sequence =>
        val builder  = Vector.newBuilder[DynamicValue]
        var idx      = 0
        var err: MigrationError = null
        while (idx < seq.elements.length && (err eq null)) {
          sub(seq.elements(idx)) match {
            case Right(elem) => builder += elem
            case Left(e)     => err = e
          }
          idx += 1
        }
        if (err ne null) Left(err)
        else Right(DynamicValue.Sequence(Chunk.from(builder.result())))
      case _ => Left(MigrationError.atPath("Expected Sequence for element transform", action.at))
    }

    if (action.at.nodes.isEmpty) transformSeq(value)
    else {
      var error: MigrationError = null
      val modified = value.modify(action.at) { dv =>
        transformSeq(dv) match {
          case Right(r) => r
          case Left(e)  => error = e; dv
        }
      }
      if (error ne null) Left(error) else Right(modified)
    }
  }

  private def applyTransformKeys(
    value: DynamicValue,
    action: MigrationAction.TransformKeys
  ): Either[MigrationError, DynamicValue] = {
    val sub = new DynamicMigration(action.actions)

    def transformMap(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
      case m: DynamicValue.Map =>
        val builder = Vector.newBuilder[(DynamicValue, DynamicValue)]
        var idx     = 0
        var err: MigrationError = null
        while (idx < m.entries.length && (err eq null)) {
          val entry = m.entries(idx)
          sub(entry._1) match {
            case Right(newKey) => builder += ((newKey, entry._2))
            case Left(e)       => err = e
          }
          idx += 1
        }
        if (err ne null) Left(err)
        else Right(DynamicValue.Map(Chunk.from(builder.result())))
      case _ => Left(MigrationError.atPath("Expected Map for key transform", action.at))
    }

    if (action.at.nodes.isEmpty) transformMap(value)
    else {
      var error: MigrationError = null
      val modified = value.modify(action.at) { dv =>
        transformMap(dv) match {
          case Right(r) => r
          case Left(e)  => error = e; dv
        }
      }
      if (error ne null) Left(error) else Right(modified)
    }
  }

  private def applyTransformValues(
    value: DynamicValue,
    action: MigrationAction.TransformValues
  ): Either[MigrationError, DynamicValue] = {
    val sub = new DynamicMigration(action.actions)

    def transformMap(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
      case m: DynamicValue.Map =>
        val builder = Vector.newBuilder[(DynamicValue, DynamicValue)]
        var idx     = 0
        var err: MigrationError = null
        while (idx < m.entries.length && (err eq null)) {
          val entry = m.entries(idx)
          sub(entry._2) match {
            case Right(newVal) => builder += ((entry._1, newVal))
            case Left(e)       => err = e
          }
          idx += 1
        }
        if (err ne null) Left(err)
        else Right(DynamicValue.Map(Chunk.from(builder.result())))
      case _ => Left(MigrationError.atPath("Expected Map for value transform", action.at))
    }

    if (action.at.nodes.isEmpty) transformMap(value)
    else {
      var error: MigrationError = null
      val modified = value.modify(action.at) { dv =>
        transformMap(dv) match {
          case Right(r) => r
          case Left(e)  => error = e; dv
        }
      }
      if (error ne null) Left(error) else Right(modified)
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────

  /**
   * Navigate to the record at `path` (or treat root as a record) and apply
   * the modification function `f`.
   */
  private def navigateAndModifyRecord(value: DynamicValue, path: DynamicOptic)(
    f: DynamicValue.Record => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {
    if (path.nodes.isEmpty) {
      value match {
        case r: DynamicValue.Record => f(r)
        case _                      => Left(MigrationError.atPath("Expected Record", path))
      }
    } else {
      var error: MigrationError = null
      val modified = value.modify(path) {
        case r: DynamicValue.Record =>
          f(r) match {
            case Right(v) => v
            case Left(e)  => error = e; r
          }
        case other =>
          error = MigrationError.atPath("Expected Record", path)
          other
      }
      if (error ne null) Left(error) else Right(modified)
    }
  }

  /**
   * Primitive type conversion. The `converter` DynamicValue encodes the target
   * type name as a string primitive, e.g. Primitive(String("Long")) means
   * "convert the source to Long".
   */
  private def convertPrimitive(
    source: DynamicValue,
    converter: DynamicValue
  ): Either[MigrationError, DynamicValue] = converter match {
    case DynamicValue.Primitive(nameVal: PrimitiveValue.String) =>
      val targetType = nameVal.value
      source match {
        case DynamicValue.Primitive(pv) =>
          convertPrimitiveValue(pv, targetType) match {
            case Some(converted) => Right(DynamicValue.Primitive(converted))
            case None =>
              Left(MigrationError(s"Cannot convert ${pv.getClass.getSimpleName} to $targetType"))
          }
        case _ => Left(MigrationError("ChangeFieldType only applies to primitive values"))
      }
    case _ => Left(MigrationError("Converter must be a string naming the target type"))
  }

  private def convertPrimitiveValue(pv: PrimitiveValue, targetType: String): Option[PrimitiveValue] =
    targetType match {
      case "String" => Some(new PrimitiveValue.String(primitiveToString(pv)))
      case "Int" =>
        primitiveToLong(pv).map(l => new PrimitiveValue.Int(l.toInt))
      case "Long" =>
        primitiveToLong(pv).map(l => new PrimitiveValue.Long(l))
      case "Double" =>
        primitiveToDouble(pv).map(d => new PrimitiveValue.Double(d))
      case "Float" =>
        primitiveToDouble(pv).map(d => new PrimitiveValue.Float(d.toFloat))
      case "Short" =>
        primitiveToLong(pv).map(l => new PrimitiveValue.Short(l.toShort))
      case "Byte" =>
        primitiveToLong(pv).map(l => new PrimitiveValue.Byte(l.toByte))
      case "Boolean" =>
        pv match {
          case v: PrimitiveValue.Boolean => Some(v)
          case v: PrimitiveValue.String =>
            v.value.toLowerCase match {
              case "true"  => Some(new PrimitiveValue.Boolean(true))
              case "false" => Some(new PrimitiveValue.Boolean(false))
              case _       => None
            }
          case _ => None
        }
      case "BigDecimal" =>
        primitiveToDouble(pv).map(d => new PrimitiveValue.BigDecimal(BigDecimal(d)))
      case "BigInt" =>
        primitiveToLong(pv).map(l => new PrimitiveValue.BigInt(BigInt(l)))
      case _ => None
    }

  private def primitiveToString(pv: PrimitiveValue): String = pv match {
    case v: PrimitiveValue.String     => v.value
    case v: PrimitiveValue.Int        => v.value.toString
    case v: PrimitiveValue.Long       => v.value.toString
    case v: PrimitiveValue.Double     => v.value.toString
    case v: PrimitiveValue.Float      => v.value.toString
    case v: PrimitiveValue.Short      => v.value.toString
    case v: PrimitiveValue.Byte       => v.value.toString
    case v: PrimitiveValue.Boolean    => v.value.toString
    case v: PrimitiveValue.BigDecimal => v.value.toString
    case v: PrimitiveValue.BigInt     => v.value.toString
    case v: PrimitiveValue.Char       => v.value.toString
    case _                            => pv.toString
  }

  private def primitiveToLong(pv: PrimitiveValue): Option[Long] = pv match {
    case v: PrimitiveValue.Int        => Some(v.value.toLong)
    case v: PrimitiveValue.Long       => Some(v.value)
    case v: PrimitiveValue.Short      => Some(v.value.toLong)
    case v: PrimitiveValue.Byte       => Some(v.value.toLong)
    case v: PrimitiveValue.Double     => Some(v.value.toLong)
    case v: PrimitiveValue.Float      => Some(v.value.toLong)
    case v: PrimitiveValue.BigInt     => Some(v.value.toLong)
    case v: PrimitiveValue.BigDecimal => Some(v.value.toLong)
    case v: PrimitiveValue.String =>
      try Some(v.value.toLong)
      catch { case _: NumberFormatException => None }
    case _ => None
  }

  private def primitiveToDouble(pv: PrimitiveValue): Option[Double] = pv match {
    case v: PrimitiveValue.Int        => Some(v.value.toDouble)
    case v: PrimitiveValue.Long       => Some(v.value.toDouble)
    case v: PrimitiveValue.Short      => Some(v.value.toDouble)
    case v: PrimitiveValue.Byte       => Some(v.value.toDouble)
    case v: PrimitiveValue.Double     => Some(v.value)
    case v: PrimitiveValue.Float      => Some(v.value.toDouble)
    case v: PrimitiveValue.BigDecimal => Some(v.value.toDouble)
    case v: PrimitiveValue.BigInt     => Some(v.value.toDouble)
    case v: PrimitiveValue.String =>
      try Some(v.value.toDouble)
      catch { case _: NumberFormatException => None }
    case _ => None
  }
}
