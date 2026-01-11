package zio.blocks.schema

/**
 * A `Migration` represents a structural transformation between two schema versions.
 * Migrations are pure, algebraic data that can be:
 *   - serialized and stored
 *   - composed with other migrations
 *   - applied to transform values from one schema version to another
 *   - reversed to enable downgrade paths
 *
 * The migration system works with structural types (records and enums) and allows
 * users to evolve schemas without keeping old case classes in their codebase.
 *
 * {{{
 * val migration =
 *   Migration.newBuilder[PersonV0, Person]
 *     .addField(_.age, 0)
 *     .renameField(_.firstName, _.fullName)
 *     .build
 *
 * migration.apply(oldValue) // Right(newValue)
 * }}}
 */
sealed trait Migration[A, B] { self =>

  /**
   * Compose this migration with another migration to create a new migration
   * that applies both transformations sequentially.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration.Compose(self, that)

  /**
   * Apply this migration to transform a DynamicValue.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]

  /**
   * Reverse this migration to create a migration that goes in the opposite direction.
   * Note: Not all migrations are perfectly reversible. Some information may be lost.
   */
  def reverse: Migration[B, A]
}

object Migration extends MigrationCompanionVersionSpecific {

  /**
   * Identity migration that doesn't change anything.
   */
  case class Identity[A]() extends Migration[A, A] {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      Right(value)

    def reverse: Migration[A, A] = this
  }

  /**
   * Composition of two migrations applied sequentially.
   */
  final case class Compose[A, B, C](
    first: Migration[A, B],
    second: Migration[B, C]
  ) extends Migration[A, C] {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      first.apply(value).flatMap(second.apply)

    def reverse: Migration[C, A] =
      Compose(second.reverse, first.reverse)
  }

  /**
   * Add a new field to a record with a default value.
   */
  final case class AddField(
    fieldName: String,
    defaultValue: DynamicValue
  ) extends Migration[Any, Any] {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          // Check if field already exists
          if (fields.exists(_._1 == fieldName)) {
            Left(MigrationError.FieldAlreadyExists(DynamicOptic.root.field(fieldName), fieldName))
          } else {
            Right(DynamicValue.Record(fields :+ (fieldName, defaultValue)))
          }
        case _ =>
          Left(MigrationError.NotARecord(DynamicOptic.root))
      }

