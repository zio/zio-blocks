package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, PrimitiveType, Reflect}
import zio.blocks.typeid.Owner

/**
 * Simplified structural representation of a schema, used for symbolic execution
 * during `.build()` validation.
 *
 * `SchemaShape` captures the structural essence of a `Reflect` tree without
 * bindings, type IDs, or constructors — just the shape of the data. This
 * enables comparing source-after-migration against target to detect mismatches
 * before runtime.
 *
 * The `Dyn` case acts as a wildcard: it matches any shape in `compareShapes`,
 * providing a safe fallback when shape inference is incomplete.
 */
sealed trait SchemaShape

object SchemaShape {
  final case class Record(fields: Vector[(String, SchemaShape)])  extends SchemaShape
  final case class Variant(cases: Vector[(String, SchemaShape)])  extends SchemaShape
  final case class Sequence(element: SchemaShape)                 extends SchemaShape
  final case class MapShape(key: SchemaShape, value: SchemaShape) extends SchemaShape
  final case class Prim(primitiveType: PrimitiveType[_])          extends SchemaShape
  final case class Opt(inner: SchemaShape)                        extends SchemaShape
  final case class Wrap(inner: SchemaShape)                       extends SchemaShape
  case object Dyn                                                 extends SchemaShape

  /**
   * Extracts a `SchemaShape` from a `Reflect` tree.
   *
   * Detects `Option`-like variants (cases named "Some" and "None") and
   * represents them as `Opt(inner)` rather than a general `Variant`.
   */
  def fromReflect[F[_, _], A](r: Reflect[F, A]): SchemaShape = r match {
    case rec: Reflect.Record[_, _] =>
      Record(rec.fields.map(t => (t.name, fromReflect(t.value))).toVector)

    case v: Reflect.Variant[_, _] =>
      if (isOptionVariant(v)) {
        // The "Some" case is a Record with a single field "value" containing the inner type.
        // Use the same extraction as Reflect.optionInnerType: cases(1).value.asRecord.fields(0).value
        val someCase     = v.cases(1)
        val innerReflect = someCase.value.asRecord.map(_.fields(0).value).getOrElse(someCase.value)
        Opt(fromReflect(innerReflect))
      } else {
        Variant(v.cases.map(t => (t.name, fromReflect(t.value))).toVector)
      }

    case s: Reflect.Sequence[_, _, _] =>
      Sequence(fromReflect(s.element))

    case m: Reflect.Map[_, _, _, _] =>
      MapShape(fromReflect(m.key), fromReflect(m.value))

    case p: Reflect.Primitive[_, _] =>
      Prim(p.primitiveType)

    case w: Reflect.Wrapper[_, _, _] =>
      Wrap(fromReflect(w.wrapped))

    case _: Reflect.Dynamic[_] =>
      Dyn

    case d: Reflect.Deferred[_, _] =>
      fromReflect(d.value)
  }

