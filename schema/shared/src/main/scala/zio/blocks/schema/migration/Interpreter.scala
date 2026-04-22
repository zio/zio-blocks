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

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema._

/**
 * Interprets a single [[MigrationAction]] against a [[DynamicValue]].
 *
 * Centralized single exhaustive `match` — adding a new
 * [[MigrationAction]] variant without an arm here fails compilation under
 * `-Werror`.
 */
private[migration] object Interpreter {

  def apply(action: MigrationAction, dv: DynamicValue): Either[MigrationError, DynamicValue] =
    action match {
      case a: MigrationAction.AddField          => addField(a, dv)
      case a: MigrationAction.DropField         => dropField(a, dv)
      case a: MigrationAction.Rename            => rename(a, dv)
      case a: MigrationAction.TransformValue    => transformValue(a, dv)
      case a: MigrationAction.ChangeType        => changeType(a, dv)
      // --- Option / Enum / Collection / Map ---
      case a: MigrationAction.Mandate           => mandate(a, dv)
      case a: MigrationAction.Optionalize       => optionalize(a, dv)
      case a: MigrationAction.RenameCase        => renameCase(a, dv)
      case a: MigrationAction.TransformCase     => transformCase(a, dv)
      case a: MigrationAction.TransformElements => transformElements(a, dv)
      case a: MigrationAction.TransformKeys     => transformKeys(a, dv)
      case a: MigrationAction.TransformValues   => transformValues(a, dv)
      // --- Join / Split ---
      case a: MigrationAction.Join              => join(a, dv)
      case a: MigrationAction.Split             => split(a, dv)
    }

  // --- Arm bodies ------------------------------------------------------------

  private def addField(
    a: MigrationAction.AddField,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val fullPath = a.at.field(a.fieldName)
    // Check the parent shape up-front so a non-Record target surfaces as a
    // SchemaMismatch (a structural schema-shape error) rather than collapsing
    // into a generic ActionFailed. The root case
    // (`a.at == DynamicOptic.root`) is also covered: `currentDv.get(root)`
    // returns the dv itself, so the kind check applies to top-level inserts.
    currentDv.get(a.at).values match {
      case Some(parentValues) if parentValues.nonEmpty =>
        parentValues.head match {
          case _: DynamicValue.Record =>
            resolveDefault(a.default, currentDv) match {
              case Right(defaultValue) =>
                currentDv.insertOrFail(fullPath, defaultValue) match {
                  case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
                  case _              => new Left(new MigrationError.ActionFailed(fullPath, "AddField"))
                }
              case Left(opticCheck) =>
                // Preserve the underlying default-resolution failure
                // so the caller sees, e.g., the
                // unsupported-SchemaRepr message rather than a bare
                // "Action AddField failed at ...".
                new Left(
                  new MigrationError.ActionFailed(fullPath, "AddField", new Some(opticCheck.message))
                )
            }
          case other =>
            new Left(new MigrationError.SchemaMismatch(a.at, "Record", dynamicValueKind(other)))
        }
      case _ =>
        new Left(new MigrationError.ActionFailed(fullPath, "AddField"))
    }
  }

  private def dropField(
    a: MigrationAction.DropField,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val fullPath = a.at.field(a.fieldName)
    currentDv.deleteOrFail(fullPath) match {
      case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
      case _              => new Left(new MigrationError.MissingField(fullPath, a.fieldName))
    }
  }

  private def rename(
    a: MigrationAction.Rename,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val nodes = a.at.nodes
    if (nodes.isEmpty)
      new Left(new MigrationError.ActionFailed(a.at, "Rename"))
    else
      nodes.last match {
        case f: DynamicOptic.Node.Field =>
          // Preserve field index by rewriting the key in place at `parent`'s
          // Record. A naive `delete + insert` would always append to the end
          // (DynamicValue.insertAtPath on Record uses `r.fields :+ ...`),
          // silently reordering non-terminal renames.
          val parent  = new DynamicOptic(nodes.init.toIndexedSeq)
          val oldPath = parent.field(f.name)
          currentDv.get(parent).values match {
            case Some(parentValues) if parentValues.nonEmpty =>
              parentValues.head match {
                case r: DynamicValue.Record =>
                  val idx = r.fields.indexWhere(_._1 == f.name)
                  if (idx < 0) new Left(new MigrationError.MissingField(oldPath, f.name))
                  else {
                    val updatedFields = r.fields.updated(idx, (a.to, r.fields(idx)._2))
                    val updatedRecord = new DynamicValue.Record(updatedFields)
                    currentDv.setOrFail(parent, updatedRecord) match {
                      case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
                      case _              => new Left(new MigrationError.ActionFailed(a.at, "Rename"))
                    }
                  }
                case other =>
                  new Left(
                    new MigrationError.SchemaMismatch(parent, "Record", dynamicValueKind(other))
                  )
              }
            case _ =>
              new Left(new MigrationError.MissingField(oldPath, f.name))
          }
        case other =>
          // Last node is not a Field (e.g. Elements / Case / AtIndex) — this
          // is a schema-shape mismatch, not a runtime data error
          new Left(
            new MigrationError.SchemaMismatch(a.at, "Field", dynamicOpticNodeKind(other))
          )
      }
  }

  private def transformValue(
    a: MigrationAction.TransformValue,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    applyTransformAt(a.at, a.transform, currentDv, "TransformValue")

  private def changeType(
    a: MigrationAction.ChangeType,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    // Same recipe as TransformValue — semantic distinction is compile-time only.
    applyTransformAt(a.at, a.converter, currentDv, "ChangeType")

  // --- Option / Enum / Collection / Map arm bodies ----------------------------------------------------

  private def mandate(
    a: MigrationAction.Mandate,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    currentDv.get(a.at).values match {
      case Some(values) if values.nonEmpty =>
        values.head match {
          case DynamicValue.Variant("Some", DynamicValue.Record(fields))
              if fields.length == 1 && fields(0)._1 == "value" =>
            currentDv.setOrFail(a.at, fields(0)._2) match {
              case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
              case _              => new Left(new MigrationError.ActionFailed(a.at, "Mandate"))
            }
          case DynamicValue.Variant("None", _) =>
            resolveDefault(a.default, currentDv) match {
              case Right(defaultValue) =>
                currentDv.setOrFail(a.at, defaultValue) match {
                  case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
                  case _              => new Left(new MigrationError.ActionFailed(a.at, "Mandate"))
                }
              case Left(opticCheck) =>
                new Left(new MigrationError.ActionFailed(a.at, "Mandate", new Some(opticCheck.message)))
            }
          case other =>
            new Left(new MigrationError.SchemaMismatch(a.at, "Option (Some/None)", dynamicValueKind(other)))
        }
      case _ =>
        new Left(new MigrationError.SchemaMismatch(a.at, "single value", "no value (path not addressable)"))
    }

  private def optionalize(
    a: MigrationAction.Optionalize,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    currentDv.get(a.at).values match {
      case Some(values) if values.nonEmpty =>
        val wrapped = new DynamicValue.Variant(
          "Some",
          new DynamicValue.Record(Chunk("value" -> values.head))
        )
        currentDv.setOrFail(a.at, wrapped) match {
          case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
          case _              => new Left(new MigrationError.ActionFailed(a.at, "Optionalize"))
        }
      case _ =>
        new Left(new MigrationError.SchemaMismatch(a.at, "single value", "no value (path not addressable)"))
    }

  private def renameCase(
    a: MigrationAction.RenameCase,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    currentDv.modifyOrFail(a.at) {
      case v: DynamicValue.Variant if v.caseNameValue == a.from =>
        new DynamicValue.Variant(a.to, v.value)
    } match {
      case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
      case _ =>
        currentDv.get(a.at).values match {
          case Some(values) if values.nonEmpty =>
            values.head match {
              case v: DynamicValue.Variant =>
                new Left(new MigrationError.SchemaMismatch(a.at, s"Case ${a.from}", s"Case ${v.caseNameValue}"))
              case other =>
                new Left(new MigrationError.SchemaMismatch(a.at, "Variant", dynamicValueKind(other)))
            }
          case _ =>
            new Left(new MigrationError.SchemaMismatch(a.at, "Variant", "no value (path not addressable)"))
        }
    }

  private def transformCase(
    a: MigrationAction.TransformCase,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val expectedCaseName: Option[String] = a.at.nodes.lastOption match {
      case Some(c: DynamicOptic.Node.Case) => new Some(c.name)
      case _                               => None
    }
    expectedCaseName match {
      case Some(_) =>
        currentDv.get(a.at).values match {
          case Some(values) if values.nonEmpty =>
            // Path resolved through Case node — variant matched expected case.
            // Run inner actions left-to-right with short-circuit, mirroring DynamicMigration.apply.
            var current: DynamicValue                        = currentDv
            val arr                                          = a.actions
            val len                                          = arr.length
            var idx                                          = 0
            var result: Either[MigrationError, DynamicValue] = new Right(currentDv)
            while (idx < len && result.isRight) {
              Interpreter(arr(idx), current) match {
                case r @ Right(updated) => current = updated; result = r
                case l                  => result = l
              }
              idx += 1
            }
            result
          case _ =>
            // Path-resolution failed — variant case did not match.
            new Right(currentDv)
        }
      case _ =>
        // `at` does not end in Node.Case — malformed paths degrade to SchemaMismatch.
        new Left(new MigrationError.SchemaMismatch(a.at, "path ending in Case", "path with non-Case terminal node"))
    }
  }

  private def transformElements(
    a: MigrationAction.TransformElements,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    currentDv.get(a.at).values match {
      case Some(values) if values.nonEmpty =>
        values.head match {
          case s: DynamicValue.Sequence =>
            val builder             = ChunkBuilder.make[DynamicValue](s.elements.length)
            val len                 = s.elements.length
            var idx                 = 0
            var failure: Option[MigrationError] = None
            while (idx < len && failure.isEmpty) {
              evalTransform(a.transform, s.elements(idx)) match {
                case Right(v) => builder.addOne(v)
                case Left(check) =>
                  val errPath = new DynamicOptic(
                    a.at.nodes.appended(DynamicOptic.Node.Elements).appended(new DynamicOptic.Node.AtIndex(idx))
                  )
                  failure = new Some(new MigrationError.ActionFailed(errPath, "TransformElements", new Some(check.message)))
              }
              idx += 1
            }
            failure match {
              case Some(err) => new Left(err)
              case _ =>
                currentDv.setOrFail(a.at, new DynamicValue.Sequence(builder.result())) match {
                  case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
                  case _              => new Left(new MigrationError.ActionFailed(a.at, "TransformElements"))
                }
            }
          case other =>
            new Left(new MigrationError.SchemaMismatch(a.at, "Sequence", dynamicValueKind(other)))
        }
      case _ =>
        new Left(new MigrationError.SchemaMismatch(a.at, "Sequence", "no value (path not addressable)"))
    }

  private def transformKeys(
    a: MigrationAction.TransformKeys,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    currentDv.get(a.at).values match {
      case Some(values) if values.nonEmpty =>
        values.head match {
          case m: DynamicValue.Map =>
            val builder  = ChunkBuilder.make[(DynamicValue, DynamicValue)](m.entries.length)
            val seenKeys = new java.util.HashSet[DynamicValue](m.entries.length << 1)
            val len      = m.entries.length
            var idx      = 0
            var failure: Option[MigrationError] = None
            while (idx < len && failure.isEmpty) {
              val (srcKey, value) = m.entries(idx)
              evalTransform(a.transform, srcKey) match {
                case Right(newKey) =>
                  if (seenKeys.contains(newKey)) {
                    val errPath = new DynamicOptic(
                      a.at.nodes.appended(DynamicOptic.Node.MapKeys).appended(new DynamicOptic.Node.AtMapKey(srcKey))
                    )
                    failure = new Some(new MigrationError.KeyCollision(errPath, srcKey))
                  } else {
                    seenKeys.add(newKey)
                    builder.addOne((newKey, value))
                  }
                case Left(check) =>
                  val errPath = new DynamicOptic(
                    a.at.nodes.appended(DynamicOptic.Node.MapKeys).appended(new DynamicOptic.Node.AtMapKey(srcKey))
                  )
                  failure = new Some(new MigrationError.ActionFailed(errPath, "TransformKeys", new Some(check.message)))
              }
              idx += 1
            }
            failure match {
              case Some(err) => new Left(err)
              case _ =>
                currentDv.setOrFail(a.at, new DynamicValue.Map(builder.result())) match {
                  case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
                  case _              => new Left(new MigrationError.ActionFailed(a.at, "TransformKeys"))
                }
            }
          case other =>
            new Left(new MigrationError.SchemaMismatch(a.at, "Map", dynamicValueKind(other)))
        }
      case _ =>
        new Left(new MigrationError.SchemaMismatch(a.at, "Map", "no value (path not addressable)"))
    }

  private def transformValues(
    a: MigrationAction.TransformValues,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    currentDv.get(a.at).values match {
      case Some(values) if values.nonEmpty =>
        values.head match {
          case m: DynamicValue.Map =>
            val builder = ChunkBuilder.make[(DynamicValue, DynamicValue)](m.entries.length)
            val len     = m.entries.length
            var idx     = 0
            var failure: Option[MigrationError] = None
            while (idx < len && failure.isEmpty) {
              val (k, v) = m.entries(idx)
              evalTransform(a.transform, v) match {
                case Right(newV) => builder.addOne((k, newV))
                case Left(check) =>
                  val errPath = new DynamicOptic(
                    a.at.nodes.appended(DynamicOptic.Node.MapValues).appended(new DynamicOptic.Node.AtMapKey(k))
                  )
                  failure = new Some(new MigrationError.ActionFailed(errPath, "TransformValues", new Some(check.message)))
              }
              idx += 1
            }
            failure match {
              case Some(err) => new Left(err)
              case _ =>
                currentDv.setOrFail(a.at, new DynamicValue.Map(builder.result())) match {
                  case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
                  case _              => new Left(new MigrationError.ActionFailed(a.at, "TransformValues"))
                }
            }
          case other =>
            new Left(new MigrationError.SchemaMismatch(a.at, "Map", dynamicValueKind(other)))
        }
      case _ =>
        new Left(new MigrationError.SchemaMismatch(a.at, "Map", "no value (path not addressable)"))
    }

  // --- Join / Split arm bodies ----------------------------------------------------

  private def join(
    a: MigrationAction.Join,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    // combiner evaluated against the ROOT currentDv, not `currentDv.get(at).values.head`.
    // sourcePaths is builder operational metadata — NOT consumed by the interpreter at apply time.
    evalTransform(a.combiner, currentDv) match {
      case Right(outputValue) =>
        currentDv.setOrFail(a.at, outputValue) match {
          case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
          case _              => new Left(new MigrationError.ActionFailed(a.at, "Join"))
        }
      case Left(opticCheck) =>
        new Left(new MigrationError.ActionFailed(a.at, "Join", new Some(opticCheck.message)))
    }

  private def split(
    a: MigrationAction.Split,
    currentDv: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    // splitter evaluated against the ROOT currentDv; expected to return a composite
    // DynamicValue.Record whose fields align positionally with targetPaths.
    // Arity/shape mismatch → MigrationError.Irreversible (NOT ActionFailed — explicit).
    evalTransform(a.splitter, currentDv) match {
      case Right(composite) =>
        composite match {
          case r: DynamicValue.Record if r.fields.length == a.targetPaths.length =>
            // Dispatch each field to its target path via setOrFail — short-circuit while-loop.
            var current: DynamicValue                        = currentDv
            val targets                                      = a.targetPaths
            val fields                                       = r.fields
            val len                                          = targets.length
            var idx                                          = 0
            var result: Either[MigrationError, DynamicValue] = new Right(currentDv)
            while (idx < len && result.isRight) {
              current.setOrFail(targets(idx), fields(idx)._2) match {
                case rr: Right[_, _] =>
                  current = rr.value.asInstanceOf[DynamicValue]
                  result = rr.asInstanceOf[Either[MigrationError, DynamicValue]]
                case _ =>
                  result = new Left(new MigrationError.ActionFailed(targets(idx), "Split"))
              }
              idx += 1
            }
            result
          case _ =>
            // Arity or shape mismatch — emit Irreversible.
            new Left(new MigrationError.Irreversible(a.at, new Some("split result shape mismatch")))
        }
      case Left(opticCheck) =>
        new Left(new MigrationError.ActionFailed(a.at, "Split", new Some(opticCheck.message)))
    }

  // --- Shared helpers --------------------------------------------------------

  /**
   * TransformValue and ChangeType share the same interpretation shape
   * (the semantic distinction is compile-time-only in the builder
   * macros — the interpreter treats them identically).
   */
  private def applyTransformAt(
    at: DynamicOptic,
    expr: SchemaExpr[_, _],
    currentDv: DynamicValue,
    actionName: String
  ): Either[MigrationError, DynamicValue] =
    currentDv.get(at).values match {
      case Some(values) if values.nonEmpty =>
        val inputValue = values.head
        evalTransform(expr, inputValue) match {
          case Right(outputValue) =>
            currentDv.setOrFail(at, outputValue) match {
              case r: Right[_, _] => r.asInstanceOf[Either[MigrationError, DynamicValue]]
              case _              => new Left(new MigrationError.ActionFailed(at, actionName))
            }
          case Left(opticCheck) =>
            // Preserve the underlying SchemaExpr.evalDynamic failure
            // so callers see the real cause rather
            // than a bare "Action TransformValue failed at ...".
            new Left(new MigrationError.ActionFailed(at, actionName, new Some(opticCheck.message)))
        }
      case _ =>
        // The path does not address a single value (e.g. it points at an
        // Elements traversal or at a non-record sub-tree). Surface this as a
        // SchemaMismatch — `actionName` may be
        // "TransformValue" or "ChangeType".
        new Left(new MigrationError.SchemaMismatch(at, "single value", "no value (path not addressable)"))
    }

  /**
   * Resolves a SchemaExpr used as a default for AddField or DropField.reverse.
   *
   * Special-cases [[SchemaExpr.DefaultValue]] because its own `evalDynamic`
   * always returns `Left` by design (the migration system owns the
   * interpretation). Other SchemaExpr variants delegate to their
   * `evalDynamic(inputDv)`.
   */
  private def resolveDefault(
    expr: SchemaExpr[_, _],
    dv: DynamicValue
  ): Either[OpticCheck, DynamicValue] =
    expr match {
      case d: SchemaExpr.DefaultValue[_] =>
        defaultForSchemaRepr(d.targetSchemaRepr)
      case _ =>
        evalTransform(expr, dv)
    }

  /**
   * Evaluates a non-DefaultValue SchemaExpr by invoking `evalDynamic` with an
   * existential cast (SchemaExpr's input type is erased at the ADT boundary;
   * safe because DynamicValue is the universal carrier at migration time).
   */
  private def evalTransform(
    expr: SchemaExpr[_, _],
    dv: DynamicValue
  ): Either[OpticCheck, DynamicValue] =
    expr.asInstanceOf[SchemaExpr[DynamicValue, DynamicValue]].evalDynamic(dv) match {
      case Right(seq) if seq.nonEmpty => new Right(seq.head)
      case Right(_)                   => defaultResolutionFailure(DynamicOptic.root, "empty evalDynamic result")
      case l: Left[_, _]              => l.asInstanceOf[Either[OpticCheck, DynamicValue]]
    }

  /** Resolves the zero-value of a [[SchemaRepr]] for the primitive scope. */
  private def defaultForSchemaRepr(repr: SchemaRepr): Either[OpticCheck, DynamicValue] =
    repr match {
      case SchemaRepr.Primitive("int")     => new Right(new DynamicValue.Primitive(new PrimitiveValue.Int(0)))
      case SchemaRepr.Primitive("long")    => new Right(new DynamicValue.Primitive(new PrimitiveValue.Long(0L)))
      case SchemaRepr.Primitive("double")  => new Right(new DynamicValue.Primitive(new PrimitiveValue.Double(0.0)))
      case SchemaRepr.Primitive("float")   => new Right(new DynamicValue.Primitive(new PrimitiveValue.Float(0.0f)))
      case SchemaRepr.Primitive("short")   => new Right(new DynamicValue.Primitive(new PrimitiveValue.Short(0.toShort)))
      case SchemaRepr.Primitive("byte")    => new Right(new DynamicValue.Primitive(new PrimitiveValue.Byte(0.toByte)))
      case SchemaRepr.Primitive("boolean") => new Right(new DynamicValue.Primitive(new PrimitiveValue.Boolean(false)))
      case SchemaRepr.Primitive("string")  => new Right(new DynamicValue.Primitive(new PrimitiveValue.String("")))
      case SchemaRepr.Sequence(_)          => new Right(DynamicValue.Sequence.empty)
      case SchemaRepr.Map(_, _)            => new Right(DynamicValue.Map.empty)
      case SchemaRepr.Optional(_)          => new Right(new DynamicValue.Variant("None", DynamicValue.Record.empty))
      case SchemaRepr.Record(_)            => new Right(DynamicValue.Record.empty)
      case SchemaRepr.Variant(cases) if cases.nonEmpty =>
        new Right(new DynamicValue.Variant(cases.head._1, DynamicValue.Record.empty))
      case _ =>
        defaultResolutionFailure(DynamicOptic.root, "unsupported SchemaRepr for primitive default-value resolution")
    }

  private def defaultResolutionFailure(path: DynamicOptic, reason: String): Either[OpticCheck, DynamicValue] =
    new Left(
      new OpticCheck(
        new ::(
          new OpticCheck.WrappingError(path, path, SchemaError.message(reason, path)),
          Nil
        )
      )
    )

  /**
   * Human-readable label for a [[DynamicValue]] kind, used in
   * [[MigrationError.SchemaMismatch]] messages where the runtime shape
   * disagrees with the action's expected shape.
   */
  private def dynamicValueKind(dv: DynamicValue): String = dv match {
    case _: DynamicValue.Primitive => "Primitive"
    case _: DynamicValue.Record    => "Record"
    case _: DynamicValue.Variant   => "Variant"
    case _: DynamicValue.Sequence  => "Sequence"
    case _: DynamicValue.Map       => "Map"
    case DynamicValue.Null         => "Null"
  }

  /**
   * Human-readable label for a [[DynamicOptic.Node]] kind, used in
   * [[MigrationError.SchemaMismatch]] messages where the path's last node is
   * not the expected kind.
   */
  private def dynamicOpticNodeKind(node: DynamicOptic.Node): String = node match {
    case _: DynamicOptic.Node.Field        => "Field"
    case _: DynamicOptic.Node.Case         => "Case"
    case _: DynamicOptic.Node.AtIndex      => "AtIndex"
    case _: DynamicOptic.Node.AtMapKey     => "AtMapKey"
    case _: DynamicOptic.Node.AtIndices    => "AtIndices"
    case _: DynamicOptic.Node.AtMapKeys    => "AtMapKeys"
    case DynamicOptic.Node.Elements        => "Elements"
    case DynamicOptic.Node.MapKeys         => "MapKeys"
    case DynamicOptic.Node.MapValues       => "MapValues"
    case DynamicOptic.Node.Wrapped         => "Wrapped"
    case _: DynamicOptic.Node.TypeSearch   => "TypeSearch"
    case _: DynamicOptic.Node.SchemaSearch => "SchemaSearch"
  }
}