    def reverse: Migration[Any, Any] =
      RemoveField(fieldName)
  }

  /**
   * Remove a field from a record.
   */
  final case class RemoveField(
    fieldName: String
  ) extends Migration[Any, Any] {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          val (found, remaining) = fields.partition(_._1 == fieldName)
          if (found.isEmpty) {
            Left(MigrationError.FieldNotFound(DynamicOptic.root.field(fieldName), fieldName))
          } else {
            Right(DynamicValue.Record(remaining))
          }
        case _ =>
          Left(MigrationError.NotARecord(DynamicOptic.root))
      }

    // Reverse requires knowledge of the default value, which we don't have
    // This is a "lossy" reverse - the original value is lost
    def reverse: Migration[Any, Any] =
      AddField(fieldName, DynamicValue.Record(Vector.empty))
  }

  /**
   * Rename a field in a record.
   */
  final case class RenameField(
    from: String,
    to: String
  ) extends Migration[Any, Any] {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          val idx = fields.indexWhere(_._1 == from)
          if (idx < 0) {
            Left(MigrationError.FieldNotFound(DynamicOptic.root.field(from), from))
          } else if (fields.exists(_._1 == to)) {
            Left(MigrationError.FieldAlreadyExists(DynamicOptic.root.field(to), to))
          } else {
            val updated = fields.updated(idx, (to, fields(idx)._2))
            Right(DynamicValue.Record(updated))
          }
        case _ =>
          Left(MigrationError.NotARecord(DynamicOptic.root))
      }

    def reverse: Migration[Any, Any] =
      RenameField(to, from)
  }

  /**
   * Transform a value at a specific path using a function.
   */
  final case class TransformValue(
    optic: DynamicOptic,
    transform: DynamicValue => Either[MigrationError, DynamicValue],
    reverseTransform: DynamicValue => Either[MigrationError, DynamicValue]
  ) extends Migration[Any, Any] {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      applyAtPath(value, optic.nodes, 0)

    private def applyAtPath(
      value: DynamicValue,
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[MigrationError, DynamicValue] = {
      if (idx >= nodes.length) {
        transform(value)
      } else {
        nodes(idx) match {
          case DynamicOptic.Node.Field(name) =>
            value match {
              case DynamicValue.Record(fields) =>
                val fieldIdx = fields.indexWhere(_._1 == name)
                if (fieldIdx < 0) {
                  Left(MigrationError.FieldNotFound(optic, name))
                } else {
                  applyAtPath(fields(fieldIdx)._2, nodes, idx + 1).map { newValue =>
                    DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
                  }
                }
              case _ =>
                Left(MigrationError.NotARecord(optic))
            }

          case DynamicOptic.Node.Elements =>
            value match {
              case DynamicValue.Sequence(elements) =>
                val results = elements.map(e => applyAtPath(e, nodes, idx + 1))
                val errors = results.collect { case Left(e) => e }
                if (errors.nonEmpty) {
                  Left(errors.head)
                } else {
                  Right(DynamicValue.Sequence(results.collect { case Right(v) => v }))
                }
              case _ =>
                Left(MigrationError.NotASequence(optic))
            }

          case DynamicOptic.Node.MapValues =>
            value match {
              case DynamicValue.Map(entries) =>
                val results = entries.map { case (k, v) =>
                  applyAtPath(v, nodes, idx + 1).map(newV => (k, newV))
                }
                val errors = results.collect { case Left(e) => e }
                if (errors.nonEmpty) {
                  Left(errors.head)
                } else {
                  Right(DynamicValue.Map(results.collect { case Right(kv) => kv }))
                }
              case _ =>
                Left(MigrationError.NotAMap(optic))
            }

          case _ =>
            Left(MigrationError.UnsupportedOpticNode(optic))
        }
      }
    }

    def reverse: Migration[Any, Any] =
      TransformValue(optic, reverseTransform, transform)
  }

  /**
   * Reorder fields in a record according to a new field order.
   */
  final case class ReorderFields(
    newOrder: Vector[String]
  ) extends Migration[Any, Any] {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          val reordered = newOrder.flatMap(name => fieldMap.get(name).map(v => (name, v)))
          // Add any fields not in newOrder at the end
          val remaining = fields.filterNot(f => newOrder.contains(f._1))
          Right(DynamicValue.Record(reordered ++ remaining))
        case _ =>
          Left(MigrationError.NotARecord(DynamicOptic.root))
      }

    def reverse: Migration[Any, Any] =
      this // Reordering is self-reversible if we track the original order
  }

  /**
   * Add a new case to an enum/variant type.
   */
  final case class AddCase(
    caseName: String
  ) extends Migration[Any, Any] {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      Right(value) // Adding a case doesn't affect existing values

    def reverse: Migration[Any, Any] =
      RemoveCase(caseName)
  }

  /**
   * Remove a case from an enum/variant type.
   */
  final case class RemoveCase(
    caseName: String,
    fallback: Option[(String, DynamicValue)] = None
  ) extends Migration[Any, Any] {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Variant(name, _) if name == caseName =>
          fallback match {
            case Some((newName, newValue)) =>
              Right(DynamicValue.Variant(newName, newValue))
            case None =>
              Left(MigrationError.CaseRemoved(DynamicOptic.root.caseOf(caseName), caseName))
          }
        case _ =>
          Right(value)
      }

    def reverse: Migration[Any, Any] =
      AddCase(caseName)
  }

  /**
   * Rename a case in an enum/variant type.
   */
  final case class RenameCase(
    from: String,
    to: String
  ) extends Migration[Any, Any] {

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Variant(name, inner) if name == from =>
          Right(DynamicValue.Variant(to, inner))
        case _ =>
          Right(value)
      }

    def reverse: Migration[Any, Any] =
      RenameCase(to, from)
  }

  // ============== Builder API ==============

  /**
   * Create a new migration builder for transforming from type A to type B.
   */
  def newBuilder[A, B]: MigrationBuilder[A, B] =
    new MigrationBuilder[A, B](Vector.empty)

  /**
   * Create an identity migration.
   */
  def identity[A]: Migration[A, A] = Identity[A]()

  /**
   * Create a migration that adds a field.
   */
  def addField(name: String, default: DynamicValue): Migration[Any, Any] =
    AddField(name, default)

  /**
   * Create a migration that removes a field.
   */
  def removeField(name: String): Migration[Any, Any] =
    RemoveField(name)

  /**
   * Create a migration that renames a field.
   */
  def renameField(from: String, to: String): Migration[Any, Any] =
    RenameField(from, to)

  /**
   * Derive a migration between two types A and B using explicit actions.
   */
  def derive[A, B](actions: Migration[Any, Any]*): Migration[A, B] =
    actions.foldLeft[Migration[Any, Any]](Migration.Identity[Any]())((acc, action) => acc ++ action).asInstanceOf[Migration[A, B]]
}

/**
 * Builder for constructing migrations incrementally with a type-safe API.
 */
final class MigrationBuilder[A, B] private[schema] (
  private val actions: Vector[Migration[Any, Any]]
) extends MigrationBuilderVersionSpecific[A, B] {

  /**
   * Add a field with a default value.
   */
  def addField(name: String, default: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ Migration.AddField(name, default))

  /**
   * Add a field with a typed default value.
   */
  def addField[T](name: String, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
    addField(name, schema.toDynamicValue(default))

  /**
   * Remove a field.
   */
  def removeField(name: String): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ Migration.RemoveField(name))

  /**
   * Rename a field.
   */
  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ Migration.RenameField(from, to))

  /**
   * Reorder fields.
   */
  def reorderFields(newOrder: String*): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ Migration.ReorderFields(newOrder.toVector))

  /**
   * Add a case to an enum.
   */
  def addCase(name: String): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ Migration.AddCase(name))

  /**
   * Remove a case from an enum.
   */
  def removeCase(name: String): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ Migration.RemoveCase(name))

  /**
   * Remove a case with a fallback.
   */
  def removeCase(name: String, fallbackCase: String, fallbackValue: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ Migration.RemoveCase(name, Some((fallbackCase, fallbackValue))))

  /**
   * Rename a case.
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ Migration.RenameCase(from, to))

  /**
   * Transform a value at a path.
   */
  def transform(
    optic: DynamicOptic,
    f: DynamicValue => Either[MigrationError, DynamicValue],
    reverse: DynamicValue => Either[MigrationError, DynamicValue]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(actions :+ Migration.TransformValue(optic, f, reverse))

  /**
   * Build the final migration.
   */
  def build: Migration[A, B] = {
    if (actions.isEmpty) {
      Migration.Identity().asInstanceOf[Migration[A, B]]
    } else {
      actions.reduceLeft[Migration[Any, Any]](_ ++ _).asInstanceOf[Migration[A, B]]
    }
  }
}
