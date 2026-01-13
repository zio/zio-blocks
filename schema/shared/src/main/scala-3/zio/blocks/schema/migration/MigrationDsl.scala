package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, ToStructural, SchemaExpr}
import zio.blocks.schema.CompanionOptics.optic
import zio.blocks.schema.Optic as ZOptic
import zio.blocks.schema.DynamicOptic

import scala.quoted.*
import zio.blocks.schema.migration.MigrationAction.*

/** Typed DSL that compiles to PURE DynamicMigration data (Vector[MigrationAction]). */
object MigrationDsl {

  /** Builder used only during macro-expanded program construction. */
  final class MigrationBuilder[A] private[migration] (
      private[migration] var ops: List[MigrationAction]
  ) {
    private[migration] def add(op: MigrationAction): Unit =
      ops = op :: ops
  }

  object MigrationBuilder {
    def empty[A]: MigrationBuilder[A] = new MigrationBuilder[A](Nil)
  }

  inline def buildPartial[A, B](
      inline f: MigrationBuilder[A] ?=> Unit
  )(using sa: Schema[A], sb: Schema[B]): Migration[A, B] =
    ${ migrationImpl[A, B]('f, 'sa, 'sb, validate = Expr(false)) }

  inline def build[A, B](
      inline f: MigrationBuilder[A] ?=> Unit
  )(using sa: Schema[A], sb: Schema[B]): Migration[A, B] =
    ${ migrationImpl[A, B]('f, 'sa, 'sb, validate = Expr(true)) }

  object ops {

    // ─────────────────────────────────────────────
    // Core field ops (recommended)
    // ─────────────────────────────────────────────

    inline def renameField[A, B](inline fromSel: A => Any, inline toSel: B => Any)(using
        b: MigrationBuilder[A]
    ): Unit =
      ${ renameFieldSelectorsImpl[A, B]('fromSel, 'toSel, 'b, 'summon[Schema[A]], 'summon[Schema[B]]) }

    inline def addFieldExpr[A, B](
        inline targetSel: B => Any,
        inline defaultExpr: SchemaExpr[Any, Any]
    )(using b: MigrationBuilder[A], sb: Schema[B]): Unit =
      ${ addFieldExprImpl[A, B]('targetSel, 'defaultExpr, 'b, 'sb) }

    /** Drop field; reverse uses DefaultValue marker (resolved by interpreter with target schema). */
    inline def dropField[A](
        inline sourceSel: A => Any
    )(using b: MigrationBuilder[A], sa: Schema[A]): Unit =
      ${ dropFieldImpl[A]('sourceSel, 'b, 'sa) }

    // ─────────────────────────────────────────────
    // Missing #519 ops (added)
    // ─────────────────────────────────────────────

    /** Transform (and optionally rename) a field value. */
    inline def transformField[A, B](
        inline fromSel: A => Any,
        inline toSel: B => Any,
        inline transform: SchemaExpr[Any, Any]
    )(using b: MigrationBuilder[A], sa: Schema[A], sb: Schema[B]): Unit =
      ${ transformFieldImpl[A, B]('fromSel, 'toSel, 'transform, 'b, 'sa, 'sb) }

    /** Optionalize (and optionally rename) a field value. */
    inline def optionalizeField[A, B](
        inline fromSel: A => Any,
        inline toSel: B => Any
    )(using b: MigrationBuilder[A], sa: Schema[A], sb: Schema[B]): Unit =
      ${ optionalizeFieldImpl[A, B]('fromSel, 'toSel, 'b, 'sa, 'sb) }

    /** Mandate (and optionally rename) a field value (Option -> Required). */
    inline def mandateField[A, B](
        inline fromSel: A => Any,
        inline toSel: B => Any,
        inline defaultExpr: SchemaExpr[Any, Any]
    )(using b: MigrationBuilder[A], sa: Schema[A], sb: Schema[B]): Unit =
      ${ mandateFieldImpl[A, B]('fromSel, 'toSel, 'defaultExpr, 'b, 'sa, 'sb) }

    /** Change primitive field type (and optionally rename). */
    inline def changeFieldType[A, B](
        inline fromSel: A => Any,
        inline toSel: B => Any,
        inline converter: SchemaExpr[Any, Any]
    )(using b: MigrationBuilder[A], sa: Schema[A], sb: Schema[B]): Unit =
      ${ changeFieldTypeImpl[A, B]('fromSel, 'toSel, 'converter, 'b, 'sa, 'sb) }

    /**
     * Join multiple fields into one target field.
     * Convention: combiner input is a record with fields: _0, _1, _2...
     */
    inline def joinFields[A, B](
        inline targetSel: B => Any,
        inline source1: A => Any,
        inline source2: A => Any,
        inline rest: Seq[A => Any],
        inline combiner: SchemaExpr[Any, Any]
    )(using b: MigrationBuilder[A], sa: Schema[A], sb: Schema[B]): Unit =
      ${ joinFieldsImpl[A, B]('targetSel, 'source1, 'source2, 'rest, 'combiner, 'b, 'sa, 'sb) }

    /**
     * Split one field into multiple target fields.
     * Convention: splitter output is a record whose field names match the target field names.
     */
    inline def splitField[A, B](
        inline sourceSel: A => Any,
        inline target1: B => Any,
        inline target2: B => Any,
        inline rest: Seq[B => Any],
        inline splitter: SchemaExpr[Any, Any]
    )(using b: MigrationBuilder[A], sa: Schema[A], sb: Schema[B]): Unit =
      ${ splitFieldImpl[A, B]('sourceSel, 'target1, 'target2, 'rest, 'splitter, 'b, 'sa, 'sb) }

    inline def renameCase[A](
        inline enumSel: A => Any,
        from: String,
        to: String
    )(using b: MigrationBuilder[A], sa: Schema[A]): Unit =
      ${ renameCaseImpl[A]('enumSel, 'from, 'to, 'b, 'sa) }

    /**
     * Transform a specific case. User must include `.when[Case]` in selector,
     * so the DynamicOptic contains Node.Case("CaseName").
     */
    inline def transformCase[A](
        inline caseSel: A => Any,
        inline f: MigrationBuilder[A] ?=> Unit
    )(using b: MigrationBuilder[A], sa: Schema[A]): Unit =
      ${ transformCaseImpl[A]('caseSel, 'f, 'b, 'sa) }
  }

  // ─────────────────────────────────────────────
  // Macro implementation
  // ─────────────────────────────────────────────

  private def migrationImpl[A: Type, B: Type](
      f: Expr[MigrationBuilder[A] ?=> Unit],
      sa: Expr[Schema[A]],
      sb: Expr[Schema[B]],
      validate: Expr[Boolean]
  )(using Quotes): Expr[Migration[A, B]] =
    '{
      val b = MigrationBuilder.empty[A]
      $f(using b)

      val program = DynamicMigration(b.ops.reverse.toVector)

      if ($validate) {
        MigrationValidator.validateOrThrow(program, $sa.structural, $sb.structural)
      }

      Migration.fromProgram[A, B](program)(using
        $sa,
        $sb,
        summon[ToStructural[A]],
        summon[ToStructural[B]]
      )
    }

  private def renameFieldSelectorsImpl[A: Type, B: Type](
      fromSel: Expr[A => Any],
      toSel: Expr[B => Any],
      b: Expr[MigrationBuilder[A]],
      sa: Expr[Schema[A]],
      sb: Expr[Schema[B]]
  )(using Quotes): Expr[Unit] = {
    val fromDyn = extractDynamicOptic[A, Any](fromSel, sa)
    val toDyn   = extractDynamicOptic[B, Any](toSel, sb)

    val fromNameE = lastFieldName(fromDyn, "renameField(fromSel)")
    val toNameE   = lastFieldName(toDyn, "renameField(toSel)")
    val parentDyn = parentOfField(fromDyn, "renameField(fromSel)")

    val atField: Expr[DynamicOptic] = '{ $parentDyn.field($fromNameE) }
    '{ $b.add(Rename(at = $atField, to = $toNameE)) }
  }

  private def addFieldExprImpl[A: Type, B: Type](
      targetSel: Expr[B => Any],
      defaultExpr: Expr[SchemaExpr[Any, Any]],
      b: Expr[MigrationBuilder[A]],
      sb: Expr[Schema[B]]
  )(using Quotes): Expr[Unit] = {
    val targetDyn = extractDynamicOptic[B, Any](targetSel, sb)
    val nameE     = lastFieldName(targetDyn, "addFieldExpr(targetSel)")
    val parentE   = parentOfField(targetDyn, "addFieldExpr(targetSel)")

    val atField: Expr[DynamicOptic] = '{ $parentE.field($nameE) }
    '{ $b.add(AddField(at = $atField, default = $defaultExpr)) }
  }

  private def dropFieldImpl[A: Type](
      sourceSel: Expr[A => Any],
      b: Expr[MigrationBuilder[A]],
      sa: Expr[Schema[A]]
  )(using Quotes): Expr[Unit] = {
    val sourceDyn = extractDynamicOptic[A, Any](sourceSel, sa)
    val nameE     = lastFieldName(sourceDyn, "dropField(sourceSel)")
    val parentE   = parentOfField(sourceDyn, "dropField(sourceSel)")

    val atField: Expr[DynamicOptic] = '{ $parentE.field($nameE) }
    val defaultExpr: Expr[SchemaExpr[Any, Any]] =
      '{ MigrationSchemaExpr.default[Any, Any].asInstanceOf[SchemaExpr[Any, Any]] }

    '{ $b.add(DropField(at = $atField, defaultForReverse = $defaultExpr)) }
  }

  private def transformFieldImpl[A: Type, B: Type](
      fromSel: Expr[A => Any],
      toSel: Expr[B => Any],
      transform: Expr[SchemaExpr[Any, Any]],
      b: Expr[MigrationBuilder[A]],
      sa: Expr[Schema[A]],
      sb: Expr[Schema[B]]
  )(using Quotes): Expr[Unit] = {
    val fromDyn = extractDynamicOptic[A, Any](fromSel, sa)
    val toDyn   = extractDynamicOptic[B, Any](toSel, sb)

    val fromNameE = lastFieldName(fromDyn, "transformField(fromSel)")
    val toNameE   = lastFieldName(toDyn, "transformField(toSel)")
    val parentDyn = parentOfField(fromDyn, "transformField(fromSel)")

    val atFrom: Expr[DynamicOptic] = '{ $parentDyn.field($fromNameE) }

    // If rename is needed, do it first, then transform at the renamed location.
    '{
      if ($fromNameE != $toNameE) {
        $b.add(Rename(at = $atFrom, to = $toNameE))
        val newAt = $parentDyn.field($toNameE)
        $b.add(TransformValue(at = newAt, transform = $transform))
      } else {
        $b.add(TransformValue(at = $atFrom, transform = $transform))
      }
    }
  }

  private def optionalizeFieldImpl[A: Type, B: Type](
      fromSel: Expr[A => Any],
      toSel: Expr[B => Any],
      b: Expr[MigrationBuilder[A]],
      sa: Expr[Schema[A]],
      sb: Expr[Schema[B]]
  )(using Quotes): Expr[Unit] = {
    val fromDyn = extractDynamicOptic[A, Any](fromSel, sa)
    val toDyn   = extractDynamicOptic[B, Any](toSel, sb)

    val fromNameE = lastFieldName(fromDyn, "optionalizeField(fromSel)")
    val toNameE   = lastFieldName(toDyn, "optionalizeField(toSel)")
    val parentDyn = parentOfField(fromDyn, "optionalizeField(fromSel)")

    val atFrom: Expr[DynamicOptic] = '{ $parentDyn.field($fromNameE) }

    '{
      if ($fromNameE != $toNameE) {
        $b.add(Rename(at = $atFrom, to = $toNameE))
        val newAt = $parentDyn.field($toNameE)
        $b.add(Optionalize(at = newAt))
      } else {
        $b.add(Optionalize(at = $atFrom))
      }
    }
  }

  private def mandateFieldImpl[A: Type, B: Type](
      fromSel: Expr[A => Any],
      toSel: Expr[B => Any],
      defaultExpr: Expr[SchemaExpr[Any, Any]],
      b: Expr[MigrationBuilder[A]],
      sa: Expr[Schema[A]],
      sb: Expr[Schema[B]]
  )(using Quotes): Expr[Unit] = {
    val fromDyn = extractDynamicOptic[A, Any](fromSel, sa)
    val toDyn   = extractDynamicOptic[B, Any](toSel, sb)

    val fromNameE = lastFieldName(fromDyn, "mandateField(fromSel)")
    val toNameE   = lastFieldName(toDyn, "mandateField(toSel)")
    val parentDyn = parentOfField(fromDyn, "mandateField(fromSel)")

    val atFrom: Expr[DynamicOptic] = '{ $parentDyn.field($fromNameE) }

    '{
      if ($fromNameE != $toNameE) {
        $b.add(Rename(at = $atFrom, to = $toNameE))
        val newAt = $parentDyn.field($toNameE)
        $b.add(Mandate(at = newAt, default = $defaultExpr))
      } else {
        $b.add(Mandate(at = $atFrom, default = $defaultExpr))
      }
    }
  }

  private def changeFieldTypeImpl[A: Type, B: Type](
      fromSel: Expr[A => Any],
      toSel: Expr[B => Any],
      converter: Expr[SchemaExpr[Any, Any]],
      b: Expr[MigrationBuilder[A]],
      sa: Expr[Schema[A]],
      sb: Expr[Schema[B]]
  )(using Quotes): Expr[Unit] = {
    val fromDyn = extractDynamicOptic[A, Any](fromSel, sa)
    val toDyn   = extractDynamicOptic[B, Any](toSel, sb)

    val fromNameE = lastFieldName(fromDyn, "changeFieldType(fromSel)")
    val toNameE   = lastFieldName(toDyn, "changeFieldType(toSel)")
    val parentDyn = parentOfField(fromDyn, "changeFieldType(fromSel)")

    val atFrom: Expr[DynamicOptic] = '{ $parentDyn.field($fromNameE) }

    '{
      if ($fromNameE != $toNameE) {
        $b.add(Rename(at = $atFrom, to = $toNameE))
        val newAt = $parentDyn.field($toNameE)
        $b.add(ChangeType(at = newAt, converter = $converter))
      } else {
        $b.add(ChangeType(at = $atFrom, converter = $converter))
      }
    }
  }

  private def joinFieldsImpl[A: Type, B: Type](
      targetSel: Expr[B => Any],
      s1: Expr[A => Any],
      s2: Expr[A => Any],
      rest: Expr[Seq[A => Any]],
      combiner: Expr[SchemaExpr[Any, Any]],
      b: Expr[MigrationBuilder[A]],
      sa: Expr[Schema[A]],
      sb: Expr[Schema[B]]
  )(using Quotes): Expr[Unit] = {
    val targetDyn = extractDynamicOptic[B, Any](targetSel, sb)

    val tNameE   = lastFieldName(targetDyn, "joinFields(targetSel)")
    val tParentE = parentOfField(targetDyn, "joinFields(targetSel)")
    val atTarget: Expr[DynamicOptic] = '{ $tParentE.field($tNameE) }

    val sd1 = extractDynamicOptic[A, Any](s1, sa)
    val sd2 = extractDynamicOptic[A, Any](s2, sa)

    '{
      val restOptics = $rest.map(sel => optic[A, Any](sel).toDynamic).toVector
      val sources = Vector($sd1, $sd2) ++ restOptics
      $b.add(Join(at = $atTarget, sourcePaths = sources, combiner = $combiner))
    }
  }

  private def splitFieldImpl[A: Type, B: Type](
      sourceSel: Expr[A => Any],
      t1: Expr[B => Any],
      t2: Expr[B => Any],
      rest: Expr[Seq[B => Any]],
      splitter: Expr[SchemaExpr[Any, Any]],
      b: Expr[MigrationBuilder[A]],
      sa: Expr[Schema[A]],
      sb: Expr[Schema[B]]
  )(using Quotes): Expr[Unit] = {
    val sourceDyn = extractDynamicOptic[A, Any](sourceSel, sa)
    val sNameE    = lastFieldName(sourceDyn, "splitField(sourceSel)")
    val sParentE  = parentOfField(sourceDyn, "splitField(sourceSel)")
    val atSource: Expr[DynamicOptic] = '{ $sParentE.field($sNameE) }

    val td1 = extractDynamicOptic[B, Any](t1, sb)
    val td2 = extractDynamicOptic[B, Any](t2, sb)

    '{
      val restOptics = $rest.map(sel => optic[B, Any](sel).toDynamic).toVector
      val targets = Vector($td1, $td2) ++ restOptics
      $b.add(Split(at = $atSource, targetPaths = targets, splitter = $splitter))
    }
  }

  private def renameCaseImpl[A: Type](
      enumSel: Expr[A => Any],
      from: Expr[String],
      to: Expr[String],
      b: Expr[MigrationBuilder[A]],
      sa: Expr[Schema[A]]
  )(using Quotes): Expr[Unit] = {
    val enumDyn = extractDynamicOptic[A, Any](enumSel, sa)
    '{ $b.add(RenameCase(at = $enumDyn, from = $from, to = $to)) }
  }

  private def transformCaseImpl[A: Type](
      caseSel: Expr[A => Any],
      f: Expr[MigrationBuilder[A] ?=> Unit],
      b: Expr[MigrationBuilder[A]],
      sa: Expr[Schema[A]]
  )(using Quotes): Expr[Unit] = {
    val caseDyn = extractDynamicOptic[A, Any](caseSel, sa)

    '{
      val nestedBuilder = MigrationBuilder.empty[A]
      $f(using nestedBuilder)
      val nestedProgram = nestedBuilder.ops.reverse.toVector
      $b.add(TransformCase(at = $caseDyn, actions = nestedProgram))
    }
  }

  // ─────────────────────────────────────────────
  // DynamicOptic extraction from selectors
  // ─────────────────────────────────────────────

  private def extractDynamicOptic[A: Type, B: Type](
      sel: Expr[A => B],
      sa: Expr[Schema[A]]
  )(using Quotes): Expr[DynamicOptic] =
    '{
      val o: ZOptic[A, B] =
        optic[A, B]($sel)(using $sa).asInstanceOf[ZOptic[A, B]]
      o.toDynamic
    }

  private def lastFieldName(dyn: Expr[DynamicOptic], ctx: String)(using Quotes): Expr[String] =
    '{ MigrationDsl.RuntimeOptic.lastFieldNameOrFail($dyn, ${ Expr(ctx) }) }

  private def parentOfField(dyn: Expr[DynamicOptic], ctx: String)(using Quotes): Expr[DynamicOptic] =
    '{ MigrationDsl.RuntimeOptic.dropLastNodeOrFail($dyn, ${ Expr(ctx) }) }

  inline def structuralSchemaOf[A](using s: Schema[A], ts: ToStructural[A]): Schema[ts.StructuralType] =
    s.structural

  // ─────────────────────────────────────────────
  // Runtime helpers (used by interpreter & reverse)
  // ─────────────────────────────────────────────

  private[migration] object RuntimeOptic {

    def lastFieldNameOrFail(d: DynamicOptic, ctx: String): String =
      d.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(name)) => name
        case Some(other) =>
          throw new IllegalArgumentException(
            s"$ctx: selector must end with a field access, but ended with: $other"
          )
        case None =>
          throw new IllegalArgumentException(s"$ctx: empty optic is not allowed")
      }

    def dropLastNodeOrFail(d: DynamicOptic, ctx: String): DynamicOptic = {
      if (d.nodes.isEmpty)
        throw new IllegalArgumentException(s"$ctx: empty optic is not allowed")

      val init = d.nodes.dropRight(1)
      rebuildFromNodes(init)
    }

    def rebuildFromNodes(nodes: Seq[DynamicOptic.Node]): DynamicOptic =
      nodes.foldLeft(DynamicOptic.root) { (acc, n) =>
        n match {
          case DynamicOptic.Node.Field(name)        => acc.field(name)
          case DynamicOptic.Node.Elements           => acc.elements
          case DynamicOptic.Node.MapKeys            => acc.mapKeys
          case DynamicOptic.Node.MapValues          => acc.mapValues
          case DynamicOptic.Node.Case(name)         => acc.caseOf(name)
          case DynamicOptic.Node.Wrapped            => acc.wrapped
          case DynamicOptic.Node.AtIndex(i)         => acc.atIndex(i)
          case DynamicOptic.Node.AtIndices(ixs)     => acc.atIndices(ixs)
          case DynamicOptic.Node.AtMapKey(k)        => acc.atMapKey(k)
          case DynamicOptic.Node.AtMapKeys(ks)      => acc.atMapKeys(ks)
        }
      }
  }
}
