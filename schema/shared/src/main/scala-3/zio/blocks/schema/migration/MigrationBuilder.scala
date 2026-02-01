package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Builder for constructing type-safe, compile-time validated migrations.
 *
 * Type parameters track which fields have been handled:
 * @tparam A
 *   Source type
 * @tparam B
 *   Target type
 * @tparam SrcRemaining
 *   Field names from A not yet consumed (as tuple of strings)
 * @tparam TgtRemaining
 *   Field names from B not yet provided (as tuple of strings)
 */
final class MigrationBuilder[A, B, SrcRemaining <: Tuple, TgtRemaining <: Tuple] private[migration] (
  private val sourceSchema: Schema[A],
  private val targetSchema: Schema[B],
  private val actions: Vector[MigrationAction]
) {

  // ─────────────────────────────────────────────────────────────────────────
  // Record Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Add a new field to the target schema with a default value.
   */
  def addField[F, Name <: String](
    target: FieldSelector[B, F, Name],
    default: F
  )(using
    ev: FieldSet.Contains[TgtRemaining, Name] =:= true,
    fieldSchema: Schema[F]
  ): MigrationBuilder[A, B, SrcRemaining, FieldSet.Remove[TgtRemaining, Name]] = {
    val resolvedDefault = Resolved.Literal(fieldSchema.toDynamicValue(default))
    val action          = MigrationAction.AddField(DynamicOptic.root, target.name, resolvedDefault)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Add a field with a resolved expression as default.
   */
  def addFieldExpr[F, Name <: String](
    target: FieldSelector[B, F, Name],
    default: Resolved
  )(using
    ev: FieldSet.Contains[TgtRemaining, Name] =:= true
  ): MigrationBuilder[A, B, SrcRemaining, FieldSet.Remove[TgtRemaining, Name]] = {
    val action = MigrationAction.AddField(DynamicOptic.root, target.name, default)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Drop a field from the source schema.
   */
  def dropField[F, Name <: String](
    source: FieldSelector[A, F, Name]
  )(using
    ev: FieldSet.Contains[SrcRemaining, Name] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, Name], TgtRemaining] = {
    val action = MigrationAction.DropField(DynamicOptic.root, source.name, Resolved.Fail("No reverse default"))
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Drop a field with a default value for reverse migration.
   */
  def dropFieldWithDefault[F, Name <: String](
    source: FieldSelector[A, F, Name],
    defaultForReverse: F
  )(using
    ev: FieldSet.Contains[SrcRemaining, Name] =:= true,
    fieldSchema: Schema[F]
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, Name], TgtRemaining] = {
    val resolvedDefault = Resolved.Literal(fieldSchema.toDynamicValue(defaultForReverse))
    val action          = MigrationAction.DropField(DynamicOptic.root, source.name, resolvedDefault)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Rename a field from source to target.
   */
  def renameField[F, SrcName <: String, TgtName <: String](
    from: FieldSelector[A, F, SrcName],
    to: FieldSelector[B, F, TgtName]
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, SrcName] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, TgtName] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, SrcName], FieldSet.Remove[TgtRemaining, TgtName]] = {
    val action = MigrationAction.Rename(DynamicOptic.root, from.name, to.name)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  /**
   * Keep a field unchanged (exists in both schemas with same name and type).
   */
  def keepField[F, Name <: String](
    @scala.annotation.unused field: FieldSelector[A, F, Name]
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, Name] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, Name] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, Name], FieldSet.Remove[TgtRemaining, Name]] =
    new MigrationBuilder(sourceSchema, targetSchema, actions)

  /**
   * Transform a field's value using a primitive conversion.
   */
  def changeFieldType[F1, F2, SrcName <: String, TgtName <: String](
    from: FieldSelector[A, F1, SrcName],
    to: FieldSelector[B, F2, TgtName],
    fromTypeName: String,
    toTypeName: String
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, SrcName] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, TgtName] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, SrcName], FieldSet.Remove[TgtRemaining, TgtName]] = {
    val converter        = Resolved.Convert(fromTypeName, toTypeName, Resolved.Identity)
    val reverseConverter = Resolved.Convert(toTypeName, fromTypeName, Resolved.Identity)

    val renameAction = if (from.name != to.name) {
      Some(MigrationAction.Rename(DynamicOptic.root, from.name, to.name))
    } else None

    val transformAction = MigrationAction.ChangeType(DynamicOptic.root, to.name, converter, reverseConverter)
    new MigrationBuilder(sourceSchema, targetSchema, actions ++ renameAction.toVector :+ transformAction)
  }

  /**
   * Make an optional field mandatory.
   */
  def mandateField[F, SrcName <: String, TgtName <: String](
    source: FieldSelector[A, Option[F], SrcName],
    target: FieldSelector[B, F, TgtName],
    default: F
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, SrcName] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, TgtName] =:= true,
    fieldSchema: Schema[F]
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, SrcName], FieldSet.Remove[TgtRemaining, TgtName]] = {
    val resolvedDefault = Resolved.Literal(fieldSchema.toDynamicValue(default))
    val renameAction    = if (source.name != target.name) {
      Some(MigrationAction.Rename(DynamicOptic.root, source.name, target.name))
    } else None
    val mandateAction = MigrationAction.Mandate(DynamicOptic.root, target.name, resolvedDefault)
    new MigrationBuilder(sourceSchema, targetSchema, actions ++ renameAction.toVector :+ mandateAction)
  }

  /**
   * Make a mandatory field optional.
   */
  def optionalizeField[F, SrcName <: String, TgtName <: String](
    source: FieldSelector[A, F, SrcName],
    target: FieldSelector[B, Option[F], TgtName]
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, SrcName] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, TgtName] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, SrcName], FieldSet.Remove[TgtRemaining, TgtName]] = {
    val renameAction = if (source.name != target.name) {
      Some(MigrationAction.Rename(DynamicOptic.root, source.name, target.name))
    } else None
    val optionalizeAction = MigrationAction.Optionalize(DynamicOptic.root, target.name)
    new MigrationBuilder(sourceSchema, targetSchema, actions ++ renameAction.toVector :+ optionalizeAction)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Enum Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Rename an enum case.
   */
  def renameCase(from: String, to: String): MigrationBuilder[A, B, SrcRemaining, TgtRemaining] = {
    val action = MigrationAction.RenameCase(DynamicOptic.root, from, to)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Nested Migration Operations (with compile-time completeness checking)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Apply a nested migration to a record field.
   *
   * The nested migration must be a complete Migration[F1, F2] built with full
   * compile-time field tracking. This ensures validation at ALL nesting levels.
   *
   * {{{
   * case class AddressV0(street: String)
   * case class AddressV1(street: String, city: String)
   * case class PersonV0(name: String, address: AddressV0)
   * case class PersonV1(name: String, address: AddressV1)
   *
   * val migration = MigrationBuilder.withFieldTracking[PersonV0, PersonV1]
   *   .keepField(select(_.name))
   *   .inField(select[PersonV0](_.address), select[PersonV1](_.address))(
   *     MigrationBuilder.withFieldTracking[AddressV0, AddressV1]
   *       .keepField(select(_.street))
   *       .addField(select(_.city), "Unknown")
   *       .build  // ← Compile-time validation for address fields
   *   )
   *   .build  // ← Compile-time validation for person fields
   * }}}
   */
  def inField[F1, F2, SrcName <: String, TgtName <: String](
    srcField: FieldSelector[A, F1, SrcName],
    tgtField: FieldSelector[B, F2, TgtName]
  )(
    nestedMigration: Migration[F1, F2]
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, SrcName] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, TgtName] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, SrcName], FieldSet.Remove[TgtRemaining, TgtName]] = {
    val renameAction = if (srcField.name != tgtField.name) {
      Some(MigrationAction.Rename(DynamicOptic.root, srcField.name, tgtField.name))
    } else None

    val fieldPath       = DynamicOptic.root.field(tgtField.name)
    val prefixedActions = nestedMigration.dynamicMigration.actions.map(_.prefixPath(fieldPath))

    new MigrationBuilder(sourceSchema, targetSchema, actions ++ renameAction.toVector ++ prefixedActions)
  }

  /**
   * Apply a nested migration to a field with the same name in source and
   * target.
   */
  def inFieldSame[F1, F2, Name <: String](
    field: FieldSelector[A, F1, Name]
  )(
    nestedMigration: Migration[F1, F2]
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, Name] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, Name] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, Name], FieldSet.Remove[TgtRemaining, Name]] = {
    val fieldPath       = DynamicOptic.root.field(field.name)
    val prefixedActions = nestedMigration.dynamicMigration.actions.map(_.prefixPath(fieldPath))
    new MigrationBuilder(sourceSchema, targetSchema, actions ++ prefixedActions)
  }

  /**
   * Apply a nested migration to each element in a sequence field.
   *
   * {{{
   * case class PersonV0(name: String, addresses: List[AddressV0])
   * case class PersonV1(name: String, addresses: List[AddressV1])
   *
   * val addressMigration = MigrationBuilder.withFieldTracking[AddressV0, AddressV1]
   *   .keepField(select(_.street))
   *   .addField(select(_.city), "Unknown")
   *   .build
   *
   * val migration = MigrationBuilder.withFieldTracking[PersonV0, PersonV1]
   *   .keepField(select(_.name))
   *   .inElements(select[PersonV0](_.addresses))(addressMigration)
   *   .build
   * }}}
   */
  def inElements[E1, E2, Name <: String](
    field: FieldSelector[A, ? <: Iterable[E1], Name]
  )(
    nestedMigration: Migration[E1, E2]
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, Name] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, Name] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, Name], FieldSet.Remove[TgtRemaining, Name]] = {
    val fieldPath       = DynamicOptic.root.field(field.name).elements
    val prefixedActions = nestedMigration.dynamicMigration.actions.map(_.prefixPath(fieldPath))
    new MigrationBuilder(sourceSchema, targetSchema, actions ++ prefixedActions)
  }

  /**
   * Apply a nested migration to each value in a map field.
   */
  def inMapValues[K, V1, V2, Name <: String](
    field: FieldSelector[A, ? <: Map[K, V1], Name]
  )(
    nestedMigration: Migration[V1, V2]
  )(using
    srcEv: FieldSet.Contains[SrcRemaining, Name] =:= true,
    tgtEv: FieldSet.Contains[TgtRemaining, Name] =:= true
  ): MigrationBuilder[A, B, FieldSet.Remove[SrcRemaining, Name], FieldSet.Remove[TgtRemaining, Name]] = {
    val fieldPath       = DynamicOptic.root.field(field.name).mapValues
    val prefixedActions = nestedMigration.dynamicMigration.actions.map(_.prefixPath(fieldPath))
    new MigrationBuilder(sourceSchema, targetSchema, actions ++ prefixedActions)
  }

  /**
   * Apply a nested migration to a specific enum case.
   */
  def inCase[C1, C2](
    caseName: String
  )(
    nestedMigration: Migration[C1, C2]
  ): MigrationBuilder[A, B, SrcRemaining, TgtRemaining] = {
    val action = MigrationAction.TransformCase(DynamicOptic.root, caseName, nestedMigration.dynamicMigration.actions)
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Build Methods
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Build the migration with full compile-time validation.
   *
   * Only compiles when ALL source fields are consumed and ALL target fields are
   * provided. This is enforced by requiring evidence that both remaining field
   * sets are EmptyTuple.
   */
  def build(using
    srcEmpty: SrcRemaining =:= EmptyTuple,
    tgtEmpty: TgtRemaining =:= EmptyTuple
  ): Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build migration without completeness validation.
   *
   * Use this for partial migrations or testing when not all fields need to be
   * explicitly handled.
   */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Get the current actions (for debugging/inspection).
   */
  def currentActions: Vector[MigrationAction] = actions

  /**
   * Get a description of the current migration state.
   */
  def describe: String =
    if (actions.isEmpty) "Empty migration builder"
    else actions.map(_.toString).mkString("MigrationBuilder(\n  ", ",\n  ", "\n)")
}

