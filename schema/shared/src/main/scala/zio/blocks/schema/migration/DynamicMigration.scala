package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaExpr}

/**
 * An untyped, serializable migration that operates on `DynamicValue`.
 *
 * `DynamicMigration` is the core execution engine: it applies a sequence of
 * `MigrationAction` steps to transform a `DynamicValue` from one schema shape
 * to another. It is the serializable, schema-agnostic backbone that the typed
 * `Migration[A, B]` wrapper delegates to.
 *
 * @param actions
 *   Ordered sequence of actions to apply
 * @param metadata
 *   Optional metadata for identification, auditing, and conflict detection
 */
final case class DynamicMigration(
  actions: Vector[MigrationAction],
  metadata: MigrationMetadata = MigrationMetadata.empty
) {

  /**
   * Applies this migration to a `DynamicValue`, returning the transformed value
   * or a `MigrationError`.
   *
   * Actions are applied sequentially in order. If any action fails, execution
   * stops and the error is returned.
   *
   * @param includeInputSlice
   *   If `true`, error messages include truncated DynamicValue snippets at the
   *   failure path. Disable for production to avoid leaking sensitive data.
   */
  def apply(value: DynamicValue, includeInputSlice: Boolean = false): Either[MigrationError, DynamicValue] =
    applyWithDepth(value, includeInputSlice, 0)

  private[migration] def applyWithDepth(
    value: DynamicValue,
    includeInputSlice: Boolean,
    depth: Int
  ): Either[MigrationError, DynamicValue] = {
    if (depth > DynamicMigration.MaxExecutionDepth)
      return Left(
        MigrationError(
          s"TransformCase nesting depth $depth exceeds maximum ${DynamicMigration.MaxExecutionDepth}",
          DynamicOptic.root
        )
      )
    var current = value
    var i       = 0
    while (i < actions.length) {
      val action = actions(i)
      DynamicMigration.executeAction(current, action, i, includeInputSlice, depth) match {
        case Right(next) =>
          current = next
          i += 1
        case left => return left
      }
    }
    Right(current)
  }

  /**
   * Concatenates two migrations. Actions from `that` are appended after this
   * migration's actions.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions, metadata)

  /**
   * Returns the reverse migration if all actions are reversible. Returns `None`
   * if any action is lossy (has `reverse == None`).
   */
  def reverse: Option[DynamicMigration] =
    if (actions.exists(_.reverse.isEmpty)) None
    else
      Some(
        DynamicMigration(
          actions.reverseIterator.flatMap(_.reverse).toVector,
          metadata
        )
      )

  /**
   * Returns the reverse migration or throws if any action is lossy.
   *
   * Throws `IllegalStateException` if any action is irreversible, listing the
   * lossy actions.
   */
  def unsafeReverse: DynamicMigration = reverse.getOrElse {
    val lossyDetails = actions.zipWithIndex.collect {
      case (a, i) if a.lossy => s"  [action $i] ${a.getClass.getSimpleName} at ${a.at}"
    }
    throw new IllegalStateException(
      s"Migration contains ${lossyDetails.size} lossy action(s):\n${lossyDetails.mkString("\n")}"
    )
  }

  /** Returns `true` if any action in this migration is lossy. */
  def isLossy: Boolean = actions.exists(_.lossy)

  /** Returns the indices of all lossy actions. */
  def lossyActionIndices: Vector[Int] =
    actions.zipWithIndex.collect { case (a, i) if a.lossy => i }.toVector

  /**
   * Returns a human-readable summary of this migration.
   */
  def explain: String = {
    val id       = metadata.id.fold("")(id => s""" "$id"""")
    val count    = actions.size
    val lossInfo = if (isLossy) "lossy" else "lossless"
    val header   = s"DynamicMigration$id ($count action${if (count != 1) "s" else ""}, $lossInfo):"
    if (actions.isEmpty) header
    else {
      val details = actions.zipWithIndex.map { case (a, i) =>
        s"  [$i] ${actionSummary(a)}"
      }
      (header +: details).mkString("\n")
    }
  }

  private def actionSummary(action: MigrationAction): String = action match {
    case MigrationAction.AddField(at, _)               => s"AddField $at"
    case MigrationAction.DropField(at, _)              => s"DropField $at${if (action.lossy) " (lossy)" else ""}"
    case MigrationAction.Rename(at, newName)           => s"Rename $at -> $newName"
    case MigrationAction.TransformValue(at, _, _)      => s"TransformValue $at${if (action.lossy) " (lossy)" else ""}"
    case MigrationAction.Mandate(at, _)                => s"Mandate $at"
    case MigrationAction.Optionalize(at)               => s"Optionalize $at"
    case MigrationAction.ChangeType(at, _, _)          => s"ChangeType $at${if (action.lossy) " (lossy)" else ""}"
    case MigrationAction.RenameCase(at, from, to)      => s"RenameCase $at: $from -> $to"
    case MigrationAction.TransformCase(at, name, subs) => s"TransformCase $at<$name> (${subs.size} sub-actions)"
    case MigrationAction.TransformElements(at, _, _)   => s"TransformElements $at${if (action.lossy) " (lossy)" else ""}"
    case MigrationAction.TransformKeys(at, _, _)       => s"TransformKeys $at${if (action.lossy) " (lossy)" else ""}"
    case MigrationAction.TransformValues(at, _, _)     => s"TransformValues $at${if (action.lossy) " (lossy)" else ""}"
    case MigrationAction.Join(at, srcs, _, _, _)       =>
      s"Join ${srcs.mkString(", ")} -> $at${if (action.lossy) " (lossy)" else ""}"
    case MigrationAction.Split(at, tgts, _, _, _) =>
      s"Split $at -> ${tgts.mkString(", ")}${if (action.lossy) " (lossy)" else ""}"
  }
}

object DynamicMigration {

  /**
   * Configurable limits for validating untrusted/deserialized migrations.
   */
  final case class Limits(
    maxActions: Int = 100,
    maxExprDepth: Int = 20,
    maxOpticDepth: Int = 50,
    maxTotalNodes: Int = 1000,
    maxNestingDepth: Int = 10
  )

  /**
   * Validates a migration against configurable safety limits.
   *
   * Use this before executing migrations received from untrusted sources
   * (deserialized from config, DB, network, etc.).
   *
   * @return
   *   `Right(migration)` if valid, `Left(errors)` listing violations
   */
  def validate(migration: DynamicMigration, limits: Limits = Limits()): Either[List[String], DynamicMigration] = {
    val errors     = List.newBuilder[String]
    var totalNodes = 0

    if (migration.actions.size > limits.maxActions)
      errors += s"Too many actions: ${migration.actions.size} > ${limits.maxActions}"

    def validateActions(actions: Vector[MigrationAction], depth: Int): Unit = {
      if (depth > limits.maxNestingDepth) {
        errors += s"TransformCase nesting depth $depth exceeds limit ${limits.maxNestingDepth}"
        return
      }
      actions.foreach { action =>
        val opticDepth = action.at.nodes.size
        if (opticDepth > limits.maxOpticDepth)
          errors += s"Optic depth $opticDepth exceeds limit ${limits.maxOpticDepth} at ${action.at}"
        totalNodes += opticDepth + 1

        action match {
          case MigrationAction.Join(_, sourcePaths, _, _, _) =>
            sourcePaths.foreach { sp =>
              val d = sp.nodes.size
              if (d > limits.maxOpticDepth)
                errors += s"Join source optic depth $d exceeds limit ${limits.maxOpticDepth} at $sp"
              totalNodes += d
            }
          case MigrationAction.Split(_, targetPaths, _, _, _) =>
            targetPaths.foreach { tp =>
              val d = tp.nodes.size
              if (d > limits.maxOpticDepth)
                errors += s"Split target optic depth $d exceeds limit ${limits.maxOpticDepth} at $tp"
              totalNodes += d
            }
          case MigrationAction.TransformCase(_, _, subActions) =>
            validateActions(subActions, depth + 1)
          case _ => ()
        }
      }
    }

    validateActions(migration.actions, 0)

    if (totalNodes > limits.maxTotalNodes)
      errors += s"Total node count $totalNodes exceeds limit ${limits.maxTotalNodes}"

    val result = errors.result()
    if (result.isEmpty) Right(migration)
    else Left(result)
  }

  // ── Action Execution ────────────────────────────────────────────────────

  private val MaxExecutionDepth: Int = 50

  private def executeAction(
    value: DynamicValue,
    action: MigrationAction,
    actionIndex: Int,
    includeInputSlice: Boolean,
    depth: Int
  ): Either[MigrationError, DynamicValue] = {
    def mkError(msg: String, path: DynamicOptic): MigrationError = {
      val slice =
        if (includeInputSlice) {
          val atPath = value.get(path)
          Some(SchemaShape.truncate(atPath.toString, 200))
        } else None
      MigrationError(msg, path, Some(actionIndex), Some(action), inputSlice = slice)
    }

    action match {
      case MigrationAction.AddField(at, defaultExpr) =>
        val fieldName   = MigrationAction.lastFieldName(at)
        val parentOptic = MigrationAction.parentPath(at)
        fieldName match {
          case None =>
            Left(mkError("AddField path must end with a Field node", at))
          case Some(name) =>
            evalExprDynamic(defaultExpr) match {
              case Left(msg) =>
                Left(mkError(s"Failed to evaluate default expression: $msg", at))
              case Right(defaultVal) =>
                modifyRecord(value, parentOptic, actionIndex, action) { fields =>
                  if (fields.exists(_._1 == name))
                    Left(mkError(s"Field '$name' already exists", at))
                  else
                    Right(fields :+ ((name, defaultVal)))
                }
            }
        }

      case MigrationAction.DropField(at, _) =>
        MigrationAction.lastFieldName(at) match {
          case None =>
            Left(mkError("DropField path must end with a Field node", at))
          case Some(name) =>
            modifyRecord(value, MigrationAction.parentPath(at), actionIndex, action) { fields =>
              if (!fields.exists(_._1 == name))
                Left(mkError(s"Field '$name' not found", at))
              else
                Right(fields.filter(_._1 != name))
            }
        }

      case MigrationAction.Rename(at, newName) =>
        MigrationAction.lastFieldName(at) match {
          case None =>
            Left(mkError("Rename path must end with a Field node", at))
          case Some(oldName) =>
            modifyRecord(value, MigrationAction.parentPath(at), actionIndex, action) { fields =>
              if (!fields.exists(_._1 == oldName))
                Left(mkError(s"Field '$oldName' not found for rename", at))
              else if (fields.exists(_._1 == newName))
                Left(mkError(s"Field '$newName' already exists; cannot rename to it", at))
              else
                Right(fields.map { case (n, v) => if (n == oldName) (newName, v) else (n, v) })
            }
        }

      case MigrationAction.TransformValue(at, transform, _) =>
        modifyAtPath(value, at, actionIndex, action) { dv =>
          evalExprDynamicWithInput(transform, dv) match {
            case Left(msg) => Left(mkError(s"Transform evaluation failed: $msg", at))
            case Right(v)  => Right(v)
          }
        }

      case MigrationAction.Mandate(at, defaultExpr) =>
        modifyAtPath(value, at, actionIndex, action) {
          case DynamicValue.Variant("Some", inner) => Right(inner)
          case DynamicValue.Variant("None", _)     =>
            evalExprDynamic(defaultExpr) match {
              case Left(msg) => Left(mkError(s"Default expression evaluation failed: $msg", at))
              case Right(dv) => Right(dv)
            }
          case other =>
            Left(mkError(s"Expected Option-like variant (Some/None), got ${other.valueType}", at))
        }

      case MigrationAction.Optionalize(at) =>
        modifyAtPath(value, at, actionIndex, action) { dv =>
          Right(DynamicValue.Variant("Some", dv))
        }

      case MigrationAction.ChangeType(at, converter, _) =>
        modifyAtPath(value, at, actionIndex, action) { dv =>
          evalExprDynamicWithInput(converter, dv) match {
            case Left(msg) => Left(mkError(s"Type conversion failed: $msg", at))
            case Right(v)  => Right(v)
          }
        }

      case MigrationAction.RenameCase(at, fromName, toName) =>
        modifyAtPath(value, at, actionIndex, action) {
          case DynamicValue.Variant(caseName, caseValue) if caseName == fromName =>
            Right(DynamicValue.Variant(toName, caseValue))
          case v: DynamicValue.Variant =>
            // Case doesn't match — this is not an error; the variant just has a different active case
            Right(v)
          case other =>
            Left(mkError(s"Expected variant, got ${other.valueType}", at))
        }

      case MigrationAction.TransformCase(at, caseName, subActions) =>
        modifyAtPath(value, at, actionIndex, action) {
          case DynamicValue.Variant(cn, caseValue) if cn == caseName =>
            val subMigration = DynamicMigration(subActions)
            subMigration.applyWithDepth(caseValue, includeInputSlice, depth + 1) match {
              case Right(transformed) => Right(DynamicValue.Variant(cn, transformed))
              case Left(err)          => Left(err.copy(actionIndex = Some(actionIndex)))
            }
          case v: DynamicValue.Variant =>
            // Case doesn't match — pass through unchanged
            Right(v)
          case other =>
            Left(mkError(s"Expected variant, got ${other.valueType}", at))
        }

      case MigrationAction.TransformElements(at, transform, _) =>
        modifyAtPath(value, at, actionIndex, action) {
          case DynamicValue.Sequence(elements) =>
            mapChunk(elements, transform) match {
              case Left(msg)       => Left(mkError(s"Element transform failed: $msg", at))
              case Right(newElems) => Right(DynamicValue.Sequence(newElems))
            }
          case other =>
            Left(mkError(s"Expected sequence, got ${other.valueType}", at))
        }

      case MigrationAction.TransformKeys(at, transform, _) =>
        modifyAtPath(value, at, actionIndex, action) {
          case DynamicValue.Map(entries) =>
            mapChunkEntryKeys(entries, transform) match {
              case Left(msg)         => Left(mkError(s"Key transform failed: $msg", at))
              case Right(newEntries) => Right(DynamicValue.Map(newEntries))
            }
          case other =>
            Left(mkError(s"Expected map, got ${other.valueType}", at))
        }

      case MigrationAction.TransformValues(at, transform, _) =>
        modifyAtPath(value, at, actionIndex, action) {
          case DynamicValue.Map(entries) =>
            mapChunkEntryValues(entries, transform) match {
              case Left(msg)         => Left(mkError(s"Value transform failed: $msg", at))
              case Right(newEntries) => Right(DynamicValue.Map(newEntries))
            }
          case other =>
            Left(mkError(s"Expected map, got ${other.valueType}", at))
        }

      case MigrationAction.Join(at, sourcePaths, combiner, _, _) =>
        // Extract values from source paths
        val extractedBuilder                     = Vector.newBuilder[DynamicValue]
        var extractError: Option[MigrationError] = None
        var i                                    = 0
        while (i < sourcePaths.length && extractError.isEmpty) {
          val sp = sourcePaths(i)
          value.get(sp).one match {
            case Left(_) =>
              extractError = Some(mkError(s"Join source path not found: $sp", sp))
            case Right(dv) =>
              extractedBuilder += dv
          }
          i += 1
        }
        extractError match {
          case Some(err) => Left(err)
          case None      =>
            // Build a record from extracted values for the combiner to operate on
            val extracted   = extractedBuilder.result()
            val inputRecord = DynamicValue.Sequence(Chunk.fromIterable(extracted))
            evalExprDynamicWithInput(combiner, inputRecord) match {
              case Left(msg) =>
                Left(mkError(s"Join combiner failed: $msg", at))
              case Right(combined) =>
                // Remove source fields, then insert target field
                var current                             = value
                var removeError: Option[MigrationError] = None
                i = 0
                while (i < sourcePaths.length && removeError.isEmpty) {
                  current.deleteOrFail(sourcePaths(i)) match {
                    case Right(next) => current = next
                    case Left(err)   =>
                      removeError = Some(mkError(s"Failed to remove source: ${err.message}", sourcePaths(i)))
                  }
                  i += 1
                }
                removeError match {
                  case Some(err) => Left(err)
                  case None      =>
                    current.insertOrFail(at, combined) match {
                      case Right(result) => Right(result)
                      case Left(err)     => Left(mkError(s"Failed to insert join result: ${err.message}", at))
                    }
                }
            }
        }

      case MigrationAction.Split(at, targetPaths, splitter, _, _) =>
        value.get(at).one match {
          case Left(_) =>
            Left(mkError(s"Split source path not found", at))
          case Right(sourceVal) =>
            evalExprDynamicWithInput(splitter, sourceVal) match {
              case Left(errMsg) =>
                Left(mkError(s"Split splitter failed: $errMsg", at))
              case Right(splitRes) =>
                // Extract individual pieces from the split result
                val pieces: Vector[DynamicValue] = splitRes match {
                  case DynamicValue.Sequence(elems) => elems.toVector
                  case DynamicValue.Record(flds)    => flds.map(_._2).toVector
                  case single                       => Vector(single)
                }
                if (pieces.size < targetPaths.size) {
                  Left(
                    mkError(
                      s"Split produced ${pieces.size} pieces but ${targetPaths.size} target paths specified",
                      at
                    )
                  )
                } else {
                  // Remove source, then insert each piece at its target path
                  value.deleteOrFail(at) match {
                    case Left(err) =>
                      Left(mkError(s"Failed to remove split source: ${err.message}", at))
                    case Right(afterRemoval) =>
                      var current                             = afterRemoval
                      var insertError: Option[MigrationError] = None
                      var i                                   = 0
                      while (i < targetPaths.length && insertError.isEmpty) {
                        current.insertOrFail(targetPaths(i), pieces(i)) match {
                          case Right(next) => current = next
                          case Left(err)   =>
                            insertError = Some(mkError(s"Failed to insert split piece: ${err.message}", targetPaths(i)))
                        }
                        i += 1
                      }
                      insertError match {
                        case Some(err) => Left(err)
                        case None      => Right(current)
                      }
                  }
                }
            }
        }
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────────

  /**
   * Modifies the DynamicValue at a path, wrapping errors with action context.
   */
  private def modifyAtPath(
    value: DynamicValue,
    path: DynamicOptic,
    actionIndex: Int,
    action: MigrationAction
  )(
    f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    if (path.nodes.isEmpty) {
      f(value)
    } else {
      var capturedError: Option[MigrationError] = None
      val modified                              = try {
        value.modifyOrFail(path) { case dv =>
          f(dv) match {
            case Right(newDv) =>
              capturedError = None
              newDv
            case Left(err) =>
              capturedError = Some(err)
              dv // Return unchanged, error captured
          }
        }
      } catch {
        case e: Throwable =>
          Left(
            zio.blocks.schema.SchemaError.conversionFailed(
              Nil,
              s"Unexpected error modifying path $path: ${e.getMessage}"
            )
          )
      }
      capturedError match {
        case Some(err) => Left(err)
        case None      =>
          modified match {
            case Right(v)  => Right(v)
            case Left(err) =>
              Left(
                MigrationError(
                  s"Path not found: ${err.message}",
                  path,
                  Some(actionIndex),
                  Some(action)
                )
              )
          }
      }
    }

  /** Modifies record fields at the parent path. */
  private def modifyRecord(
    value: DynamicValue,
    parentPath: DynamicOptic,
    actionIndex: Int,
    action: MigrationAction
  )(
    f: Chunk[(String, DynamicValue)] => Either[MigrationError, Chunk[(String, DynamicValue)]]
  ): Either[MigrationError, DynamicValue] =
    modifyAtPath(value, parentPath, actionIndex, action) {
      case DynamicValue.Record(fields) =>
        f(fields).map(DynamicValue.Record(_))
      case other =>
        Left(
          MigrationError(
            s"Expected record, got ${other.valueType}",
            parentPath,
            Some(actionIndex),
            Some(action)
          )
        )
    }

  /** Evaluates a SchemaExpr to produce a DynamicValue (no input needed). */
  private def evalExprDynamic(expr: SchemaExpr[Any, Any]): Either[String, DynamicValue] =
    try {
      expr.evalDynamic(null.asInstanceOf[Any]) match {
        case Right(values) if values.nonEmpty => Right(values.head)
        case Right(_)                         => Left("Expression produced no values")
        case Left(check)                      => Left(s"Expression check failed: $check")
      }
    } catch {
      case e: Throwable => Left(s"Expression evaluation error: ${e.getMessage}")
    }

  /**
   * Evaluates a SchemaExpr with a DynamicValue as input, producing a
   * DynamicValue output.
   */
  private def evalExprDynamicWithInput(expr: SchemaExpr[Any, Any], input: DynamicValue): Either[String, DynamicValue] =
    try {
      expr.evalDynamic(input) match {
        case Right(values) if values.nonEmpty => Right(values.head)
        case Right(_)                         => Left("Expression produced no values")
        case Left(check)                      => Left(s"Expression check failed: $check")
      }
    } catch {
      case e: Throwable => Left(s"Expression evaluation error: ${e.getMessage}")
    }

  /** Maps a transform expression over each element in a Chunk. */
  private def mapChunk(
    elements: Chunk[DynamicValue],
    transform: SchemaExpr[Any, Any]
  ): Either[String, Chunk[DynamicValue]] = {
    val builder = Chunk.newBuilder[DynamicValue]
    val len     = elements.length
    var i       = 0
    while (i < len) {
      evalExprDynamicWithInput(transform, elements(i)) match {
        case Right(v)  => builder += v
        case Left(msg) => return Left(s"At element [$i]: $msg")
      }
      i += 1
    }
    Right(builder.result())
  }

  /** Maps a transform over map entry keys. */
  private def mapChunkEntryKeys(
    entries: Chunk[(DynamicValue, DynamicValue)],
    transform: SchemaExpr[Any, Any]
  ): Either[String, Chunk[(DynamicValue, DynamicValue)]] = {
    val builder = Chunk.newBuilder[(DynamicValue, DynamicValue)]
    val len     = entries.length
    var i       = 0
    while (i < len) {
      val (k, v) = entries(i)
      evalExprDynamicWithInput(transform, k) match {
        case Right(newK) => builder += ((newK, v))
        case Left(msg)   => return Left(s"At key [$i]: $msg")
      }
      i += 1
    }
    Right(builder.result())
  }

  /** Maps a transform over map entry values. */
  private def mapChunkEntryValues(
    entries: Chunk[(DynamicValue, DynamicValue)],
    transform: SchemaExpr[Any, Any]
  ): Either[String, Chunk[(DynamicValue, DynamicValue)]] = {
    val builder = Chunk.newBuilder[(DynamicValue, DynamicValue)]
    val len     = entries.length
    var i       = 0
    while (i < len) {
      val (k, v) = entries(i)
      evalExprDynamicWithInput(transform, v) match {
        case Right(newV) => builder += ((k, newV))
        case Left(msg)   => return Left(s"At value [$i]: $msg")
      }
      i += 1
    }
    Right(builder.result())
  }
}