  /**
   * Compares two shapes structurally, returning a list of mismatch
   * descriptions. An empty list means the shapes are compatible.
   *
   * `Dyn` matches any shape (permissive fallback for unknown expressions).
   *
   * @param wrapTransparent
   *   If `true`, `Wrap(x)` is considered equivalent to `x`.
   */
  def compareShapes(
    actual: SchemaShape,
    expected: SchemaShape,
    path: String = "",
    wrapTransparent: Boolean = false
  ): List[MigrationError] = {
    val (unwrappedActual, unwrappedExpected) =
      if (wrapTransparent) (unwrap(actual), unwrap(expected))
      else (actual, expected)

    (unwrappedActual, unwrappedExpected) match {
      case (Dyn, _) => Nil
      case (_, Dyn) => Nil

      case (Record(aFields), Record(eFields)) =>
        val aMap    = aFields.toMap
        val eMap    = eFields.toMap
        val missing = (eMap.keySet -- aMap.keySet).toList.sorted.map { n =>
          MigrationError(
            s"missing target field '$n'",
            pathToOptic(path),
            expectedShape = Some(truncate(eMap(n).toString, 200))
          )
        }
        val extra = (aMap.keySet -- eMap.keySet).toList.sorted.map { n =>
          MigrationError(
            s"unaccounted source field '$n'",
            pathToOptic(path),
            actualShape = Some(truncate(aMap(n).toString, 200))
          )
        }
        val nested = (aMap.keySet intersect eMap.keySet).toList.sorted.flatMap { name =>
          compareShapes(aMap(name), eMap(name), s"$path.$name", wrapTransparent)
        }
        missing ++ extra ++ nested

      case (Variant(aCases), Variant(eCases)) =>
        val aMap    = aCases.toMap
        val eMap    = eCases.toMap
        val missing = (eMap.keySet -- aMap.keySet).toList.sorted.map { n =>
          MigrationError(
            s"missing target case '$n'",
            pathToOptic(path),
            expectedShape = Some(truncate(eMap(n).toString, 200))
          )
        }
        val extra = (aMap.keySet -- eMap.keySet).toList.sorted.map { n =>
          MigrationError(
            s"unaccounted source case '$n'",
            pathToOptic(path),
            actualShape = Some(truncate(aMap(n).toString, 200))
          )
        }
        val nested = (aMap.keySet intersect eMap.keySet).toList.sorted.flatMap { name =>
          compareShapes(aMap(name), eMap(name), s"$path<$name>", wrapTransparent)
        }
        missing ++ extra ++ nested

      case (Sequence(a), Sequence(b)) =>
        compareShapes(a, b, s"$path[*]", wrapTransparent)

      case (MapShape(ak, av), MapShape(bk, bv)) =>
        compareShapes(ak, bk, s"$path{key}", wrapTransparent) ++
          compareShapes(av, bv, s"$path{value}", wrapTransparent)

      case (Prim(a), Prim(b)) if a == b => Nil

      case (Opt(a), Opt(b)) =>
        compareShapes(a, b, path, wrapTransparent)

      case (Wrap(a), Wrap(b)) =>
        compareShapes(a, b, s"$path.~", wrapTransparent)

      case _ =>
        List(
          MigrationError(
            s"shape mismatch",
            pathToOptic(path),
            actualShape = Some(truncate(unwrappedActual.toString, 200)),
            expectedShape = Some(truncate(unwrappedExpected.toString, 200))
          )
        )
    }
  }

