package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, SchemaExpr, ToStructural}
import zio.blocks.schema.CompanionOptics.optic
import zio.blocks.schema.DynamicOptic

import scala.quoted.*
import zio.blocks.schema.migration.MigrationAction.*

/**
 * Macro-backed, typed migration builder (issue #519).
 *
 * Notes:
 * - User supplies *selectors* (A => Any, B => Any), never optics.
 * - Selectors are macro-compiled into DynamicOptic paths.
 * - The resulting migration is pure data: Vector[MigrationAction].
 */
final class MigrationBuilder[A, B](
    val sourceSchema: Schema[A],
    val targetSchema: Schema[B],
    val actions: Vector[MigrationAction]
) { self =>

  // ----------------------------
  // Record operations
  // ----------------------------

  inline def addField(inline target: B => Any, inline default: SchemaExpr[A, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.addFieldImpl[A, B]('self, 'target, 'default) }

  inline def dropField(
      inline source: A => Any,
      inline defaultForReverse: SchemaExpr[B, _] = MigrationSchemaExpr.default
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.dropFieldImpl[A, B]('self, 'source, 'defaultForReverse) }

  inline def renameField(inline from: A => Any, inline to: B => Any): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.renameFieldImpl[A, B]('self, 'from, 'to) }

  inline def transformField(inline from: A => Any, inline to: B => Any, inline transform: SchemaExpr[A, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformFieldImpl[A, B]('self, 'from, 'to, 'transform) }

  inline def mandateField(inline source: A => Option[?], inline target: B => Any, inline default: SchemaExpr[A, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.mandateFieldImpl[A, B]('self, 'source, 'target, 'default) }

  inline def optionalizeField(inline source: A => Any, inline target: B => Option[?]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.optionalizeFieldImpl[A, B]('self, 'source, 'target) }

  inline def changeFieldType(inline source: A => Any, inline target: B => Any, inline converter: SchemaExpr[A, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.changeFieldTypeImpl[A, B]('self, 'source, 'target, 'converter) }

  // ----------------------------
  // Enum operations (limited)
  // ----------------------------

  def renameCase[SumA, SumB](from: String, to: String): MigrationBuilder[A, B] =
    copyAppended(RenameCase(at = DynamicOptic.root, from = from, to = to))

  inline def transformCase[SumA, CaseA, SumB, CaseB](
      inline caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  )(using
      sa: Schema[CaseA],
      sb: Schema[CaseB]
  ): MigrationBuilder[A, B] = {
    // case migrations are nested, so we just collect their actions and embed them.
    val nested = caseMigration(new MigrationBuilder[CaseA, CaseB](sa, sb, Vector.empty))
    copyAppended(TransformCase(at = DynamicOptic.root, actions = nested.actions))
  }

  // ----------------------------
  // Collections / Maps
  // ----------------------------

  inline def transformElements(inline at: A => Vector[?], inline transform: SchemaExpr[A, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformElementsImpl[A, B]('self, 'at, 'transform) }

  inline def transformKeys(inline at: A => Map[?, ?], inline transform: SchemaExpr[A, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformKeysImpl[A, B]('self, 'at, 'transform) }

  inline def transformValues(inline at: A => Map[?, ?], inline transform: SchemaExpr[A, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformValuesImpl[A, B]('self, 'at, 'transform) }

  // ----------------------------
  // Build
  // ----------------------------

  /** Build migration with validation (shape + constraints). */
  def build(using ToStructural[A], ToStructural[B]): Migration[A, B] = {
    val prog = DynamicMigration(actions)
    MigrationValidator.validateOrThrow(prog, sourceSchema, targetSchema)
    Migration.fromProgram[A, B](prog)(using sourceSchema, targetSchema, summon[ToStructural[A]], summon[ToStructural[B]])
  }

  /** Build migration without validation. */
  def buildPartial(using ToStructural[A], ToStructural[B]): Migration[A, B] = {
    val prog = DynamicMigration(actions)
    Migration.fromProgram[A, B](prog)(using sourceSchema, targetSchema, summon[ToStructural[A]], summon[ToStructural[B]])
  }

  // ----------------------------
  // Internals
  // ----------------------------

  private[migration] def copyAppended(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
}

private object MigrationBuilderMacros {

  // ----------------------------
  // Shared helpers
  // ----------------------------

  private def extractDynamicOptic[S: Type, T: Type](
      selector: Expr[S => T],
      schema: Expr[Schema[S]]
  )(using Quotes): Expr[DynamicOptic] =
    // We rely on the existing `optic` macro and then convert to DynamicOptic.
    // `optic` validates the selector syntax (field access, .each, .when[Case], etc).
    '{ optic[S, T](${selector})(using ${schema}).dynamic }


  private def lastFieldName(at: Expr[DynamicOptic], ctx: String)(using Quotes): Expr[String] = {
    import quotes.reflect.*
    '{
      $at.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(name)) => name
        case other =>
          throw new IllegalArgumentException(s"$ctx: selector must end in a field, got: " + other)
      }
    }
  }

  private def parentOfField(at: Expr[DynamicOptic], ctx: String)(using Quotes): Expr[DynamicOptic] =
    '{
      val ns = $at.nodes
      ns.lastOption match {
        case Some(_: DynamicOptic.Node.Field) =>
          DynamicOptic(ns.dropRight(1).toVector)
        case _ =>
          throw new IllegalArgumentException(s"$ctx: selector must end in a field")
      }
    }

  private def ensureFieldSelectorEnd(at: Expr[DynamicOptic], ctx: String)(using Quotes): Unit =
    import quotes.reflect.*
    ()

  private def append[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      action: Expr[MigrationAction]
  )(using Quotes): Expr[MigrationBuilder[A, B]] =
    '{ $builder.copyAppended($action) }

  // ----------------------------
  // addField
  // ----------------------------

  def addFieldImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      targetSel: Expr[B => Any],
      defaultExpr: Expr[SchemaExpr[A, _]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val sb        = '{ $builder.targetSchema }
    val targetDyn = extractDynamicOptic[B, Any](targetSel, sb)
    val nameE     = lastFieldName(targetDyn, "addField(target)")
    val parentE   = parentOfField(targetDyn, "addField(target)")
    val atField   = '{ $parentE.field($nameE) }

    // Capture the *field schema* (target field type) and turn DefaultValue marker into DefaultValueFromSchema(fieldSchema)
    val fieldTpe: TypeRepr =
      targetSel.asTerm match {
        case Inlined(_, _, Lambda(_, body)) => body.tpe.widen
        case other                          => report.errorAndAbort(s"addField(target): expected a lambda selector, got: ${other.show}")
      }

    fieldTpe.asType match {
      case '[t] =>
        val fieldSchema: Expr[Schema[t]] =
          Expr.summon[Schema[t]].getOrElse {
            report.errorAndAbort(s"addField(target): could not summon Schema[${Type.show[t]}] to capture default")
          }

        val capturedDefault: Expr[SchemaExpr[A, _]] =
          '{ MigrationSchemaExpr.captureDefaultIfMarker[A, t]($defaultExpr.asInstanceOf[SchemaExpr[A, t]], $fieldSchema) }

        append(builder, '{ AddField(at = $atField, default = $capturedDefault) })
    }
  }

  // ----------------------------
  // dropField
  // ----------------------------

  def dropFieldImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      sourceSel: Expr[A => Any],
      defaultForReverseExpr: Expr[SchemaExpr[B, _]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val sa        = '{ $builder.sourceSchema }
    val sourceDyn = extractDynamicOptic[A, Any](sourceSel, sa)
    val nameE     = lastFieldName(sourceDyn, "dropField(source)")
    val parentE   = parentOfField(sourceDyn, "dropField(source)")
    val atField   = '{ $parentE.field($nameE) }

    // Capture schema from the *source field type* for reverse default when needed.
    val fieldTpe: TypeRepr =
      sourceSel.asTerm match {
        case Inlined(_, _, Lambda(_, body)) => body.tpe.widen
        case other                          => report.errorAndAbort(s"dropField(source): expected a lambda selector, got: ${other.show}")
      }

    fieldTpe.asType match {
      case '[t] =>
        val fieldSchema: Expr[Schema[t]] =
          Expr.summon[Schema[t]].getOrElse {
            report.errorAndAbort(s"dropField(source): could not summon Schema[${Type.show[t]}] to capture reverse default")
          }

        val capturedReverse: Expr[SchemaExpr[B, _]] =
          '{ MigrationSchemaExpr.captureDefaultIfMarker[B, t]($defaultForReverseExpr.asInstanceOf[SchemaExpr[B, t]], $fieldSchema) }

        append(builder, '{ DropField(at = $atField, defaultForReverse = $capturedReverse) })
    }
  }

  // ----------------------------
  // renameField
  // ----------------------------

  def renameFieldImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      fromSel: Expr[A => Any],
      toSel: Expr[B => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sa      = '{ $builder.sourceSchema }
    val sb      = '{ $builder.targetSchema }
    val fromDyn = extractDynamicOptic[A, Any](fromSel, sa)
    val toDyn   = extractDynamicOptic[B, Any](toSel, sb)

    val fromNameE = lastFieldName(fromDyn, "renameField(from)")
    val toNameE   = lastFieldName(toDyn, "renameField(to)")
    val parentDyn = parentOfField(fromDyn, "renameField(from)")

    val atField: Expr[DynamicOptic] = '{ $parentDyn.field($fromNameE) }
    append(builder, '{ Rename(at = $atField, to = $toNameE) })
  }

  // ----------------------------
  // transformField (value transform + optional rename)
  // ----------------------------

  def transformFieldImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      fromSel: Expr[A => Any],
      toSel: Expr[B => Any],
      transform: Expr[SchemaExpr[A, _]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sa      = '{ $builder.sourceSchema }
    val sb      = '{ $builder.targetSchema }
    val fromDyn = extractDynamicOptic[A, Any](fromSel, sa)
    val toDyn   = extractDynamicOptic[B, Any](toSel, sb)

    val fromNameE = lastFieldName(fromDyn, "transformField(from)")
    val toNameE   = lastFieldName(toDyn, "transformField(to)")
    val parentDyn = parentOfField(fromDyn, "transformField(from)")
    val atField   = '{ $parentDyn.field($fromNameE) }

    // `transformField` in the suggested design can also rename; we always emit a Rename (no-op if same name),
    // then apply the value transform at the (original) field location.
    '{
      $builder
        .copyAppended(Rename(at = $atField, to = $toNameE))
        .copyAppended(TransformValue(at = $atField, transform = $transform))
    }
  }

  // ----------------------------
  // mandateField
  // ----------------------------

  def mandateFieldImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      sourceSel: Expr[A => Option[?]],
      targetSel: Expr[B => Any],
      defaultExpr: Expr[SchemaExpr[A, _]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val sa        = '{ $builder.sourceSchema }
    val sb        = '{ $builder.targetSchema }
    val sourceDyn = extractDynamicOptic[A, Any](sourceSel.asInstanceOf[Expr[A => Any]], sa)
    val targetDyn = extractDynamicOptic[B, Any](targetSel, sb)

    val sourceNameE = lastFieldName(sourceDyn, "mandateField(source)")
    val targetNameE = lastFieldName(targetDyn, "mandateField(target)")
    val sourceParen = parentOfField(sourceDyn, "mandateField(source)")
    val atField     = '{ $sourceParen.field($sourceNameE) }

    // Capture target field schema for DefaultValue marker
    val fieldTpe: TypeRepr =
      targetSel.asTerm match {
        case Inlined(_, _, Lambda(_, body)) => body.tpe.widen
        case other                          => report.errorAndAbort(s"mandateField(target): expected a lambda selector, got: ${other.show}")
      }

    fieldTpe.asType match {
      case '[t] =>
        val fieldSchema: Expr[Schema[t]] =
          Expr.summon[Schema[t]].getOrElse {
            report.errorAndAbort(s"mandateField: could not summon Schema[${Type.show[t]}] to capture default")
          }

        val capturedDefault: Expr[SchemaExpr[A, _]] =
          '{ MigrationSchemaExpr.captureDefaultIfMarker[A, t]($defaultExpr.asInstanceOf[SchemaExpr[A, t]], $fieldSchema) }

        // Rename (if needed) happens first, then Mandate at the (renamed) location.
        '{
          $builder
            .copyAppended(Rename(at = $atField, to = $targetNameE))
            .copyAppended(Mandate(at = $atField, default = $capturedDefault))
        }
    }
  }

  // ----------------------------
  // optionalizeField
  // ----------------------------

  def optionalizeFieldImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      sourceSel: Expr[A => Any],
      targetSel: Expr[B => Option[?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sa        = '{ $builder.sourceSchema }
    val sb        = '{ $builder.targetSchema }
    val sourceDyn = extractDynamicOptic[A, Any](sourceSel, sa)
    val targetDyn = extractDynamicOptic[B, Any](targetSel.asInstanceOf[Expr[B => Any]], sb)

    val sourceNameE = lastFieldName(sourceDyn, "optionalizeField(source)")
    val targetNameE = lastFieldName(targetDyn, "optionalizeField(target)")
    val parentDyn   = parentOfField(sourceDyn, "optionalizeField(source)")
    val atField     = '{ $parentDyn.field($sourceNameE) }

    '{
      $builder
        .copyAppended(Rename(at = $atField, to = $targetNameE))
        .copyAppended(Optionalize(at = $atField))
    }
  }

  // ----------------------------
  // changeFieldType
  // ----------------------------

  def changeFieldTypeImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      sourceSel: Expr[A => Any],
      targetSel: Expr[B => Any],
      converter: Expr[SchemaExpr[A, _]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sa        = '{ $builder.sourceSchema }
    val sb        = '{ $builder.targetSchema }
    val sourceDyn = extractDynamicOptic[A, Any](sourceSel, sa)
    val targetDyn = extractDynamicOptic[B, Any](targetSel, sb)

    val sourceNameE = lastFieldName(sourceDyn, "changeFieldType(source)")
    val targetNameE = lastFieldName(targetDyn, "changeFieldType(target)")
    val parentDyn   = parentOfField(sourceDyn, "changeFieldType(source)")
    val atField     = '{ $parentDyn.field($sourceNameE) }

    '{
      $builder
        .copyAppended(Rename(at = $atField, to = $targetNameE))
        .copyAppended(ChangeType(at = $atField, converter = $converter))
    }
  }

  // ----------------------------
  // transformElements / keys / values
  // ----------------------------

  def transformElementsImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      atSel: Expr[A => Vector[?]],
      transform: Expr[SchemaExpr[A, _]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sa  = '{ $builder.sourceSchema }
    val dyn = extractDynamicOptic[A, Any](atSel.asInstanceOf[Expr[A => Any]], sa)
    append(builder, '{ TransformElements(at = $dyn, transform = $transform) })
  }

  def transformKeysImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      atSel: Expr[A => Map[?, ?]],
      transform: Expr[SchemaExpr[A, _]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sa  = '{ $builder.sourceSchema }
    val dyn = extractDynamicOptic[A, Any](atSel.asInstanceOf[Expr[A => Any]], sa)
    append(builder, '{ TransformKeys(at = $dyn, transform = $transform) })
  }

  def transformValuesImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      atSel: Expr[A => Map[?, ?]],
      transform: Expr[SchemaExpr[A, _]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sa  = '{ $builder.sourceSchema }
    val dyn = extractDynamicOptic[A, Any](atSel.asInstanceOf[Expr[A => Any]], sa)
    append(builder, '{ TransformValues(at = $dyn, transform = $transform) })
  }
}
