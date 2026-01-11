package zio.blocks.schema.migration

import scala.collection.immutable.Vector
import zio.blocks.schema.DynamicValue
import zio.blocks.schema.PrimitiveValue
import zio.blocks.schema.DynamicOptic

object DynamicMigrationInterpreter {

  def apply(
      m: DynamicMigration,
      value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    m match {
      case DynamicMigration.Id => Right(value)

      case DynamicMigration.Sequence(ops) =>
        ops.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
          (acc, op) =>
            acc.flatMap(v => apply(op, v))
        }

      case DynamicMigration.AddField(at, field, default) =>
        modifyRecord(at, value) { fields =>
          if (fields.exists(_._1 == field)) fields
          else fields :+ (field -> default)
        }

      case DynamicMigration.DeleteField(at, field) =>
        modifyRecord(at, value)(_.filterNot(_._1 == field))

      case DynamicMigration.RenameField(at, from, to) =>
        modifyRecord(at, value)(_.map {
          case (`from`, v) => (to, v); case other => other
        })

      case DynamicMigration.WrapInArray(at) =>
        modifyAt(at, value)(v => Right(DynamicValue.Sequence(Vector(v))))

      case DynamicMigration.UnwrapArray(at) =>
        modifyAt(at, value) {
          case DynamicValue.Sequence(values) if values.length == 1 =>
            Right(values(0))

          case other =>
            Left(
              MigrationError.TypeMismatch(
                at,
                "single-element array",
                other.getClass.getSimpleName
              )
            )
        }

      case DynamicMigration.MapEnumCase(at, mapping) =>
        val map = mapping.toMap
        modifyAt(at, value) {
          case DynamicValue.Primitive(PrimitiveValue.String(in)) =>
            mapping.toMap.get(in) match {
              case Some(out) =>
                Right(DynamicValue.Primitive(PrimitiveValue.String(out)))
              case None => Left(MigrationError.UnknownEnumCase(at, in))
            }

          case other =>
            Left(
              MigrationError.TypeMismatch(
                at,
                "string enum",
                other.getClass.getSimpleName
              )
            )
        }

      case DynamicMigration.ConvertPrimitive(at, from, to) =>
        modifyAt(at, value)(v => PrimitiveConversions.convert(at, v, from, to))

      case DynamicMigration.AddFieldDyn(at, field, default) =>
        modifyRecordDyn(at, value) { fields =>
          if (fields.exists(_._1 == field)) fields
          else fields :+ (field -> default)
        }

      case DynamicMigration.DeleteFieldDyn(at, field) =>
        modifyRecordDyn(at, value)(_.filterNot(_._1 == field))

      case DynamicMigration.RenameFieldDyn(at, from, to) =>
        modifyRecordDyn(at, value)(_.map {
          case (`from`, v) => (to, v)
          case other       => other
        })

    }

  // ---------------- helpers ----------------

  // ---------------- helpers (DynamicOptic) ----------------