  /**
   * Symbolically applies a `MigrationAction` to a `SchemaShape`, producing the
   * transformed shape or a list of validation errors.
   *
   * This is the core of `.build()` validation — it simulates each action on the
   * shape without actually transforming data.
   */
  def applyAction(shape: SchemaShape, action: MigrationAction): Either[List[MigrationError], SchemaShape] =
    action match {
      case MigrationAction.AddField(at, _) =>
        MigrationAction.lastFieldName(at) match {
          case Some(name) =>
            modifyRecordAt(shape, MigrationAction.parentPath(at)) { fields =>
              if (fields.exists(_._1 == name))
                Left(List(MigrationError(s"Field '$name' already exists", at)))
              else
                Right(fields :+ (name, Dyn))
            }
          case None =>
            Left(List(MigrationError("AddField path must end with a Field node", at)))
        }

      case MigrationAction.DropField(at, _) =>
        MigrationAction.lastFieldName(at) match {
          case Some(name) =>
            modifyRecordAt(shape, MigrationAction.parentPath(at)) { fields =>
              if (!fields.exists(_._1 == name))
                Left(List(MigrationError(s"Field '$name' not found", at)))
              else
                Right(fields.filterNot(_._1 == name))
            }
          case None =>
            Left(List(MigrationError("DropField path must end with a Field node", at)))
        }

      case MigrationAction.Rename(at, newName) =>
        MigrationAction.lastFieldName(at) match {
          case Some(oldName) =>
            modifyRecordAt(shape, MigrationAction.parentPath(at)) { fields =>
              if (!fields.exists(_._1 == oldName))
                Left(List(MigrationError(s"Field '$oldName' not found for rename", at)))
              else if (fields.exists(_._1 == newName))
                Left(List(MigrationError(s"Field '$newName' already exists; cannot rename to it", at)))
              else
                Right(fields.map { case (n, s) => if (n == oldName) (newName, s) else (n, s) })
            }
          case None =>
            Left(List(MigrationError("Rename path must end with a Field node", at)))
        }

      case MigrationAction.TransformValue(at, _, _) =>
        modifyShapeAt(shape, at)(_ => Right(Dyn))

      case MigrationAction.Mandate(at, _) =>
        modifyShapeAt(shape, at) {
          case Opt(inner) => Right(inner)
          case other      =>
            Left(
              List(
                MigrationError(
                  s"Expected optional shape",
                  at,
                  actualShape = Some(truncate(other.toString, 200))
                )
              )
            )
        }

      case MigrationAction.Optionalize(at) =>
        modifyShapeAt(shape, at)(inner => Right(Opt(inner)))

      case MigrationAction.ChangeType(at, _, _) =>
        modifyShapeAt(shape, at)(_ => Right(Dyn))

      case MigrationAction.RenameCase(at, fromName, toName) =>
        modifyVariantAt(shape, at) { cases =>
          if (!cases.exists(_._1 == fromName))
            Left(List(MigrationError(s"Case '$fromName' not found in variant", at)))
          else if (cases.exists(_._1 == toName))
            Left(List(MigrationError(s"Case '$toName' already exists in variant", at)))
          else
            Right(cases.map { case (n, s) => if (n == fromName) (toName, s) else (n, s) })
        }

      case MigrationAction.TransformCase(at, caseName, subActions) =>
        modifyVariantAt(shape, at) { cases =>
          cases.find(_._1 == caseName) match {
            case None =>
              Left(List(MigrationError(s"Case '$caseName' not found in variant", at)))
            case Some((_, caseShape)) =>
              val transformed = subActions.foldLeft[Either[List[MigrationError], SchemaShape]](Right(caseShape)) {
                case (Right(s), action) => applyAction(s, action)
                case (left, _)          => left
              }
              transformed.map { newShape =>
                cases.map { case (n, s) => if (n == caseName) (n, newShape) else (n, s) }
              }
          }
        }

      case MigrationAction.TransformElements(at, _, _) =>
        modifyShapeAt(shape, at) {
          case Sequence(_) => Right(Sequence(Dyn))
          case other       =>
            Left(
              List(
                MigrationError(
                  s"Expected sequence shape",
                  at,
                  actualShape = Some(truncate(other.toString, 200))
                )
              )
            )
        }

      case MigrationAction.TransformKeys(at, _, _) =>
        modifyShapeAt(shape, at) {
          case MapShape(_, v) => Right(MapShape(Dyn, v))
          case other          =>
            Left(
              List(
                MigrationError(
                  s"Expected map shape",
                  at,
                  actualShape = Some(truncate(other.toString, 200))
                )
              )
            )
        }

      case MigrationAction.TransformValues(at, _, _) =>
        modifyShapeAt(shape, at) {
          case MapShape(k, _) => Right(MapShape(k, Dyn))
          case other          =>
            Left(
              List(
                MigrationError(
                  s"Expected map shape",
                  at,
                  actualShape = Some(truncate(other.toString, 200))
                )
              )
            )
        }

      case MigrationAction.Join(at, sourcePaths, _, _, targetShape) =>
        val sourceErrors = sourcePaths.flatMap { sp =>
          resolveShapeAt(shape, sp) match {
            case None => Some(MigrationError(s"Join source path does not exist", sp))
            case _    => None
          }
        }
        if (sourceErrors.nonEmpty) Left(sourceErrors.toList)
        else {
          val afterRemoval = sourcePaths.foldLeft[Either[List[MigrationError], SchemaShape]](Right(shape)) {
            case (Right(s), sp) => removeFieldAt(s, sp)
            case (left, _)      => left
          }
          afterRemoval.flatMap { s =>
            MigrationAction.lastFieldName(at) match {
              case Some(name) =>
                modifyRecordAt(s, MigrationAction.parentPath(at)) { fields =>
                  if (fields.exists(_._1 == name))
                    Left(List(MigrationError(s"Join target '$name' already exists", at)))
                  else
                    Right(fields :+ (name, targetShape))
                }
              case None =>
                Left(List(MigrationError("Join target path must end with a Field node", at)))
            }
          }
        }

      case MigrationAction.Split(at, targetPaths, _, _, targetShapes) =>
        resolveShapeAt(shape, at) match {
          case None =>
            Left(List(MigrationError(s"Split source path does not exist", at)))
          case Some(_) =>
            removeFieldAt(shape, at).flatMap { s =>
              targetPaths.zip(targetShapes).foldLeft[Either[List[MigrationError], SchemaShape]](Right(s)) {
                case (Right(current), (tp, tShape)) =>
                  MigrationAction.lastFieldName(tp) match {
                    case Some(name) =>
                      modifyRecordAt(current, MigrationAction.parentPath(tp)) { fields =>
                        if (fields.exists(_._1 == name))
                          Left(List(MigrationError(s"Split target '$name' already exists", tp)))
                        else
                          Right(fields :+ (name, tShape))
                      }
                    case None =>
                      Left(List(MigrationError("Split target path must end with a Field node", tp)))
                  }
                case (left, _) => left
              }
            }
        }
    }

  // ── Path navigation helpers ────────────────────────────────────────────

