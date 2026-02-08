package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * An untyped migration that operates on [[DynamicValue]]. Migrations are
 * serializable and can be composed, reversed, and applied to transform data
 * between schema versions.
 *
 * `DynamicMigration` follows the same dual-layer architecture as
 * [[zio.blocks.schema.patch.DynamicPatch]]: an untyped serializable core that a
 * typed wrapper can build upon.
 *
 * '''Laws:'''
 *   - '''Identity:''' `DynamicMigration.empty.apply(v) == Right(v)`
 *   - '''Associativity:''' `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)` (chunk
 *     concatenation is associative)
 *   - '''Double-reverse:''' `m.reverse.reverse == m`
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  /**
   * Apply this migration to a [[DynamicValue]].
   *
   * Folds over `actions` sequentially, threading the current `DynamicValue`
   * through each action. Short-circuits on the first error.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current: DynamicValue = value
    val len                   = actions.size
    var idx                   = 0
    while (idx < len) {
      DynamicMigration.applyAction(current, actions(idx)) match {
        case Right(updated) => current = updated
        case l              => return l
      }
      idx += 1
    }
    Right(current)
  }

  /**
   * Compose two migrations. The result applies this migration first, then
   * `that` migration.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /**
   * Produce the structural inverse of this migration. Reverses the order of
   * actions and reverses each individual action.
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  /** Check if this migration is empty (no actions). */
  def isEmpty: Boolean = actions.isEmpty
}

object DynamicMigration {

  /** Empty migration — identity element for composition. */
  val empty: DynamicMigration = DynamicMigration(Chunk.empty)

  // ─────────────────────────────────────────────────────────────────────────
  // Action Interpreter
  // ─────────────────────────────────────────────────────────────────────────

