package zio.blocks.schema.migration

import scala.collection.immutable.Vector
import zio.blocks.schema.DynamicValue
import zio.blocks.schema.PrimitiveValue

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
    }

  // ---------------- helpers ----------------

  private def modifyAt(at: Path, root: DynamicValue)(
      f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {

    def loop(
        path: Vector[Path.Segment],
        cur: DynamicValue
    ): Either[MigrationError, DynamicValue] =
      path.headOption match {
        case None => f(cur)

        case Some(Path.Segment.Field(name)) =>
          cur match {
            case DynamicValue.Record(fields) =>
              val idx = fields.indexWhere(_._1 == name)
              if (idx < 0) Left(MigrationError.MissingPath(at))
              else {
                val (k, v) = fields(idx)
                loop(path.tail, v).map(v2 =>
                  DynamicValue.Record(fields.updated(idx, (k, v2)))
                )
              }
            case other =>
              Left(
                MigrationError.TypeMismatch(
                  at,
                  "record",
                  other.getClass.getSimpleName
                )
              )
          }

        case Some(Path.Segment.Index(i)) =>
          cur match {
            case DynamicValue.Sequence(values) =>
              if (i < 0 || i >= values.length)
                Left(MigrationError.MissingPath(at))
              else
                loop(path.tail, values(i)).map(v2 =>
                  DynamicValue.Sequence(values.updated(i, v2))
                )
            case other =>
              Left(
                MigrationError.TypeMismatch(
                  at,
                  "array",
                  other.getClass.getSimpleName
                )
              )
          }
      }

    loop(at.segments, root)
  }

  private def modifyRecord(at: Path, root: DynamicValue)(
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

  private object PrimitiveConversions {
    import DynamicMigration.Primitive

    def convert(
        at: Path,
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

      }

    }
  }
}