  /**
   * Resolves the shape at a given DynamicOptic path, returning None if not
   * found.
   */
  def resolveShapeAt(shape: SchemaShape, path: DynamicOptic): Option[SchemaShape] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return Some(shape)

    var current = shape
    var i       = 0
    while (i < nodes.length) {
      val next: Option[SchemaShape] = nodes(i) match {
        case DynamicOptic.Node.Field(name) =>
          current match {
            case Record(fields) => fields.find(_._1 == name).map(_._2)
            case _              => None
          }
        case DynamicOptic.Node.Case(name) =>
          current match {
            case Variant(cases) => cases.find(_._1 == name).map(_._2)
            case _              => None
          }
        case DynamicOptic.Node.Elements =>
          current match {
            case Sequence(elem) => Some(elem)
            case _              => None
          }
        case DynamicOptic.Node.MapKeys =>
          current match {
            case MapShape(k, _) => Some(k)
            case _              => None
          }
        case DynamicOptic.Node.MapValues =>
          current match {
            case MapShape(_, v) => Some(v)
            case _              => None
          }
        case DynamicOptic.Node.Wrapped =>
          current match {
            case Wrap(inner) => Some(inner)
            case _           => None
          }
        case _ => None // AtIndex, AtMapKey etc. — not used in shape navigation
      }
      next match {
        case Some(s) =>
          current = s
          i += 1
        case None =>
          return None
      }
    }
    Some(current)
  }

  /**
   * Modifies a record's fields at a given path. The path should point to the
   * record containing the fields to modify.
   */
  private def modifyRecordAt(
    shape: SchemaShape,
    path: DynamicOptic
  )(
    f: Vector[(String, SchemaShape)] => Either[List[MigrationError], Vector[(String, SchemaShape)]]
  ): Either[List[MigrationError], SchemaShape] =
    modifyShapeAt(shape, path) {
      case Record(fields) => f(fields).map(Record(_))
      case other          =>
        Left(
          List(
            MigrationError(
              s"Expected record shape",
              path,
              actualShape = Some(truncate(other.toString, 200))
            )
          )
        )
    }

  /**
   * Modifies a variant's cases at a given path.
   */
  private def modifyVariantAt(
    shape: SchemaShape,
    path: DynamicOptic
  )(
    f: Vector[(String, SchemaShape)] => Either[List[MigrationError], Vector[(String, SchemaShape)]]
  ): Either[List[MigrationError], SchemaShape] =
    modifyShapeAt(shape, path) {
      case Variant(cases) => f(cases).map(Variant(_))
      case other          =>
        Left(
          List(
            MigrationError(
              s"Expected variant shape",
              path,
              actualShape = Some(truncate(other.toString, 200))
            )
          )
        )
    }

  /**
   * Navigates to a shape at a DynamicOptic path and applies a transformation.
   * If the path is empty (root), transforms the root shape directly.
   */
  private def modifyShapeAt(
    shape: SchemaShape,
    path: DynamicOptic
  )(
    f: SchemaShape => Either[List[MigrationError], SchemaShape]
  ): Either[List[MigrationError], SchemaShape] = {
    val nodes = path.nodes
    if (nodes.isEmpty) return f(shape)
    modifyShapeAtNodes(shape, nodes, 0, path)(f)
  }

  private def modifyShapeAtNodes(
    shape: SchemaShape,
    nodes: IndexedSeq[DynamicOptic.Node],
    index: Int,
    fullPath: DynamicOptic
  )(
    f: SchemaShape => Either[List[MigrationError], SchemaShape]
  ): Either[List[MigrationError], SchemaShape] = {
    if (index >= nodes.length) return f(shape)

    nodes(index) match {
      case DynamicOptic.Node.Field(name) =>
        shape match {
          case Record(fields) =>
            fields.indexWhere(_._1 == name) match {
              case -1 =>
                Left(List(MigrationError(s"Field '$name' not found in record", fullPath)))
              case idx =>
                modifyShapeAtNodes(fields(idx)._2, nodes, index + 1, fullPath)(f).map { newShape =>
                  Record(fields.updated(idx, (name, newShape)))
                }
            }
          case other =>
            Left(
              List(
                MigrationError(
                  s"Expected record shape at field '$name'",
                  fullPath,
                  actualShape = Some(truncate(other.toString, 200))
                )
              )
            )
        }

      case DynamicOptic.Node.Case(name) =>
        shape match {
          case Variant(cases) =>
            cases.indexWhere(_._1 == name) match {
              case -1 =>
                Left(List(MigrationError(s"Case '$name' not found in variant", fullPath)))
              case idx =>
                modifyShapeAtNodes(cases(idx)._2, nodes, index + 1, fullPath)(f).map { newShape =>
                  Variant(cases.updated(idx, (name, newShape)))
                }
            }
          case other =>
            Left(
              List(
                MigrationError(
                  s"Expected variant shape at case '$name'",
                  fullPath,
                  actualShape = Some(truncate(other.toString, 200))
                )
              )
            )
        }

      case DynamicOptic.Node.Elements =>
        shape match {
          case Sequence(elem) =>
            modifyShapeAtNodes(elem, nodes, index + 1, fullPath)(f).map(Sequence(_))
          case other =>
            Left(
              List(
                MigrationError(
                  s"Expected sequence shape at elements",
                  fullPath,
                  actualShape = Some(truncate(other.toString, 200))
                )
              )
            )
        }

      case DynamicOptic.Node.MapKeys =>
        shape match {
          case MapShape(k, v) =>
            modifyShapeAtNodes(k, nodes, index + 1, fullPath)(f).map(MapShape(_, v))
          case other =>
            Left(
              List(
                MigrationError(
                  s"Expected map shape at keys",
                  fullPath,
                  actualShape = Some(truncate(other.toString, 200))
                )
              )
            )
        }

      case DynamicOptic.Node.MapValues =>
        shape match {
          case MapShape(k, v) =>
            modifyShapeAtNodes(v, nodes, index + 1, fullPath)(f).map(MapShape(k, _))
          case other =>
            Left(
              List(
                MigrationError(
                  s"Expected map shape at values",
                  fullPath,
                  actualShape = Some(truncate(other.toString, 200))
                )
              )
            )
        }

      case DynamicOptic.Node.Wrapped =>
        shape match {
          case Wrap(inner) =>
            modifyShapeAtNodes(inner, nodes, index + 1, fullPath)(f).map(Wrap(_))
          case other =>
            Left(
              List(
                MigrationError(
                  s"Expected wrapper shape",
                  fullPath,
                  actualShape = Some(truncate(other.toString, 200))
                )
              )
            )
        }

      case other =>
        Left(List(MigrationError(s"Unsupported path node for shape navigation: $other", fullPath)))
    }
  }

  /** Removes a field at a path from the shape. */
  def removeFieldAt(shape: SchemaShape, path: DynamicOptic): Either[List[MigrationError], SchemaShape] =
    MigrationAction.lastFieldName(path) match {
      case Some(name) =>
        modifyRecordAt(shape, MigrationAction.parentPath(path)) { fields =>
          if (!fields.exists(_._1 == name))
            Left(List(MigrationError(s"Field '$name' not found for removal", path)))
          else
            Right(fields.filterNot(_._1 == name))
        }
      case None =>
        Left(List(MigrationError("Remove path must end with a Field node", path)))
    }

  /**
   * Detects if a Variant represents an Option type, matching Reflect.isOption
   * logic.
   */
  private def isOptionVariant[F[_, _], A](v: Reflect.Variant[F, A]): Boolean = {
    val tid   = v.typeId
    val cases = v.cases
    tid.owner == Owner.fromPackagePath("scala") && tid.name == "Option" &&
    cases.length == 2 && cases(1).name == "Some"
  }

  /** Recursively unwraps Wrap nodes. */
  private def unwrap(shape: SchemaShape): SchemaShape = shape match {
    case Wrap(inner) => unwrap(inner)
    case other       => other
  }

  /** Converts a string path like ".foo.bar" to a DynamicOptic. */
  private def pathToOptic(path: String): DynamicOptic =
    if (path.isEmpty) DynamicOptic.root
    else {
      val parts = path.split("\\.").filter(_.nonEmpty)
      parts.foldLeft(DynamicOptic.root) { (optic, part) =>
        if (part.startsWith("<") && part.endsWith(">"))
          optic.caseOf(part.substring(1, part.length - 1))
        else if (part == "[*]")
          optic.elements
        else if (part == "{key}")
          optic // Approximation — MapKeys path not directly representable
        else if (part == "{value}")
          optic // Approximation — MapValues path not directly representable
        else if (part == "~")
          optic.wrapped
        else
          optic.field(part)
      }
    }

  /**
   * Truncates a string to the given max length, appending "..." if truncated.
   */
  private[migration] def truncate(s: String, maxLen: Int): String =
    if (s.length <= maxLen) s
    else s.take(maxLen - 3) + "..."
}