  private def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] =
    action match {
      case MigrationAction.AddField(at, fieldName, defaultExpr) =>
        navigateToRecord(value, at) { (record, path) =>
          if (hasField(record.fields, fieldName))
            Left(MigrationError.FieldAlreadyExists(path, fieldName))
          else
            evalExpr(defaultExpr, value, path).map { defaultVal =>
              DynamicValue.Record(record.fields.appended((fieldName, defaultVal)))
            }
        }

      case MigrationAction.DropField(at, fieldName, _) =>
        navigateToRecord(value, at) { (record, path) =>
          val idx = fieldIndex(record.fields, fieldName)
          if (idx < 0) Left(MigrationError.FieldNotFound(path, fieldName))
          else Right(DynamicValue.Record(record.fields.filterNot(_._1 == fieldName)))
        }

      case MigrationAction.Rename(at, fromName, toName) =>
        navigateToRecord(value, at) { (record, path) =>
          val idx = fieldIndex(record.fields, fromName)
          if (idx < 0) Left(MigrationError.FieldNotFound(path, fromName))
          else if (hasField(record.fields, toName))
            Left(MigrationError.FieldAlreadyExists(path, toName))
          else {
            val newFields = record.fields.map { case (name, v) =>
              if (name == fromName) (toName, v) else (name, v)
            }
            Right(DynamicValue.Record(newFields))
          }
        }

      case MigrationAction.TransformValue(at, fieldName, expr, _) =>
        navigateToRecord(value, at) { (record, path) =>
          val idx = fieldIndex(record.fields, fieldName)
          if (idx < 0) Left(MigrationError.FieldNotFound(path, fieldName))
          else {
            val fieldVal = record.fields(idx)._2
            evalExpr(expr, fieldVal, path.field(fieldName)).map { newVal =>
              DynamicValue.Record(record.fields.updated(idx, (fieldName, newVal)))
            }
          }
        }

      case MigrationAction.Mandate(at, fieldName, defaultExpr) =>
        navigateToRecord(value, at) { (record, path) =>
          val idx = fieldIndex(record.fields, fieldName)
          if (idx < 0) Left(MigrationError.FieldNotFound(path, fieldName))
          else {
            val fieldVal  = record.fields(idx)._2
            val fieldPath = path.field(fieldName)
            unwrapOption(fieldVal, fieldPath, defaultExpr, value).map { unwrapped =>
              DynamicValue.Record(record.fields.updated(idx, (fieldName, unwrapped)))
            }
          }
        }

      case MigrationAction.Optionalize(at, fieldName, _) =>
        navigateToRecord(value, at) { (record, path) =>
          val idx = fieldIndex(record.fields, fieldName)
          if (idx < 0) Left(MigrationError.FieldNotFound(path, fieldName))
          else {
            val fieldVal = record.fields(idx)._2
            val wrapped  = DynamicValue.Variant("Some", DynamicValue.Record(Chunk(("value", fieldVal))))
            Right(DynamicValue.Record(record.fields.updated(idx, (fieldName, wrapped))))
          }
        }

      case MigrationAction.Join(at, sourceFields, targetField, joinExpr, _) =>
        navigateToRecord(value, at) { (record, path) =>
          // Verify all source fields exist
          val missingIdx = sourceFields.indexWhere(name => !hasField(record.fields, name))
          if (missingIdx >= 0)
            Left(MigrationError.FieldNotFound(path, sourceFields(missingIdx)))
          else if (hasField(record.fields, targetField) && !sourceFields.contains(targetField))
            Left(MigrationError.FieldAlreadyExists(path, targetField))
          else {
            // Evaluate joinExpr using the record as context
            evalExpr(joinExpr, DynamicValue.Record(record.fields), path).map { joinedVal =>
              // Remove source fields, add target field
              val filtered = record.fields.filterNot { case (name, _) => sourceFields.contains(name) }
              DynamicValue.Record(filtered.appended((targetField, joinedVal)))
            }
          }
        }

      case MigrationAction.Split(at, sourceField, targetExprs, _) =>
        navigateToRecord(value, at) { (record, path) =>
          val idx = fieldIndex(record.fields, sourceField)
          if (idx < 0) Left(MigrationError.FieldNotFound(path, sourceField))
          else {
            val sourceVal = record.fields(idx)._2
            // Evaluate each target expr against the source value
            val results                                = new Array[(String, DynamicValue)](targetExprs.size)
            var i                                      = 0
            var error: Either[MigrationError, Nothing] = null
            while (i < targetExprs.size && error == null) {
              val (targetName, targetExpr) = targetExprs(i)
              evalExpr(targetExpr, sourceVal, path.field(sourceField)) match {
                case Right(v) => results(i) = (targetName, v)
                case Left(e)  => error = Left(e)
              }
              i += 1
            }
            if (error != null) error
            else {
              // Remove source field, add all target fields
              val filtered = record.fields.filterNot(_._1 == sourceField)
              Right(DynamicValue.Record(filtered ++ Chunk.from(results.toSeq)))
            }
          }
        }

      case MigrationAction.ChangeType(at, fieldName, coercion, _) =>
        navigateToRecord(value, at) { (record, path) =>
          val idx = fieldIndex(record.fields, fieldName)
          if (idx < 0) Left(MigrationError.FieldNotFound(path, fieldName))
          else {
            val fieldVal = record.fields(idx)._2
            evalExpr(coercion, fieldVal, path.field(fieldName)).map { newVal =>
              DynamicValue.Record(record.fields.updated(idx, (fieldName, newVal)))
            }
          }
        }

      case MigrationAction.RenameCase(at, fromCase, toCase) =>
        navigateToVariant(value, at) { (variant, _) =>
          if (variant.caseNameValue == fromCase)
            Right(DynamicValue.Variant(toCase, variant.value))
          else
            Right(variant)
        }

      case MigrationAction.TransformCase(at, caseName, innerActions) =>
        navigateToVariant(value, at) { (variant, _) =>
          if (variant.caseNameValue == caseName) {
            val innerMigration = DynamicMigration(innerActions)
            innerMigration(variant.value) match {
              case Right(transformed) => Right(DynamicValue.Variant(caseName, transformed))
              case Left(err)          => Left(err)
            }
          } else
            Right(variant)
        }

      case MigrationAction.TransformElements(at, expr, _) =>
        navigateToSequence(value, at) { (seq, path) =>
          val elements                               = seq.elements
          val len                                    = elements.length
          val newElems                               = new Array[DynamicValue](len)
          var i                                      = 0
          var error: Either[MigrationError, Nothing] = null
          while (i < len && error == null) {
            evalExpr(expr, elements(i), path.at(i)) match {
              case Right(v) => newElems(i) = v
              case Left(e)  => error = Left(e)
            }
            i += 1
          }
          if (error != null) error
          else Right(DynamicValue.Sequence(Chunk.from(newElems.toSeq)))
        }

      case MigrationAction.TransformKeys(at, expr, _) =>
        navigateToMap(value, at) { (m, path) =>
          val entries                                = m.entries
          val len                                    = entries.length
          val newEntries                             = new Array[(DynamicValue, DynamicValue)](len)
          var i                                      = 0
          var error: Either[MigrationError, Nothing] = null
          while (i < len && error == null) {
            val (k, v) = entries(i)
            evalExpr(expr, k, path) match {
              case Right(newK) => newEntries(i) = (newK, v)
              case Left(e)     => error = Left(e)
            }
            i += 1
          }
          if (error != null) error
          else Right(DynamicValue.Map(Chunk.from(newEntries.toSeq)))
        }

      case MigrationAction.TransformValues(at, expr, _) =>
        navigateToMap(value, at) { (m, path) =>
          val entries                                = m.entries
          val len                                    = entries.length
          val newEntries                             = new Array[(DynamicValue, DynamicValue)](len)
          var i                                      = 0
          var error: Either[MigrationError, Nothing] = null
          while (i < len && error == null) {
            val (k, v) = entries(i)
            evalExpr(expr, v, path) match {
              case Right(newV) => newEntries(i) = (k, newV)
              case Left(e)     => error = Left(e)
            }
            i += 1
          }
          if (error != null) error
          else Right(DynamicValue.Map(Chunk.from(newEntries.toSeq)))
        }
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation Helpers
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Navigate to a Record at the given path, apply a transformation function,
   * and reconstruct the top-level DynamicValue.
   */
  private def navigateToRecord(
    root: DynamicValue,
    at: DynamicOptic
  )(
    f: (DynamicValue.Record, DynamicOptic) => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    navigateAndTransform(root, at) { (dv, path) =>
      dv match {
        case r: DynamicValue.Record => f(r, path)
        case _                      => Left(MigrationError.TypeMismatch(path, "Record", dvTypeName(dv)))
      }
    }

  /**
   * Navigate to a Variant at the given path, apply a transformation function,
   * and reconstruct the top-level DynamicValue.
   */
  private def navigateToVariant(
    root: DynamicValue,
    at: DynamicOptic
  )(
    f: (DynamicValue.Variant, DynamicOptic) => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    navigateAndTransform(root, at) { (dv, path) =>
      dv match {
        case v: DynamicValue.Variant => f(v, path)
        case _                       => Left(MigrationError.TypeMismatch(path, "Variant", dvTypeName(dv)))
      }
    }

  /**
   * Navigate to a Sequence at the given path, apply a transformation function,
   * and reconstruct the top-level DynamicValue.
   */
  private def navigateToSequence(
    root: DynamicValue,
    at: DynamicOptic
  )(
    f: (DynamicValue.Sequence, DynamicOptic) => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    navigateAndTransform(root, at) { (dv, path) =>
      dv match {
        case s: DynamicValue.Sequence => f(s, path)
        case _                        => Left(MigrationError.TypeMismatch(path, "Sequence", dvTypeName(dv)))
      }
    }

  /**
   * Navigate to a Map at the given path, apply a transformation function, and
   * reconstruct the top-level DynamicValue.
   */
  private def navigateToMap(
    root: DynamicValue,
    at: DynamicOptic
  )(
    f: (DynamicValue.Map, DynamicOptic) => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    navigateAndTransform(root, at) { (dv, path) =>
      dv match {
        case m: DynamicValue.Map => f(m, path)
        case _                   => Left(MigrationError.TypeMismatch(path, "Map", dvTypeName(dv)))
      }
    }

  /**
   * Core navigation: walk the optic path from root to target. If the path is
   * empty (root optic), apply `f` directly to root. Otherwise, recursively
   * navigate through the DynamicValue structure, apply `f` at the target, and
   * reconstruct the tree on the way back up.
   */
  private def navigateAndTransform(
    root: DynamicValue,
    at: DynamicOptic
  )(
    f: (DynamicValue, DynamicOptic) => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {
    val nodes = at.nodes
    if (nodes.isEmpty) f(root, at)
    else navigateImpl(root, nodes, 0, at, f)
  }

  private def navigateImpl(
    dv: DynamicValue,
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    fullPath: DynamicOptic,
    f: (DynamicValue, DynamicOptic) => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {
    val node   = nodes(idx)
    val isLast = idx == nodes.length - 1

    node match {
      case DynamicOptic.Node.Field(name) =>
        dv match {
          case r: DynamicValue.Record =>
            val fieldIdx = fieldIndex(r.fields, name)
            if (fieldIdx < 0)
              Left(MigrationError.NavigationFailure(fullPath, s"Field '$name' not found in Record"))
            else {
              val (fieldName, fieldValue) = r.fields(fieldIdx)
              val result                  =
                if (isLast) f(fieldValue, fullPath)
                else navigateImpl(fieldValue, nodes, idx + 1, fullPath, f)
              result.map(newVal => DynamicValue.Record(r.fields.updated(fieldIdx, (fieldName, newVal))))
            }
          case _ =>
            Left(MigrationError.NavigationFailure(fullPath, s"Expected Record but got ${dvTypeName(dv)}"))
        }

      case DynamicOptic.Node.Case(name) =>
        dv match {
          case v: DynamicValue.Variant if v.caseNameValue == name =>
            val result =
              if (isLast) f(v.value, fullPath)
              else navigateImpl(v.value, nodes, idx + 1, fullPath, f)
            result.map(newVal => DynamicValue.Variant(name, newVal))
          case v: DynamicValue.Variant =>
            Left(
              MigrationError.NavigationFailure(fullPath, s"Expected case '$name' but got '${v.caseNameValue}'")
            )
          case _ =>
            Left(MigrationError.NavigationFailure(fullPath, s"Expected Variant but got ${dvTypeName(dv)}"))
        }

      case DynamicOptic.Node.AtIndex(i) =>
        dv match {
          case s: DynamicValue.Sequence if i >= 0 && i < s.elements.length =>
            val result =
              if (isLast) f(s.elements(i), fullPath)
              else navigateImpl(s.elements(i), nodes, idx + 1, fullPath, f)
            result.map(newVal => DynamicValue.Sequence(s.elements.updated(i, newVal)))
          case _: DynamicValue.Sequence =>
            Left(MigrationError.NavigationFailure(fullPath, s"Index $i out of bounds"))
          case _ =>
            Left(MigrationError.NavigationFailure(fullPath, s"Expected Sequence but got ${dvTypeName(dv)}"))
        }

      case DynamicOptic.Node.AtMapKey(key) =>
        dv match {
          case m: DynamicValue.Map =>
            val entryIdx = m.entries.indexWhere(_._1 == key)
            if (entryIdx < 0)
              Left(MigrationError.NavigationFailure(fullPath, "Key not found in Map"))
            else {
              val (k, v) = m.entries(entryIdx)
              val result =
                if (isLast) f(v, fullPath)
                else navigateImpl(v, nodes, idx + 1, fullPath, f)
              result.map(newVal => DynamicValue.Map(m.entries.updated(entryIdx, (k, newVal))))
            }
          case _ =>
            Left(MigrationError.NavigationFailure(fullPath, s"Expected Map but got ${dvTypeName(dv)}"))
        }

      case DynamicOptic.Node.Wrapped =>
        val result =
          if (isLast) f(dv, fullPath)
          else navigateImpl(dv, nodes, idx + 1, fullPath, f)
        result

      case _ =>
        Left(
          MigrationError.NavigationFailure(
            fullPath,
            s"Unsupported optic node type for migration: ${node.getClass.getSimpleName}"
          )
        )
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Option Unwrapping (Mandate)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Unwrap an optional value. If it is a `Variant("Some", Record(("value",
   * inner)))`, extract `inner`. If it is a `Variant("None", _)` or
   * `Record.empty` (representing None), evaluate the default expression. If it
   * is a `DynamicValue.Null`, evaluate the default expression.
   */
  private def unwrapOption(
    dv: DynamicValue,
    path: DynamicOptic,
    defaultExpr: MigrationExpr,
    rootContext: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    dv match {
      case DynamicValue.Variant("Some", inner) =>
        // Check for Record(("value", x)) pattern
        inner match {
          case r: DynamicValue.Record if r.fields.length == 1 && r.fields(0)._1 == "value" =>
            Right(r.fields(0)._2)
          case _ => Right(inner)
        }
      case DynamicValue.Variant("None", _) =>
        evalExpr(defaultExpr, rootContext, path)
      case DynamicValue.Null =>
        evalExpr(defaultExpr, rootContext, path)
      case r: DynamicValue.Record if r.fields.isEmpty =>
        evalExpr(defaultExpr, rootContext, path)
      case _ =>
        // Already a non-optional value; pass through
        Right(dv)
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Expression Evaluator
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Evaluate a [[MigrationExpr]] against a `DynamicValue` context. The context
   * is used for `FieldRef` resolution. Returns either a `MigrationError` or the
   * computed `DynamicValue`.
   */
  private[migration] def evalExpr(
    expr: MigrationExpr,
    context: DynamicValue,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    expr match {
      case MigrationExpr.Literal(value) =>
        Right(value)

      case MigrationExpr.DefaultValue(value) =>
        Right(value)

      case MigrationExpr.FieldRef(optic) =>
        val selection = context.get(optic)
        val chunk     = selection.toChunk
        if (chunk.isEmpty)
          Left(MigrationError.NavigationFailure(path, s"FieldRef path $optic not found in context"))
        else
          Right(chunk(0))

      case MigrationExpr.Concat(left, right) =>
        for {
          l   <- evalExpr(left, context, path)
          r   <- evalExpr(right, context, path)
          res <- concatStrings(l, r, path)
        } yield res

      case MigrationExpr.Add(left, right) =>
        evalArithmetic(left, right, context, path, "Add")(
          addInt,
          addLong,
          addFloat,
          addDouble,
          addBigInt,
          addBigDecimal
        )

      case MigrationExpr.Subtract(left, right) =>
        evalArithmetic(left, right, context, path, "Subtract")(
          subInt,
          subLong,
          subFloat,
          subDouble,
          subBigInt,
          subBigDecimal
        )

      case MigrationExpr.Multiply(left, right) =>
        evalArithmetic(left, right, context, path, "Multiply")(
          mulInt,
          mulLong,
          mulFloat,
          mulDouble,
          mulBigInt,
          mulBigDecimal
        )

      case MigrationExpr.Divide(left, right) =>
        evalArithmetic(left, right, context, path, "Divide")(
          divInt,
          divLong,
          divFloat,
          divDouble,
          divBigInt,
          divBigDecimal
        )

      case MigrationExpr.Coerce(inner, targetType) =>
        evalExpr(inner, context, path).flatMap(v => coerceValue(v, targetType, path))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // String Operations
  // ─────────────────────────────────────────────────────────────────────────

  private def concatStrings(
    left: DynamicValue,
    right: DynamicValue,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    (extractString(left), extractString(right)) match {
      case (Some(l), Some(r)) => Right(DynamicValue.string(l + r))
      case _                  =>
        Left(MigrationError.TypeMismatch(path, "String", s"${dvTypeName(left)}, ${dvTypeName(right)}"))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Arithmetic Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private def evalArithmetic(
    left: MigrationExpr,
    right: MigrationExpr,
    context: DynamicValue,
    path: DynamicOptic,
    opName: String
  )(
    intOp: (Int, Int) => Either[MigrationError, PrimitiveValue],
    longOp: (Long, Long) => Either[MigrationError, PrimitiveValue],
    floatOp: (Float, Float) => Either[MigrationError, PrimitiveValue],
    doubleOp: (Double, Double) => Either[MigrationError, PrimitiveValue],
    bigIntOp: (BigInt, BigInt) => Either[MigrationError, PrimitiveValue],
    bigDecimalOp: (BigDecimal, BigDecimal) => Either[MigrationError, PrimitiveValue]
  ): Either[MigrationError, DynamicValue] =
    for {
      l   <- evalExpr(left, context, path)
      r   <- evalExpr(right, context, path)
      res <- numericBinaryOp(l, r, path, opName)(intOp, longOp, floatOp, doubleOp, bigIntOp, bigDecimalOp)
    } yield res

  /**
   * Perform a binary numeric operation with type promotion. Promotion order:
   * Byte/Short/Int -> Long -> Float -> Double -> BigInt -> BigDecimal
   */
  private def numericBinaryOp(
    left: DynamicValue,
    right: DynamicValue,
    path: DynamicOptic,
    opName: String
  )(
    intOp: (Int, Int) => Either[MigrationError, PrimitiveValue],
    longOp: (Long, Long) => Either[MigrationError, PrimitiveValue],
    floatOp: (Float, Float) => Either[MigrationError, PrimitiveValue],
    doubleOp: (Double, Double) => Either[MigrationError, PrimitiveValue],
    bigIntOp: (BigInt, BigInt) => Either[MigrationError, PrimitiveValue],
    bigDecimalOp: (BigDecimal, BigDecimal) => Either[MigrationError, PrimitiveValue]
  ): Either[MigrationError, DynamicValue] = {
    val lp = extractPrimitive(left)
    val rp = extractPrimitive(right)
    (lp, rp) match {
      case (Some(lpv), Some(rpv)) =>
        promoteAndCompute(lpv, rpv, path, opName)(intOp, longOp, floatOp, doubleOp, bigIntOp, bigDecimalOp)
          .map(pv => DynamicValue.Primitive(pv))
      case _ =>
        Left(
          MigrationError.TypeMismatch(
            path,
            "numeric Primitive",
            s"${dvTypeName(left)}, ${dvTypeName(right)}"
          )
        )
    }
  }

  private def promoteAndCompute(
    left: PrimitiveValue,
    right: PrimitiveValue,
    path: DynamicOptic,
    opName: String
  )(
    intOp: (Int, Int) => Either[MigrationError, PrimitiveValue],
    longOp: (Long, Long) => Either[MigrationError, PrimitiveValue],
    floatOp: (Float, Float) => Either[MigrationError, PrimitiveValue],
    doubleOp: (Double, Double) => Either[MigrationError, PrimitiveValue],
    bigIntOp: (BigInt, BigInt) => Either[MigrationError, PrimitiveValue],
    bigDecimalOp: (BigDecimal, BigDecimal) => Either[MigrationError, PrimitiveValue]
  ): Either[MigrationError, PrimitiveValue] = {
    val lNum = toNumeric(left)
    val rNum = toNumeric(right)
    (lNum, rNum) match {
      case (Some(ln), Some(rn)) =>
        val promoted = promoteType(ln, rn)
        promoted match {
          case NumericType.IntType =>
            intOp(ln.toInt, rn.toInt)
          case NumericType.LongType =>
            longOp(ln.toLong, rn.toLong)
          case NumericType.FloatType =>
            floatOp(ln.toFloat, rn.toFloat)
          case NumericType.DoubleType =>
            doubleOp(ln.toDouble, rn.toDouble)
          case NumericType.BigIntType =>
            bigIntOp(ln.toBigInt, rn.toBigInt)
          case NumericType.BigDecimalType =>
            bigDecimalOp(ln.toBigDecimal, rn.toBigDecimal)
        }
      case _ =>
        Left(
          MigrationError.CustomError(
            path,
            s"$opName requires numeric operands, got ${left.getClass.getSimpleName} and ${right.getClass.getSimpleName}"
          )
        )
    }
  }

  // Numeric wrapper for type promotion
  private sealed trait NumericType
  private object NumericType {
    case object IntType        extends NumericType
    case object LongType       extends NumericType
    case object FloatType      extends NumericType
    case object DoubleType     extends NumericType
    case object BigIntType     extends NumericType
    case object BigDecimalType extends NumericType
  }

  private case class NumericVal(
    numType: NumericType,
    toInt: Int,
    toLong: Long,
    toFloat: Float,
    toDouble: Double,
    toBigInt: BigInt,
    toBigDecimal: BigDecimal
  )

  private def toNumeric(pv: PrimitiveValue): Option[NumericVal] = pv match {
    case PrimitiveValue.Byte(v) =>
      Some(
        NumericVal(NumericType.IntType, v.toInt, v.toLong, v.toFloat, v.toDouble, BigInt(v.toInt), BigDecimal(v.toInt))
      )
    case PrimitiveValue.Short(v) =>
      Some(
        NumericVal(NumericType.IntType, v.toInt, v.toLong, v.toFloat, v.toDouble, BigInt(v.toInt), BigDecimal(v.toInt))
      )
    case PrimitiveValue.Int(v) =>
      Some(NumericVal(NumericType.IntType, v, v.toLong, v.toFloat, v.toDouble, BigInt(v), BigDecimal(v)))
    case PrimitiveValue.Long(v) =>
      Some(NumericVal(NumericType.LongType, v.toInt, v, v.toFloat, v.toDouble, BigInt(v), BigDecimal(v)))
    case PrimitiveValue.Float(v) =>
      Some(NumericVal(NumericType.FloatType, v.toInt, v.toLong, v, v.toDouble, BigInt(v.toInt), BigDecimal(v.toDouble)))
    case PrimitiveValue.Double(v) =>
      Some(NumericVal(NumericType.DoubleType, v.toInt, v.toLong, v.toFloat, v, BigInt(v.toInt), BigDecimal(v)))
    case PrimitiveValue.BigInt(v) =>
      Some(NumericVal(NumericType.BigIntType, v.toInt, v.toLong, v.toFloat, v.toDouble, v, BigDecimal(v)))
    case PrimitiveValue.BigDecimal(v) =>
      Some(NumericVal(NumericType.BigDecimalType, v.toInt, v.toLong, v.toFloat, v.toDouble, v.toBigInt, v))
    case _ => None
  }

  /**
   * Promote to the wider of two numeric types. Promotion hierarchy: Int < Long
   * < BigInt < Float < Double < BigDecimal
   */
  private def promoteType(l: NumericVal, r: NumericVal): NumericType = {
    val lRank = typeRank(l.numType)
    val rRank = typeRank(r.numType)
    if (lRank >= rRank) l.numType else r.numType
  }

  private def typeRank(t: NumericType): Int = t match {
    case NumericType.IntType        => 0
    case NumericType.LongType       => 1
    case NumericType.FloatType      => 2
    case NumericType.DoubleType     => 3
    case NumericType.BigIntType     => 4
    case NumericType.BigDecimalType => 5
  }

  // Arithmetic operation implementations
  private val addInt: (Int, Int) => Either[MigrationError, PrimitiveValue]    = (a, b) => Right(PrimitiveValue.Int(a + b))
  private val addLong: (Long, Long) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.Long(a + b))
  private val addFloat: (Float, Float) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.Float(a + b))
  private val addDouble: (Double, Double) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.Double(a + b))
  private val addBigInt: (BigInt, BigInt) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.BigInt(a + b))
  private val addBigDecimal: (BigDecimal, BigDecimal) => Either[MigrationError, PrimitiveValue] =
    (a, b) => Right(PrimitiveValue.BigDecimal(a + b))

  private val subInt: (Int, Int) => Either[MigrationError, PrimitiveValue]    = (a, b) => Right(PrimitiveValue.Int(a - b))
  private val subLong: (Long, Long) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.Long(a - b))
  private val subFloat: (Float, Float) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.Float(a - b))
  private val subDouble: (Double, Double) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.Double(a - b))
  private val subBigInt: (BigInt, BigInt) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.BigInt(a - b))
  private val subBigDecimal: (BigDecimal, BigDecimal) => Either[MigrationError, PrimitiveValue] =
    (a, b) => Right(PrimitiveValue.BigDecimal(a - b))

  private val mulInt: (Int, Int) => Either[MigrationError, PrimitiveValue]    = (a, b) => Right(PrimitiveValue.Int(a * b))
  private val mulLong: (Long, Long) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.Long(a * b))
  private val mulFloat: (Float, Float) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.Float(a * b))
  private val mulDouble: (Double, Double) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.Double(a * b))
  private val mulBigInt: (BigInt, BigInt) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    Right(PrimitiveValue.BigInt(a * b))
  private val mulBigDecimal: (BigDecimal, BigDecimal) => Either[MigrationError, PrimitiveValue] =
    (a, b) => Right(PrimitiveValue.BigDecimal(a * b))

  private val divInt: (Int, Int) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    if (b == 0) Left(MigrationError.CustomError(DynamicOptic.root, "Division by zero"))
    else Right(PrimitiveValue.Int(a / b))

  private val divLong: (Long, Long) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    if (b == 0L) Left(MigrationError.CustomError(DynamicOptic.root, "Division by zero"))
    else Right(PrimitiveValue.Long(a / b))

  private val divFloat: (Float, Float) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    if (b == 0.0f) Left(MigrationError.CustomError(DynamicOptic.root, "Division by zero"))
    else Right(PrimitiveValue.Float(a / b))

  private val divDouble: (Double, Double) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    if (b == 0.0d) Left(MigrationError.CustomError(DynamicOptic.root, "Division by zero"))
    else Right(PrimitiveValue.Double(a / b))

  private val divBigInt: (BigInt, BigInt) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    if (b == BigInt(0)) Left(MigrationError.CustomError(DynamicOptic.root, "Division by zero"))
    else Right(PrimitiveValue.BigInt(a / b))

  private val divBigDecimal: (BigDecimal, BigDecimal) => Either[MigrationError, PrimitiveValue] = (a, b) =>
    if (b == BigDecimal(0)) Left(MigrationError.CustomError(DynamicOptic.root, "Division by zero"))
    else Right(PrimitiveValue.BigDecimal(a / b))

  // ─────────────────────────────────────────────────────────────────────────
  // Type Coercion
  // ─────────────────────────────────────────────────────────────────────────

  private def coerceValue(
    dv: DynamicValue,
    targetType: String,
    path: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    dv match {
      case DynamicValue.Primitive(pv) =>
        coercePrimitive(pv, targetType, path).map(DynamicValue.Primitive(_))
      case _ =>
        Left(MigrationError.InvalidCoercion(path, dvTypeName(dv), targetType))
    }

  private def coercePrimitive(
    pv: PrimitiveValue,
    targetType: String,
    path: DynamicOptic
  ): Either[MigrationError, PrimitiveValue] = {
    val fromType = pv.getClass.getSimpleName
    targetType match {
      case "String" =>
        Right(PrimitiveValue.String(primitiveToString(pv)))
      case "Int" =>
        toIntPrimitive(pv, path)
      case "Long" =>
        toLongPrimitive(pv, path)
      case "Float" =>
        toFloatPrimitive(pv, path)
      case "Double" =>
        toDoublePrimitive(pv, path)
      case "BigInt" =>
        toBigIntPrimitive(pv, path)
      case "BigDecimal" =>
        toBigDecimalPrimitive(pv, path)
      case "Boolean" =>
        toBooleanPrimitive(pv, path)
      case "Short" =>
        toShortPrimitive(pv, path)
      case "Byte" =>
        toBytePrimitive(pv, path)
      case _ =>
        Left(MigrationError.InvalidCoercion(path, fromType, targetType))
    }
  }

  private def primitiveToString(pv: PrimitiveValue): String = pv match {
    case PrimitiveValue.String(v)     => v
    case PrimitiveValue.Boolean(v)    => v.toString
    case PrimitiveValue.Byte(v)       => v.toString
    case PrimitiveValue.Short(v)      => v.toString
    case PrimitiveValue.Int(v)        => v.toString
    case PrimitiveValue.Long(v)       => v.toString
    case PrimitiveValue.Float(v)      => v.toString
    case PrimitiveValue.Double(v)     => v.toString
    case PrimitiveValue.Char(v)       => v.toString
    case PrimitiveValue.BigInt(v)     => v.toString
    case PrimitiveValue.BigDecimal(v) => v.toString
    case PrimitiveValue.Unit          => "()"
    case other                        => other.toString
  }

  private def toIntPrimitive(pv: PrimitiveValue, path: DynamicOptic): Either[MigrationError, PrimitiveValue] =
    pv match {
      case PrimitiveValue.Int(v)    => Right(PrimitiveValue.Int(v))
      case PrimitiveValue.Byte(v)   => Right(PrimitiveValue.Int(v.toInt))
      case PrimitiveValue.Short(v)  => Right(PrimitiveValue.Int(v.toInt))
      case PrimitiveValue.Long(v)   => Right(PrimitiveValue.Int(v.toInt))
      case PrimitiveValue.Float(v)  => Right(PrimitiveValue.Int(v.toInt))
      case PrimitiveValue.Double(v) => Right(PrimitiveValue.Int(v.toInt))
      case PrimitiveValue.BigInt(v) => Right(PrimitiveValue.Int(v.toInt))
      case PrimitiveValue.String(v) =>
        try Right(PrimitiveValue.Int(v.toInt))
        catch { case _: NumberFormatException => Left(MigrationError.InvalidCoercion(path, s"String($v)", "Int")) }
      case _ => Left(MigrationError.InvalidCoercion(path, pv.getClass.getSimpleName, "Int"))
    }

  private def toLongPrimitive(pv: PrimitiveValue, path: DynamicOptic): Either[MigrationError, PrimitiveValue] =
    pv match {
      case PrimitiveValue.Long(v)   => Right(PrimitiveValue.Long(v))
      case PrimitiveValue.Byte(v)   => Right(PrimitiveValue.Long(v.toLong))
      case PrimitiveValue.Short(v)  => Right(PrimitiveValue.Long(v.toLong))
      case PrimitiveValue.Int(v)    => Right(PrimitiveValue.Long(v.toLong))
      case PrimitiveValue.Float(v)  => Right(PrimitiveValue.Long(v.toLong))
      case PrimitiveValue.Double(v) => Right(PrimitiveValue.Long(v.toLong))
      case PrimitiveValue.BigInt(v) => Right(PrimitiveValue.Long(v.toLong))
      case PrimitiveValue.String(v) =>
        try Right(PrimitiveValue.Long(v.toLong))
        catch { case _: NumberFormatException => Left(MigrationError.InvalidCoercion(path, s"String($v)", "Long")) }
      case _ => Left(MigrationError.InvalidCoercion(path, pv.getClass.getSimpleName, "Long"))
    }

  private def toFloatPrimitive(pv: PrimitiveValue, path: DynamicOptic): Either[MigrationError, PrimitiveValue] =
    pv match {
      case PrimitiveValue.Float(v)  => Right(PrimitiveValue.Float(v))
      case PrimitiveValue.Byte(v)   => Right(PrimitiveValue.Float(v.toFloat))
      case PrimitiveValue.Short(v)  => Right(PrimitiveValue.Float(v.toFloat))
      case PrimitiveValue.Int(v)    => Right(PrimitiveValue.Float(v.toFloat))
      case PrimitiveValue.Long(v)   => Right(PrimitiveValue.Float(v.toFloat))
      case PrimitiveValue.Double(v) => Right(PrimitiveValue.Float(v.toFloat))
      case PrimitiveValue.String(v) =>
        try Right(PrimitiveValue.Float(v.toFloat))
        catch { case _: NumberFormatException => Left(MigrationError.InvalidCoercion(path, s"String($v)", "Float")) }
      case _ => Left(MigrationError.InvalidCoercion(path, pv.getClass.getSimpleName, "Float"))
    }

  private def toDoublePrimitive(pv: PrimitiveValue, path: DynamicOptic): Either[MigrationError, PrimitiveValue] =
    pv match {
      case PrimitiveValue.Double(v) => Right(PrimitiveValue.Double(v))
      case PrimitiveValue.Byte(v)   => Right(PrimitiveValue.Double(v.toDouble))
      case PrimitiveValue.Short(v)  => Right(PrimitiveValue.Double(v.toDouble))
      case PrimitiveValue.Int(v)    => Right(PrimitiveValue.Double(v.toDouble))
      case PrimitiveValue.Long(v)   => Right(PrimitiveValue.Double(v.toDouble))
      case PrimitiveValue.Float(v)  => Right(PrimitiveValue.Double(v.toDouble))
      case PrimitiveValue.String(v) =>
        try Right(PrimitiveValue.Double(v.toDouble))
        catch {
          case _: NumberFormatException => Left(MigrationError.InvalidCoercion(path, s"String($v)", "Double"))
        }
      case _ => Left(MigrationError.InvalidCoercion(path, pv.getClass.getSimpleName, "Double"))
    }

  private def toBigIntPrimitive(pv: PrimitiveValue, path: DynamicOptic): Either[MigrationError, PrimitiveValue] =
    pv match {
      case PrimitiveValue.BigInt(v) => Right(PrimitiveValue.BigInt(v))
      case PrimitiveValue.Byte(v)   => Right(PrimitiveValue.BigInt(BigInt(v.toInt)))
      case PrimitiveValue.Short(v)  => Right(PrimitiveValue.BigInt(BigInt(v.toInt)))
      case PrimitiveValue.Int(v)    => Right(PrimitiveValue.BigInt(BigInt(v)))
      case PrimitiveValue.Long(v)   => Right(PrimitiveValue.BigInt(BigInt(v)))
      case PrimitiveValue.String(v) =>
        try Right(PrimitiveValue.BigInt(BigInt(v)))
        catch { case _: NumberFormatException => Left(MigrationError.InvalidCoercion(path, s"String($v)", "BigInt")) }
      case _ => Left(MigrationError.InvalidCoercion(path, pv.getClass.getSimpleName, "BigInt"))
    }

  private def toBigDecimalPrimitive(
    pv: PrimitiveValue,
    path: DynamicOptic
  ): Either[MigrationError, PrimitiveValue] =
    pv match {
      case PrimitiveValue.BigDecimal(v) => Right(PrimitiveValue.BigDecimal(v))
      case PrimitiveValue.Byte(v)       => Right(PrimitiveValue.BigDecimal(BigDecimal(v.toInt)))
      case PrimitiveValue.Short(v)      => Right(PrimitiveValue.BigDecimal(BigDecimal(v.toInt)))
      case PrimitiveValue.Int(v)        => Right(PrimitiveValue.BigDecimal(BigDecimal(v)))
      case PrimitiveValue.Long(v)       => Right(PrimitiveValue.BigDecimal(BigDecimal(v)))
      case PrimitiveValue.Float(v)      => Right(PrimitiveValue.BigDecimal(BigDecimal(v.toDouble)))
      case PrimitiveValue.Double(v)     => Right(PrimitiveValue.BigDecimal(BigDecimal(v)))
      case PrimitiveValue.BigInt(v)     => Right(PrimitiveValue.BigDecimal(BigDecimal(v)))
      case PrimitiveValue.String(v)     =>
        try Right(PrimitiveValue.BigDecimal(BigDecimal(v)))
        catch {
          case _: NumberFormatException =>
            Left(MigrationError.InvalidCoercion(path, s"String($v)", "BigDecimal"))
        }
      case _ => Left(MigrationError.InvalidCoercion(path, pv.getClass.getSimpleName, "BigDecimal"))
    }

  private def toBooleanPrimitive(pv: PrimitiveValue, path: DynamicOptic): Either[MigrationError, PrimitiveValue] =
    pv match {
      case PrimitiveValue.Boolean(v) => Right(PrimitiveValue.Boolean(v))
      case PrimitiveValue.String(v)  =>
        v.toLowerCase match {
          case "true"  => Right(PrimitiveValue.Boolean(true))
          case "false" => Right(PrimitiveValue.Boolean(false))
          case _       => Left(MigrationError.InvalidCoercion(path, s"String($v)", "Boolean"))
        }
      case PrimitiveValue.Int(v) => Right(PrimitiveValue.Boolean(v != 0))
      case _                     => Left(MigrationError.InvalidCoercion(path, pv.getClass.getSimpleName, "Boolean"))
    }

  private def toShortPrimitive(pv: PrimitiveValue, path: DynamicOptic): Either[MigrationError, PrimitiveValue] =
    pv match {
      case PrimitiveValue.Short(v)  => Right(PrimitiveValue.Short(v))
      case PrimitiveValue.Byte(v)   => Right(PrimitiveValue.Short(v.toShort))
      case PrimitiveValue.Int(v)    => Right(PrimitiveValue.Short(v.toShort))
      case PrimitiveValue.Long(v)   => Right(PrimitiveValue.Short(v.toShort))
      case PrimitiveValue.String(v) =>
        try Right(PrimitiveValue.Short(v.toShort))
        catch { case _: NumberFormatException => Left(MigrationError.InvalidCoercion(path, s"String($v)", "Short")) }
      case _ => Left(MigrationError.InvalidCoercion(path, pv.getClass.getSimpleName, "Short"))
    }

  private def toBytePrimitive(pv: PrimitiveValue, path: DynamicOptic): Either[MigrationError, PrimitiveValue] =
    pv match {
      case PrimitiveValue.Byte(v)   => Right(PrimitiveValue.Byte(v))
      case PrimitiveValue.Short(v)  => Right(PrimitiveValue.Byte(v.toByte))
      case PrimitiveValue.Int(v)    => Right(PrimitiveValue.Byte(v.toByte))
      case PrimitiveValue.Long(v)   => Right(PrimitiveValue.Byte(v.toByte))
      case PrimitiveValue.String(v) =>
        try Right(PrimitiveValue.Byte(v.toByte))
        catch { case _: NumberFormatException => Left(MigrationError.InvalidCoercion(path, s"String($v)", "Byte")) }
      case _ => Left(MigrationError.InvalidCoercion(path, pv.getClass.getSimpleName, "Byte"))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Extraction Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private def extractString(dv: DynamicValue): Option[String] = dv match {
    case DynamicValue.Primitive(PrimitiveValue.String(s)) => Some(s)
    case _                                                => None
  }

  private def extractPrimitive(dv: DynamicValue): Option[PrimitiveValue] = dv match {
    case DynamicValue.Primitive(pv) => Some(pv)
    case _                          => None
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Record Field Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private def fieldIndex(fields: Chunk[(String, DynamicValue)], name: String): Int = {
    val len = fields.length
    var idx = 0
    while (idx < len) {
      if (fields(idx)._1 == name) return idx
      idx += 1
    }
    -1
  }

  private def hasField(fields: Chunk[(String, DynamicValue)], name: String): Boolean =
    fieldIndex(fields, name) >= 0

  private def dvTypeName(dv: DynamicValue): String = dv match {
    case _: DynamicValue.Primitive => "Primitive"
    case _: DynamicValue.Record    => "Record"
    case _: DynamicValue.Variant   => "Variant"
    case _: DynamicValue.Sequence  => "Sequence"
    case _: DynamicValue.Map       => "Map"
    case DynamicValue.Null         => "Null"
  }
}