object MigrationBuilder {

  /**
   * Create a new migration builder without compile-time field tracking.
   *
   * This creates a builder with EmptyTuple for both field sets, which means
   * .build will always compile. Use [[withFieldTracking]] for full compile-time
   * validation.
   */
  def create[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, EmptyTuple, EmptyTuple] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  /**
   * Create a new migration builder with compile-time field tracking.
   *
   * This extracts field names from the case class types at compile time,
   * enabling full validation that all fields are handled.
   *
   * The [[build]] method will only compile when:
   *   - All source fields have been consumed (dropped, renamed, or kept)
   *   - All target fields have been provided (added, renamed, or kept)
   *
   * Example:
   * {{{
   * case class PersonV0(name: String, age: Int)
   * case class PersonV1(fullName: String, age: Int, country: String)
   *
   * val migration = MigrationBuilder.withFieldTracking[PersonV0, PersonV1]
   *   .renameField(select(_.name), select(_.fullName))
   *   .keepField(select(_.age))
   *   .addField(select(_.country), "US")
   *   .build  // ✅ Compiles - all fields handled
   * }}}
   */
  transparent inline def withFieldTracking[A, B](using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, ?, ?] =
    ${ withFieldTrackingImpl[A, B]('sourceSchema, 'targetSchema) }