  private def modifyAt(at: DynamicOptic, root: DynamicValue)(
      f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {

    val nodes: Vector[DynamicOptic.Node] = at.nodes.toVector

    def loop(i: Int, cur: DynamicValue): Either[MigrationError, DynamicValue] =
      if (i >= nodes.length) f(cur)
      else
        nodes(i) match {

          // .field("name")
          case DynamicOptic.Node.Field(name) =>
            cur match {
              case DynamicValue.Record(fields) =>
                val idx = fields.indexWhere(_._1 == name)
                if (idx < 0) Left(MigrationError.MissingPath(at))
                else {
                  val (k, v) = fields(idx)
                  loop(i + 1, v).map(v2 =>
                    DynamicValue.Record(fields.updated(idx, (k, v2)))
                  )
                }

              case other =>
                Left(
                  MigrationError
                    .TypeMismatch(at, "record", other.getClass.getSimpleName)
                )
            }

          // .atIndex(i)
          case DynamicOptic.Node.AtIndex(idx) =>
            cur match {
              case DynamicValue.Sequence(values) =>
                if (idx < 0 || idx >= values.length)
                  Left(MigrationError.MissingPath(at))
                else
                  loop(i + 1, values(idx))
                    .map(v2 => DynamicValue.Sequence(values.updated(idx, v2)))

              case other =>
                Left(
                  MigrationError
                    .TypeMismatch(at, "array", other.getClass.getSimpleName)
                )
            }

          // .elements (aka .each): apply remainder to every element
          case DynamicOptic.Node.Elements =>
            cur match {
              case DynamicValue.Sequence(values) =>
                val updated =
                  values.foldLeft[Either[MigrationError, Vector[DynamicValue]]](
                    Right(Vector.empty)
                  ) { case (acc, elem) =>
                    acc.flatMap { soFar =>
                      loop(i + 1, elem).map(updatedElem => soFar :+ updatedElem)
                    }
                  }
                updated.map(DynamicValue.Sequence.apply)

              case other =>
                Left(
                  MigrationError
                    .TypeMismatch(at, "array", other.getClass.getSimpleName)
                )
            }

          // .case("CaseName") (aka .when[CaseName])
          case DynamicOptic.Node.Case(caseName) =>
            cur match {
              case DynamicValue.Variant(actualName, v) =>
                if (actualName == caseName)
                  loop(i + 1, v).map(v2 => DynamicValue.Variant(actualName, v2))
                else
                  Left(MigrationError.UnknownEnumCase(at, actualName))

              case other =>
                Left(
                  MigrationError
                    .TypeMismatch(at, "variant", other.getClass.getSimpleName)
                )
            }

          // These exist in DynamicOptic, but you can add support later when you need it.
          case DynamicOptic.Node.AtMapKey(_) =>
            Left(
              MigrationError
                .InvalidOp("DynamicOptic", "AtMapKey not supported yet")
            )

          case DynamicOptic.Node.MapKeys =>
            Left(
              MigrationError
                .InvalidOp("DynamicOptic", "MapKeys not supported yet")
            )

          case DynamicOptic.Node.MapValues =>
            Left(
              MigrationError
                .InvalidOp("DynamicOptic", "MapValues not supported yet")
            )

          case DynamicOptic.Node.Wrapped =>
            Left(
              MigrationError
                .InvalidOp("DynamicOptic", "Wrapped not supported yet")
            )

          case DynamicOptic.Node.AtIndices(_) =>
            Left(
              MigrationError
                .InvalidOp("DynamicOptic", "AtIndices not supported yet")
            )

          case DynamicOptic.Node.AtMapKeys(_) =>
            Left(
              MigrationError
                .InvalidOp("DynamicOptic", "AtMapKeys not supported yet")
            )
        }

    loop(0, root)
  }

  private def modifyRecord(at: DynamicOptic, root: DynamicValue)(
      f: Vector[(String, DynamicValue)] => Vector[(String, DynamicValue)]
  ): Either[MigrationError, DynamicValue] =
    modifyAt(at, root) {
      case DynamicValue.Record(fields) => Right(DynamicValue.Record(f(fields)))
      case other =>
        Left(
          MigrationError.TypeMismatch(
            at,
            "record",
            other.getClass.getSimpleName
          )
        )
    }

    // ---------------- DynamicOptic helpers (NEW) ----------------

  private def modifyAtDyn(at: DynamicOptic, root: DynamicValue)(
      f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {

    // DynamicOptic stores a sequence of nodes describing where we are
    val nodes: Seq[DynamicOptic.Node] = at.nodes

    def loop(
        idx: Int,
        cur: DynamicValue
    ): Either[MigrationError, DynamicValue] =
      if (idx >= nodes.length) f(cur)
      else
        nodes(idx) match {

          // .field
          case DynamicOptic.Node.Field(name) =>
            cur match {
              case DynamicValue.Record(fields) =>
                val i = fields.indexWhere(_._1 == name)
                if (i < 0)
                  Left(
                    MigrationError.MissingPath(Path(Vector.empty))
                  ) // see note below
                else {
                  val (k, v) = fields(i)
                  loop(idx + 1, v)
                    .map(v2 => DynamicValue.Record(fields.updated(i, (k, v2))))
                }
              case other =>
                Left(
                  MigrationError.TypeMismatch(
                    Path(Vector.empty),
                    "record",
                    other.getClass.getSimpleName
                  )
                )
            }

          // .at(index)
          case DynamicOptic.Node.AtIndex(i) =>
            cur match {
              case DynamicValue.Sequence(values) =>
                if (i < 0 || i >= values.length)
                  Left(MigrationError.MissingPath(Path(Vector.empty)))
                else
                  loop(idx + 1, values(i))
                    .map(v2 => DynamicValue.Sequence(values.updated(i, v2)))
              case other =>
                Left(
                  MigrationError.TypeMismatch(
                    Path(Vector.empty),
                    "array",
                    other.getClass.getSimpleName
                  )
                )
            }

          // .elements (aka .each)
          // Apply the rest of the optic to every element
          case DynamicOptic.Node.Elements =>
            cur match {
              case DynamicValue.Sequence(values) =>
                // apply remaining nodes to each element
                val updatedEither =
                  values.foldLeft[Either[MigrationError, Vector[DynamicValue]]](
                    Right(Vector.empty)
                  ) { case (acc, elem) =>
                    acc.flatMap { soFar =>
                      loop(idx + 1, elem)
                        .map(updatedElem => soFar :+ updatedElem)
                    }
                  }
                updatedEither.map(updated => DynamicValue.Sequence(updated))
              case other =>
                Left(
                  MigrationError.TypeMismatch(
                    Path(Vector.empty),
                    "array",
                    other.getClass.getSimpleName
                  )
                )
            }

          // .when[CaseName] (represented as Case("X"))
          // For DynamicValue.Variant(caseName, value), enter value only if names match
          case DynamicOptic.Node.Case(caseName) =>
            cur match {
              case DynamicValue.Variant(name, v) =>
                if (name == caseName)
                  loop(idx + 1, v).map(v2 => DynamicValue.Variant(name, v2))
                else
                  Left(
                    MigrationError.InvalidOp(
                      "When",
                      s"expected case '$caseName' but got '$name'"
                    )
                  )
              case other =>
                Left(
                  MigrationError.TypeMismatch(
                    Path(Vector.empty),
                    "variant",
                    other.getClass.getSimpleName
                  )
                )
            }

          // Map optics — only add if you need them now.
          case DynamicOptic.Node.AtMapKey(_) =>
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                "AtMapKey not supported by this interpreter yet"
              )
            )

          case DynamicOptic.Node.MapKeys =>
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                "MapKeys not supported by this interpreter yet"
              )
            )

