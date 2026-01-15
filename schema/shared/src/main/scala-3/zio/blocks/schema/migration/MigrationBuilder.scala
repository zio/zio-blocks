package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, SchemaExpr, ToStructural}
import zio.blocks.schema.DynamicOptic

import scala.quoted.*
import zio.blocks.schema.migration.MigrationAction.*

/** Macro-backed, typed migration builder (issue #519).
  *
  * Notes:
  *   - User supplies *selectors* (A => Any, B => Any), never optics.
  *   - Selectors are macro-compiled into DynamicOptic paths.
  *   - The resulting migration is pure data: Vector[MigrationAction].
  */
final class MigrationBuilder[A, B](
    val sourceSchema: Schema[A],
    val targetSchema: Schema[B],
    val actions: Vector[MigrationAction]
) { self =>

  // ----------------------------
  // Record operations
  // ----------------------------

  inline def addField(
      inline target: B => Any,
      inline default: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.addFieldImpl[A, B]('self, 'target, 'default) }

  inline def dropField(
      inline source: A => Any,
      inline defaultForReverse: SchemaExpr[B, _] = MigrationSchemaExpr.default
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.dropFieldImpl[A, B](
        'self,
        'source,
        'defaultForReverse
      )
    }

  inline def renameField(
      inline from: A => Any,
      inline to: B => Any
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.renameFieldImpl[A, B]('self, 'from, 'to) }

  inline def transformField(
      inline from: A => Any,
      inline to: B => Any,
      inline transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.transformFieldImpl[A, B](
        'self,
        'from,
        'to,
        'transform
      )
    }

  inline def mandateField(
      inline source: A => Option[?],
      inline target: B => Any,
      inline default: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.mandateFieldImpl[A, B](
        'self,
        'source,
        'target,
        'default
      )
    }

  inline def optionalizeField(
      inline source: A => Any,
      inline target: B => Option[?]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.optionalizeFieldImpl[A, B]('self, 'source, 'target)
    }

  inline def changeFieldType(
      inline source: A => Any,
      inline target: B => Any,
      inline converter: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.changeFieldTypeImpl[A, B](
        'self,
        'source,
        'target,
        'converter
      )
    }

  // ----------------------------
  // Enum operations (limited)
  // ----------------------------

  def renameCase[SumA, SumB](from: String, to: String): MigrationBuilder[A, B] =
    copyAppended(RenameCase(at = DynamicOptic.root, from = from, to = to))

  inline def renameCaseAt[SumA, SumB](
      inline at: A => Any,
      inline from: String,
      inline to: String
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.renameCaseAtImpl[A, B](
        'self,
        'at,
        'from,
        'to
      )
    }

  inline def transformCaseAt[CaseA, CaseB](
      inline at: A => CaseA
  )(
      inline caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[
        CaseA,
        CaseB
      ]
  )(using sa: Schema[CaseA], sb: Schema[CaseB]): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.transformCaseAtImpl[A, B, CaseA, CaseB](
        'self,
        'at,
        'caseMigration,
        'sa,
        'sb
      )
    }

  inline def transformCase[SumA, CaseA, SumB, CaseB](
      inline caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[
        CaseA,
        CaseB
      ]
  )(using
      sa: Schema[CaseA],
      sb: Schema[CaseB]
  ): MigrationBuilder[A, B] = {
    // case migrations are nested, so we just collect their actions and embed them.
    val nested = caseMigration(
      new MigrationBuilder[CaseA, CaseB](sa, sb, Vector.empty)
    )
    copyAppended(
      TransformCase(at = DynamicOptic.root, actions = nested.actions)
    )
  }

  // ----------------------------
  // Collections / Maps
  // ----------------------------

  inline def transformElements(
      inline at: A => Vector[?],
      inline transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.transformElementsImpl[A, B]('self, 'at, 'transform)
    }

  inline def transformKeys(
      inline at: A => Map[?, ?],
      inline transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformKeysImpl[A, B]('self, 'at, 'transform) }

  inline def transformValues(
      inline at: A => Map[?, ?],
      inline transform: SchemaExpr[A, _]
  ): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacros.transformValuesImpl[A, B]('self, 'at, 'transform)
    }

  // ----------------------------
  // Build
  // ----------------------------

  /** Build migration with validation (shape + constraints). */
  def build(using ToStructural[A], ToStructural[B]): Migration[A, B] = {
    val prog = DynamicMigration(actions)
    MigrationValidator.validateOrThrow(prog, sourceSchema, targetSchema)
    Migration.fromProgram[A, B](prog)(using
      sourceSchema,
      targetSchema,
      summon[ToStructural[A]],
      summon[ToStructural[B]]
    )
  }

  /** Build migration without validation. */
  def buildPartial(using ToStructural[A], ToStructural[B]): Migration[A, B] = {
    val prog = DynamicMigration(actions)
    Migration.fromProgram[A, B](prog)(using
      sourceSchema,
      targetSchema,
      summon[ToStructural[A]],
      summon[ToStructural[B]]
    )
  }

  // ----------------------------
  // Internals
  // ----------------------------

  private[migration] def copyAppended(
      action: MigrationAction
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
}

private object MigrationBuilderMacros {

  // ----------------------------
  // Shared helpers
  // ----------------------------

  private def extractDynamicOptic[S: Type, T: Type](
      selector: Expr[S => T],
      schema: Expr[Schema[S]]
  )(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    // We support selectors like:
    //   _.a.b.c
    //   _.addresses.each.streetNumber
    //   _.country.when[UK].postcode
    //
    // Internally we translate these into DynamicOptic nodes:
    //   Field("a"), Field("b"), Field("c")
    //   Field("addresses"), Elements, Field("streetNumber")
    //   Field("country"), Case("UK"), Field("postcode")
    //
    // Notes:
    // - `.each` is treated as a collection traversal (DynamicOptic.Node.Elements)
    // - `.when[Case]` is treated as an enum/variant focus (DynamicOptic.Node.Case("Case"))
    def fail(term: Term): Nothing =
      report.errorAndAbort(
        s"migration selector must be a simple path like `_.a.b.c`, `_.xs.each.y`, or `_.e.when[Case].x`, got: ${term.show}"
      )

    sealed trait Step
    object Step {
      final case class Field(name: String) extends Step
      case object Each extends Step
      final case class Case(name: String) extends Step
    }

    def collectSteps(term0: Term, paramSym: Symbol): List[Step] = {
      def loop(term: Term, acc: List[Step]): List[Step] =
        term match {
          case Inlined(_, _, t) =>
            loop(t, acc)

          // Support `.when[Case]` (type application, no term args)
          case TypeApply(Select(qual, "when"), List(caseTpt)) =>
            val caseName = caseTpt.tpe.typeSymbol.name
            loop(qual, Step.Case(caseName) :: acc)

          // Support `.each`
          case Select(qual, "each") =>
            loop(qual, Step.Each :: acc)

          // Plain field selection: _.a or _.a.b.c
          case Select(qual, name) =>
            loop(qual, Step.Field(name) :: acc)

          // stop when we reach the lambda param
          case id: Ident if id.symbol == paramSym =>
            acc

          // allow typed wrappers
          case Typed(t, _) =>
            loop(t, acc)

          case other =>
            fail(other)
        }

      loop(term0, Nil)
    }

    selector.asTerm match {
      case Inlined(_, _, Lambda(List(param), body)) =>
        val steps = collectSteps(body, param.symbol)

        // Build DynamicOptic(Vector(Node...)) directly so we don't depend on
        // any particular fluent API surface on DynamicOptic.
        val nodeExprs: List[Expr[DynamicOptic.Node]] =
          steps.map {
            case Step.Field(n) =>
              '{ DynamicOptic.Node.Field(${ Expr(n) }) }
            case Step.Each =>
              '{ DynamicOptic.Node.Elements }
            case Step.Case(n) =>
              '{ DynamicOptic.Node.Case(${ Expr(n) }) }
          }

        '{
          DynamicOptic(${ Expr.ofList(nodeExprs) }.toVector)
        }

      case other =>
        fail(other)
    }
  }

  def transformCaseAtImpl[A: Type, B: Type, CaseA: Type, CaseB: Type](
      builder: Expr[MigrationBuilder[A, B]],
      atSel: Expr[A => CaseA],
      caseMigration: Expr[
        MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
      ],
      sa: Expr[Schema[CaseA]],
      sb: Expr[Schema[CaseB]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceSchemaExpr = '{ $builder.sourceSchema }
    val atDyn = extractDynamicOptic[A, CaseA](atSel, sourceSchemaExpr)

    '{
      val nested =
        $caseMigration(
          new MigrationBuilder[CaseA, CaseB](
            $sa,
            $sb,
            Vector.empty
          )
        )

      $builder.copyAppended(
        TransformCase(at = $atDyn, actions = nested.actions)
      )
    }
  }

  private def lastFieldName(at: Expr[DynamicOptic], ctx: Expr[String])(using
      Quotes
  ): Expr[String] = {
    '{
      $at.nodes.lastOption match {
        case Some(DynamicOptic.Node.Field(name)) => name
        case other =>
          throw new IllegalArgumentException(
            $ctx + ": selector must end in a field, got: " + other
          )
      }
    }
  }

  private def parentOfField(at: Expr[DynamicOptic], ctx: Expr[String])(using
      Quotes
  ): Expr[DynamicOptic] =
    '{
      val ns = $at.nodes
      ns.lastOption match {
        case Some(_: DynamicOptic.Node.Field) =>
          DynamicOptic(ns.dropRight(1).toVector)
        case _ =>
          throw new IllegalArgumentException(
            $ctx + ": selector must end in a field"
          )
      }
    }

  // ----------------------------
// Optic extraction helpers (for enum/case APIs)
// ----------------------------

  def sourceOpticImpl[A: Type, B: Type, T: Type](
      builder: Expr[MigrationBuilder[A, B]],
      sel: Expr[A => T]
  )(using Quotes): Expr[DynamicOptic] = {
    val sa = '{ $builder.sourceSchema }
    extractDynamicOptic[A, T](sel, sa)
  }

  def renameCaseAtImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      atSel: Expr[A => Any],
      from: Expr[String],
      to: Expr[String]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sa = '{ $builder.sourceSchema }
    val atDyn = extractDynamicOptic[A, Any](atSel, sa)
    '{
      $builder.copyAppended(
        RenameCase(at = $atDyn, from = $from, to = $to)
      )
    }
  }

  private def ensureFieldSelectorEnd(at: Expr[DynamicOptic], ctx: String)(using
      Quotes
  ): Unit = {
    ()
  }

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

    val sb = '{ $builder.targetSchema }
    val targetDyn = extractDynamicOptic[B, Any](targetSel, sb)
    val nameE = lastFieldName(targetDyn, Expr("addField(target)"))
    val parentE = parentOfField(targetDyn, Expr("addField(target)"))
    val atField = '{ $parentE.field($nameE) }

    // Capture the *field schema* (target field type) and turn DefaultValue marker into DefaultValueFromSchema(fieldSchema)
    val fieldTpe: TypeRepr =
      targetSel.asTerm match {
        case Inlined(_, _, Lambda(_, body)) => body.tpe.widen
        case other =>
          report.errorAndAbort(
            s"addField(target): expected a lambda selector, got: ${other.show}"
          )
      }

    fieldTpe.asType match {
      case '[t] =>
        val fieldSchema: Expr[Schema[t]] =
          Expr.summon[Schema[t]].getOrElse {
            report.errorAndAbort(
              s"addField(target): could not summon Schema[${Type.show[t]}] to capture default"
            )
          }

        val capturedDefault: Expr[SchemaExpr[A, _]] =
          '{
            MigrationSchemaExpr.captureDefaultIfMarker[A, t](
              $defaultExpr.asInstanceOf[SchemaExpr[A, t]],
              $fieldSchema
            )
          }

        append(
          builder,
          '{
            AddField(
              at = $atField,
              default = $capturedDefault.asInstanceOf[SchemaExpr[Any, Any]]
            )
          }
        )

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

    val sa = '{ $builder.sourceSchema }
    val sourceDyn = extractDynamicOptic[A, Any](sourceSel, sa)
    val nameE = lastFieldName(sourceDyn, Expr("dropField(source)"))
    val parentE = parentOfField(sourceDyn, Expr("dropField(source)"))
    val atField = '{ $parentE.field($nameE) }

    // Capture schema from the *source field type* for reverse default when needed.
    val fieldTpe: TypeRepr =
      sourceSel.asTerm match {
        case Inlined(_, _, Lambda(_, body)) => body.tpe.widen
        case other =>
          report.errorAndAbort(
            s"dropField(source): expected a lambda selector, got: ${other.show}"
          )
      }

    fieldTpe.asType match {
      case '[t] =>
        val fieldSchema: Expr[Schema[t]] =
          Expr.summon[Schema[t]].getOrElse {
            report.errorAndAbort(
              s"dropField(source): could not summon Schema[${Type.show[t]}] to capture reverse default"
            )
          }

        val capturedReverse: Expr[SchemaExpr[B, _]] =
          '{
            MigrationSchemaExpr.captureDefaultIfMarker[B, t](
              $defaultForReverseExpr.asInstanceOf[SchemaExpr[B, t]],
              $fieldSchema
            )
          }

        append(
          builder,
          '{
            DropField(
              at = $atField,
              defaultForReverse =
                $capturedReverse.asInstanceOf[SchemaExpr[Any, Any]]
            )
          }
        )

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
    val sa = '{ $builder.sourceSchema }
    val sb = '{ $builder.targetSchema }
    val fromDyn = extractDynamicOptic[A, Any](fromSel, sa)
    val toDyn = extractDynamicOptic[B, Any](toSel, sb)

    val fromNameE = lastFieldName(fromDyn, Expr("renameField(from)"))
    val toNameE = lastFieldName(toDyn, Expr("renameField(to)"))
    val parentDyn = parentOfField(fromDyn, Expr("renameField(from)"))

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
    val sa = '{ $builder.sourceSchema }
    val sb = '{ $builder.targetSchema }
    val fromDyn = extractDynamicOptic[A, Any](fromSel, sa)
    val toDyn = extractDynamicOptic[B, Any](toSel, sb)

    val fromNameE = lastFieldName(fromDyn, Expr("transformField(from)"))
    val toNameE = lastFieldName(toDyn, Expr("transformField(to)"))
    val parentDyn = parentOfField(fromDyn, Expr("transformField(from)"))
    val atField = '{ $parentDyn.field($fromNameE) }

    // `transformField` in the suggested design can also rename; we always emit a Rename (no-op if same name),
    // then apply the value transform at the (original) field location.
    '{
      // After a rename we must continue at the *new* field path.
      val renamedAt = $parentDyn.field($toNameE)
      $builder
        .copyAppended(Rename(at = $atField, to = $toNameE))
        .copyAppended(
          TransformValue(
            at = renamedAt,
            transform = $transform.asInstanceOf[SchemaExpr[Any, Any]]
          )
        )
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

    val sa = '{ $builder.sourceSchema }
    val sb = '{ $builder.targetSchema }
    val sourceDyn =
      extractDynamicOptic[A, Any](sourceSel.asInstanceOf[Expr[A => Any]], sa)
    val targetDyn = extractDynamicOptic[B, Any](targetSel, sb)

    val sourceNameE = lastFieldName(sourceDyn, Expr("mandateField(source)"))
    val targetNameE = lastFieldName(targetDyn, Expr("mandateField(target)"))
    val sourceParen = parentOfField(sourceDyn, Expr("mandateField(source)"))
    val atField = '{ $sourceParen.field($sourceNameE) }

    // Capture target field schema for DefaultValue marker
    val fieldTpe: TypeRepr =
      targetSel.asTerm match {
        case Inlined(_, _, Lambda(_, body)) => body.tpe.widen
        case other =>
          report.errorAndAbort(
            s"mandateField(target): expected a lambda selector, got: ${other.show}"
          )
      }

    fieldTpe.asType match {
      case '[t] =>
        val fieldSchema: Expr[Schema[t]] =
          Expr.summon[Schema[t]].getOrElse {
            report.errorAndAbort(
              s"mandateField: could not summon Schema[${Type.show[t]}] to capture default"
            )
          }

        val capturedDefault: Expr[SchemaExpr[A, _]] =
          '{
            MigrationSchemaExpr.captureDefaultIfMarker[A, t](
              $defaultExpr.asInstanceOf[SchemaExpr[A, t]],
              $fieldSchema
            )
          }

        // Rename (if needed) happens first, then Mandate at the (renamed) location.
        '{
          // After a rename we must continue at the *new* field path.
          val renamedAt = $sourceParen.field($targetNameE)
          $builder
            .copyAppended(Rename(at = $atField, to = $targetNameE))
            .copyAppended(
              Mandate(
                at = renamedAt,
                default = $capturedDefault.asInstanceOf[SchemaExpr[Any, Any]]
              )
            )
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
    val sa = '{ $builder.sourceSchema }
    val sb = '{ $builder.targetSchema }
    val sourceDyn = extractDynamicOptic[A, Any](sourceSel, sa)
    val targetDyn =
      extractDynamicOptic[B, Any](targetSel.asInstanceOf[Expr[B => Any]], sb)

    val sourceNameE = lastFieldName(sourceDyn, Expr("optionalizeField(source)"))
    val targetNameE = lastFieldName(targetDyn, Expr("optionalizeField(target)"))
    val parentDyn = parentOfField(sourceDyn, Expr("optionalizeField(source)"))
    val atField = '{ $parentDyn.field($sourceNameE) }

    '{
      // After a rename we must continue at the *new* field path.
      val renamedAt = $parentDyn.field($targetNameE)
      $builder
        .copyAppended(Rename(at = $atField, to = $targetNameE))
        .copyAppended(Optionalize(at = renamedAt))
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
    val sa = '{ $builder.sourceSchema }
    val sb = '{ $builder.targetSchema }
    val sourceDyn = extractDynamicOptic[A, Any](sourceSel, sa)
    val targetDyn = extractDynamicOptic[B, Any](targetSel, sb)

    val sourceNameE = lastFieldName(sourceDyn, Expr("changeFieldType(source)"))
    val targetNameE = lastFieldName(targetDyn, Expr("changeFieldType(target)"))
    val parentDyn = parentOfField(sourceDyn, Expr("changeFieldType(source)"))
    val atField = '{ $parentDyn.field($sourceNameE) }

    '{
      // After a rename we must continue at the *new* field path.
      val renamedAt = $parentDyn.field($targetNameE)
      $builder
        .copyAppended(Rename(at = $atField, to = $targetNameE))
        .copyAppended(
          ChangeType(
            at = renamedAt,
            converter = $converter.asInstanceOf[SchemaExpr[Any, Any]]
          )
        )
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
    val sa = '{ $builder.sourceSchema }
    val dyn =
      extractDynamicOptic[A, Any](atSel.asInstanceOf[Expr[A => Any]], sa)
    append(
      builder,
      '{
        TransformElements(
          at = $dyn,
          transform = $transform.asInstanceOf[SchemaExpr[Any, Any]]
        )
      }
    )
  }

  def transformKeysImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      atSel: Expr[A => Map[?, ?]],
      transform: Expr[SchemaExpr[A, _]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sa = '{ $builder.sourceSchema }
    val dyn =
      extractDynamicOptic[A, Any](atSel.asInstanceOf[Expr[A => Any]], sa)
    append(
      builder,
      '{
        TransformKeys(
          at = $dyn,
          transform = $transform.asInstanceOf[SchemaExpr[Any, Any]]
        )
      }
    )
  }

  def transformValuesImpl[A: Type, B: Type](
      builder: Expr[MigrationBuilder[A, B]],
      atSel: Expr[A => Map[?, ?]],
      transform: Expr[SchemaExpr[A, _]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sa = '{ $builder.sourceSchema }
    val dyn =
      extractDynamicOptic[A, Any](atSel.asInstanceOf[Expr[A => Any]], sa)
    append(
      builder,
      '{
        TransformValues(
          at = $dyn,
          transform = $transform.asInstanceOf[SchemaExpr[Any, Any]]
        )
      }
    )
  }
}