  /**
   * Implementation of withFieldTracking that extracts field names at compile
   * time.
   */
  private def withFieldTrackingImpl[A: scala.quoted.Type, B: scala.quoted.Type](
    sourceSchema: scala.quoted.Expr[Schema[A]],
    targetSchema: scala.quoted.Expr[Schema[B]]
  )(using quotes: scala.quoted.Quotes): scala.quoted.Expr[MigrationBuilder[A, B, ?, ?]] = {
    import quotes.reflect.*

    def extractFieldNames(tpe: TypeRepr): List[String] =
      tpe.classSymbol match {
        case Some(cls) if cls.flags.is(Flags.Case) =>
          cls.primaryConstructor.paramSymss.flatten
            .filter(_.isValDef)
            .map(_.name)
        case _ =>
          Nil
      }

    val srcFields = extractFieldNames(TypeRepr.of[A])
    val tgtFields = extractFieldNames(TypeRepr.of[B])

    def buildTupleType(names: List[String]): TypeRepr =
      names.foldRight(TypeRepr.of[EmptyTuple]) { (name, acc) =>
        val nameType = ConstantType(StringConstant(name))
        TypeRepr.of[*:].appliedTo(List(nameType, acc))
      }

    val srcTupleType = buildTupleType(srcFields)
    val tgtTupleType = buildTupleType(tgtFields)

    (srcTupleType.asType, tgtTupleType.asType) match {
      case ('[s], '[t]) =>
        '{
          MigrationBuilder.initial[A, B, s & Tuple, t & Tuple](
            $sourceSchema,
            $targetSchema
          )
        }
    }
  }

  /**
   * Create a migration builder with explicit field names.
   */
  inline def withFields[A, B](
    inline srcFields: String*
  )(
    inline tgtFields: String*
  )(using
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, ?, ?] =
    ${ withFieldsImpl[A, B]('srcFields, 'tgtFields, 'sourceSchema, 'targetSchema) }

  private def withFieldsImpl[A: scala.quoted.Type, B: scala.quoted.Type](
    srcFields: scala.quoted.Expr[Seq[String]],
    tgtFields: scala.quoted.Expr[Seq[String]],
    sourceSchema: scala.quoted.Expr[Schema[A]],
    targetSchema: scala.quoted.Expr[Schema[B]]
  )(using quotes: scala.quoted.Quotes): scala.quoted.Expr[MigrationBuilder[A, B, ?, ?]] = {
    import quotes.reflect.*

    def extractStrings(expr: scala.quoted.Expr[Seq[String]]): List[String] =
      expr match {
        case scala.quoted.Varargs(exprs) =>
          exprs.toList.map { case '{ $s: String } =>
            s.asTerm match {
              case Literal(StringConstant(str)) => str
              case _                            => report.errorAndAbort("withFields requires string literals")
            }
          }
        case _ =>
          report.errorAndAbort("withFields requires string literals")
      }

    val srcNames = extractStrings(srcFields)
    val tgtNames = extractStrings(tgtFields)

    def buildTupleType(names: List[String]): TypeRepr =
      names.foldRight(TypeRepr.of[EmptyTuple]) { (name, acc) =>
        val nameType = ConstantType(StringConstant(name))
        TypeRepr.of[*:].appliedTo(List(nameType, acc))
      }

    val srcTupleType = buildTupleType(srcNames)
    val tgtTupleType = buildTupleType(tgtNames)

    (srcTupleType.asType, tgtTupleType.asType) match {
      case ('[s], '[t]) =>
        '{
          MigrationBuilder.initial[A, B, s & Tuple, t & Tuple](
            $sourceSchema,
            $targetSchema
          )
        }
    }
  }

  /**
   * Internal constructor for creating a builder with specific field sets. Used
   * by macros and nested migration methods.
   */
  private[migration] def initial[A, B, SrcFields <: Tuple, TgtFields <: Tuple](
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B, SrcFields, TgtFields] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