          case DynamicOptic.Node.MapValues =>
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                "MapValues not supported by this interpreter yet"
              )
            )

          case DynamicOptic.Node.Wrapped =>
            Left(
              MigrationError.InvalidOp(
                "DynamicOptic",
                "Wrapped not supported by this interpreter yet"
              )
            )
        }

    loop(0, root)
  }

  private def modifyRecordDyn(at: DynamicOptic, root: DynamicValue)(
      f: Vector[(String, DynamicValue)] => Vector[(String, DynamicValue)]
  ): Either[MigrationError, DynamicValue] =
    modifyAtDyn(at, root) {
      case DynamicValue.Record(fields) => Right(DynamicValue.Record(f(fields)))
      case other =>
        Left(
          MigrationError.InvalidOp(
            "DynamicOptic",
            s"Expected record but got ${other.getClass.getSimpleName}"
          )
        )
    }

  private object PrimitiveConversions {
    import DynamicMigration.Primitive

    def convert(
        at: DynamicOptic,
        v: DynamicValue,
        from: Primitive,
        to: Primitive
    ): Either[MigrationError, DynamicValue] = {

      def mismatch(got: DynamicValue) =
        Left(
          MigrationError.TypeMismatch(at, s"$from", got.getClass.getSimpleName)
        )

      (from, to, v) match {
        case (
              Primitive.Int,
              Primitive.Long,
              DynamicValue.Primitive(PrimitiveValue.Int(n))
            ) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(n.toLong)))

        case (
              Primitive.Long,
              Primitive.String,
              DynamicValue.Primitive(PrimitiveValue.Long(n))
            ) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(n.toString)))

        case (
              Primitive.Int,
              Primitive.String,
              DynamicValue.Primitive(PrimitiveValue.Int(n))
            ) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(n.toString)))

        case (
              Primitive.String,
              Primitive.Int,
              DynamicValue.Primitive(PrimitiveValue.String(s))
            ) =>
          s.toIntOption match {
            case Some(i) => Right(DynamicValue.Primitive(PrimitiveValue.Int(i)))
            case None =>
              Left(
                MigrationError.InvalidOp(
                  "ConvertPrimitive",
                  s"cannot parse Int from '$s'"
                )
              )
          }
        case (_, _, got) =>
          mismatch(got)

      }

    }
  }
}
