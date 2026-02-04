package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, SchemaExpr}

import scala.reflect.macros.blackbox

private[migration] object MigrationBuilderMacros {

  // ----------------------------
  // Selector -> DynamicOptic
  // ----------------------------

  private sealed trait Step
  private object Step {
    final case class Field(name: String) extends Step
    case object Each                     extends Step
    final case class Case(name: String)  extends Step
  }

  private def abort(c: blackbox.Context, msg: String): Nothing =
    c.abort(c.enclosingPosition, msg)

  private def collectSteps(
    c: blackbox.Context
  )(body: c.Tree, paramSym: c.Symbol): List[Step] = {
    import c.universe._

    def fail(t: Tree): Nothing =
      abort(
        c,
        "migration selector must be a simple path like `_.a.b.c`, `_.xs.each.y`, or `_.e.when[Case].x`, got: " +
          showCode(t)
      )

    def loop(t: Tree, acc: List[Step]): List[Step] =
      t match {
        case TypeApply(Select(qual, TermName("when")), List(caseTpt)) =>
          val caseName = caseTpt.tpe.typeSymbol.name.decodedName.toString
          loop(qual, Step.Case(caseName) :: acc)

        case Select(qual, TermName("each")) =>
          loop(qual, Step.Each :: acc)

        case Select(qual, name) =>
          loop(qual, Step.Field(name.decodedName.toString) :: acc)

        case id: Ident if id.symbol == paramSym =>
          acc

        case Typed(t0, _) =>
          loop(t0, acc)

        case other =>
          fail(other)
      }

    loop(body, Nil).reverse
  }

  private def selectorToSteps(
    c: blackbox.Context
  )(selector: c.Expr[Any => Any]): (List[Step], c.Type) = {
    import c.universe._

    selector.tree match {
      case Function(List(param), body) =>
        val steps = collectSteps(c)(body, param.symbol)
        (steps, body.tpe.widen)
      case other =>
        abort(c, "expected a lambda selector, got: " + showCode(other))
    }
  }

  private def stepsToDynamicOpticTree(
    c: blackbox.Context
  )(steps: List[Step]): c.Tree = {
    import c.universe._

    val nodeTrees: List[Tree] = steps.map {
      case Step.Field(n) =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($n)"
      case Step.Each =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.Elements"
      case Step.Case(n) =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.Case($n)"
    }

    q"_root_.zio.blocks.schema.DynamicOptic(_root_.scala.Vector(..$nodeTrees))"
  }

  private def requireEndsInField(
    c: blackbox.Context
  )(steps: List[Step], ctx: String): String =
    steps.lastOption match {
      case Some(Step.Field(n)) => n
      case other               =>
        abort(c, s"$ctx: selector must end in a field, got: $other")
    }

  private def parentStepsOfField(
    c: blackbox.Context
  )(steps: List[Step], ctx: String): List[Step] = {
    requireEndsInField(c)(steps, ctx)
    steps.dropRight(1)
  }

  // ----------------------------
  // addField
  // ----------------------------

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[A, _]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self              = c.prefix
    val (steps, fieldTpe) =
      selectorToSteps(c)(target.asInstanceOf[c.Expr[Any => Any]])
    requireEndsInField(c)(steps, "addField(target)")

    val atField = stepsToDynamicOpticTree(c)(steps)

    val fieldSchema =
      q"""{
        val __bound =
          $self.targetSchema
            .get($atField)
            .getOrElse(
              throw new IllegalArgumentException(
                "addField(target): field not found in target schema: " + $atField
              )
            )

        _root_.zio.blocks.schema.Schema[$fieldTpe](
          __bound.asInstanceOf[_root_.zio.blocks.schema.Reflect.Bound[$fieldTpe]]
        )
      }"""

    val capturedDefault =
      q"""_root_.zio.blocks.schema.migration.MigrationSchemaExpr
            .captureDefaultIfMarker[${weakTypeOf[A]}, $fieldTpe](
              ${default.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[${weakTypeOf[A]}, $fieldTpe]],
              $fieldSchema.asInstanceOf[_root_.zio.blocks.schema.Schema[$fieldTpe]]
            )"""

    val action =
      q"""_root_.zio.blocks.schema.migration.MigrationAction.AddField(
            at = $atField,
            default = $capturedDefault.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]]
          )"""

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }

  // ----------------------------
  // dropField
  // ----------------------------

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[SchemaExpr[B, _]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self              = c.prefix
    val (steps, fieldTpe) =
      selectorToSteps(c)(source.asInstanceOf[c.Expr[Any => Any]])
    requireEndsInField(c)(steps, "dropField(source)")

    val atField = stepsToDynamicOpticTree(c)(steps)

    val fieldSchema =
      q"""{
        val __bound =
          $self.sourceSchema
            .get($atField)
            .getOrElse(
              throw new IllegalArgumentException(
                "dropField(source): field not found in source schema: " + $atField
              )
            )

        _root_.zio.blocks.schema.Schema[$fieldTpe](
          __bound.asInstanceOf[_root_.zio.blocks.schema.Reflect.Bound[$fieldTpe]]
        )
      }"""

    val capturedReverse =
      q"""_root_.zio.blocks.schema.migration.MigrationSchemaExpr
            .captureDefaultIfMarker[${weakTypeOf[B]}, $fieldTpe](
              ${defaultForReverse.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[${weakTypeOf[B]}, $fieldTpe]],
              $fieldSchema.asInstanceOf[_root_.zio.blocks.schema.Schema[$fieldTpe]]
            )"""

    val action =
      q"""_root_.zio.blocks.schema.migration.MigrationAction.DropField(
            at = $atField,
            defaultForReverse = $capturedReverse.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]]
          )"""

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }

  // ----------------------------
  // renameField
  // ----------------------------

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self           = c.prefix
    val (fromSteps, _) =
      selectorToSteps(c)(from.asInstanceOf[c.Expr[Any => Any]])
    val (toSteps, _) =
      selectorToSteps(c)(to.asInstanceOf[c.Expr[Any => Any]])

    val fromName = requireEndsInField(c)(fromSteps, "renameField(from)")
    val toName   = requireEndsInField(c)(toSteps, "renameField(to)")

    val parent  = parentStepsOfField(c)(fromSteps, "renameField(from)")
    val atField = stepsToDynamicOpticTree(c)(parent :+ Step.Field(fromName))

    val action =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Rename(at = $atField, to = $toName)"

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }

  // ----------------------------
  // transformField (Rename + TransformValue)
  // ----------------------------

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any],
    transform: c.Expr[SchemaExpr[A, _]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self           = c.prefix
    val (fromSteps, _) =
      selectorToSteps(c)(from.asInstanceOf[c.Expr[Any => Any]])
    val (toSteps, _) =
      selectorToSteps(c)(to.asInstanceOf[c.Expr[Any => Any]])

    val fromName = requireEndsInField(c)(fromSteps, "transformField(from)")
    val toName   = requireEndsInField(c)(toSteps, "transformField(to)")
    val parent   = parentStepsOfField(c)(fromSteps, "transformField(from)")

    val atOld = stepsToDynamicOpticTree(c)(parent :+ Step.Field(fromName))
    val atNew = stepsToDynamicOpticTree(c)(parent :+ Step.Field(toName))

    val renameAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Rename(at = $atOld, to = $toName)"

    val transformAction =
      q"""_root_.zio.blocks.schema.migration.MigrationAction.TransformValue(
            at = $atNew,
            transform = ${transform.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]]
          )"""

    c.Expr[MigrationBuilder[A, B]](
      q"$self.copyAppended($renameAction).copyAppended($transformAction)"
    )
  }

  // ----------------------------
  // mandateField (Rename + Mandate)
  // ----------------------------

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    source: c.Expr[A => Option[_]],
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[A, _]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self = c.prefix

    val (sourceSteps, _) =
      selectorToSteps(c)(source.asInstanceOf[c.Expr[Any => Any]])
    val (targetSteps, tTpe) =
      selectorToSteps(c)(target.asInstanceOf[c.Expr[Any => Any]])

    val sourceName = requireEndsInField(c)(sourceSteps, "mandateField(source)")
    val targetName = requireEndsInField(c)(targetSteps, "mandateField(target)")
    val parent     = parentStepsOfField(c)(sourceSteps, "mandateField(source)")

    val atOld = stepsToDynamicOpticTree(c)(parent :+ Step.Field(sourceName))
    val atNew = stepsToDynamicOpticTree(c)(parent :+ Step.Field(targetName))

    val fieldSchema =
      q"""{
        val __bound =
          $self.targetSchema
            .get($atNew)
            .getOrElse(
              throw new IllegalArgumentException(
                "mandateField(target): field not found in target schema: " + $atNew
              )
            )

        _root_.zio.blocks.schema.Schema[$tTpe](
          __bound.asInstanceOf[_root_.zio.blocks.schema.Reflect.Bound[$tTpe]]
        )
      }"""

    val capturedDefault =
      q"""_root_.zio.blocks.schema.migration.MigrationSchemaExpr
            .captureDefaultIfMarker[${weakTypeOf[A]}, $tTpe](
              ${default.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[${weakTypeOf[A]}, $tTpe]],
              $fieldSchema.asInstanceOf[_root_.zio.blocks.schema.Schema[$tTpe]]
            )"""

    val renameAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Rename(at = $atOld, to = $targetName)"

    val mandateAction =
      q"""_root_.zio.blocks.schema.migration.MigrationAction.Mandate(
            at = $atNew,
            default = $capturedDefault.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]]
          )"""

    c.Expr[MigrationBuilder[A, B]](
      q"$self.copyAppended($renameAction).copyAppended($mandateAction)"
    )
  }

  // ----------------------------
  // optionalizeField (Rename + Optionalize)
  // ----------------------------

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    source: c.Expr[A => Any],
    target: c.Expr[B => Option[_]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self = c.prefix

    val (sourceSteps, _) =
      selectorToSteps(c)(source.asInstanceOf[c.Expr[Any => Any]])
    val (targetSteps, _) =
      selectorToSteps(c)(target.asInstanceOf[c.Expr[Any => Any]])

    val sourceName = requireEndsInField(c)(sourceSteps, "optionalizeField(source)")
    val targetName = requireEndsInField(c)(targetSteps, "optionalizeField(target)")
    val parent     = parentStepsOfField(c)(sourceSteps, "optionalizeField(source)")

    val atOld = stepsToDynamicOpticTree(c)(parent :+ Step.Field(sourceName))
    val atNew = stepsToDynamicOpticTree(c)(parent :+ Step.Field(targetName))

    val renameAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Rename(at = $atOld, to = $targetName)"
    val optAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Optionalize(at = $atNew)"

    c.Expr[MigrationBuilder[A, B]](
      q"$self.copyAppended($renameAction).copyAppended($optAction)"
    )
  }

  // ----------------------------
  // changeFieldType (Rename + ChangeType)
  // ----------------------------

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    source: c.Expr[A => Any],
    target: c.Expr[B => Any],
    converter: c.Expr[SchemaExpr[A, _]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self = c.prefix

    val (sourceSteps, _) =
      selectorToSteps(c)(source.asInstanceOf[c.Expr[Any => Any]])
    val (targetSteps, _) =
      selectorToSteps(c)(target.asInstanceOf[c.Expr[Any => Any]])

    val sourceName = requireEndsInField(c)(sourceSteps, "changeFieldType(source)")
    val targetName = requireEndsInField(c)(targetSteps, "changeFieldType(target)")
    val parent     = parentStepsOfField(c)(sourceSteps, "changeFieldType(source)")

    val atOld = stepsToDynamicOpticTree(c)(parent :+ Step.Field(sourceName))
    val atNew = stepsToDynamicOpticTree(c)(parent :+ Step.Field(targetName))

    val renameAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Rename(at = $atOld, to = $targetName)"

    val changeAction =
      q"""_root_.zio.blocks.schema.migration.MigrationAction.ChangeType(
            at = $atNew,
            converter = ${converter.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]]
          )"""

    c.Expr[MigrationBuilder[A, B]](
      q"$self.copyAppended($renameAction).copyAppended($changeAction)"
    )
  }

  // ----------------------------
  // renameCaseAt
  // ----------------------------

  def renameCaseAtImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    at: c.Expr[A => Any],
    from: c.Expr[String],
    to: c.Expr[String]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self       = c.prefix
    val (steps, _) = selectorToSteps(c)(at.asInstanceOf[c.Expr[Any => Any]])
    val atDyn      = stepsToDynamicOpticTree(c)(steps)

    val action =
      q"_root_.zio.blocks.schema.migration.MigrationAction.RenameCase(at = $atDyn, from = ${from.tree}, to = ${to.tree})"

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }

  // ----------------------------
  // transformCaseAt
  // ----------------------------

  def transformCaseAtImpl[
    A: c.WeakTypeTag,
    B: c.WeakTypeTag,
    CaseA: c.WeakTypeTag,
    CaseB: c.WeakTypeTag
  ](
    c: blackbox.Context
  )(
    at: c.Expr[A => CaseA]
  )(
    caseMigration: c.Expr[MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self       = c.prefix
    val (steps, _) = selectorToSteps(c)(at.asInstanceOf[c.Expr[Any => Any]])
    val atDyn      = stepsToDynamicOpticTree(c)(steps)

    val caseATpe = weakTypeOf[CaseA]
    val caseBTpe = weakTypeOf[CaseB]

    // Summon Schema[CaseA] and Schema[CaseB] from implicits at call site
    val saTree = c.inferImplicitValue(appliedType(typeOf[Schema[_]].typeConstructor, caseATpe), silent = true)
    if (saTree == EmptyTree)
      c.abort(c.enclosingPosition, s"Could not find implicit Schema[$caseATpe] for transformCaseAt")

    val sbTree = c.inferImplicitValue(appliedType(typeOf[Schema[_]].typeConstructor, caseBTpe), silent = true)
    if (sbTree == EmptyTree)
      c.abort(c.enclosingPosition, s"Could not find implicit Schema[$caseBTpe] for transformCaseAt")

    val nested =
      q"${caseMigration.tree}(new _root_.zio.blocks.schema.migration.MigrationBuilder[$caseATpe, $caseBTpe]($saTree, $sbTree, _root_.scala.Vector.empty))"

    val action =
      q"_root_.zio.blocks.schema.migration.MigrationAction.TransformCase(at = $atDyn, actions = $nested.actions)"

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }

  // ----------------------------
  // transformElements / keys / values
  // ----------------------------

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    at: c.Expr[A => Vector[_]],
    transform: c.Expr[SchemaExpr[A, _]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self       = c.prefix
    val (steps, _) = selectorToSteps(c)(at.asInstanceOf[c.Expr[Any => Any]])
    val atDyn      = stepsToDynamicOpticTree(c)(steps)

    val action =
      q"""_root_.zio.blocks.schema.migration.MigrationAction.TransformElements(
            at = $atDyn,
            transform = ${transform.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]]
          )"""

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[A, _]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self       = c.prefix
    val (steps, _) = selectorToSteps(c)(at.asInstanceOf[c.Expr[Any => Any]])
    val atDyn      = stepsToDynamicOpticTree(c)(steps)

    val action =
      q"""_root_.zio.blocks.schema.migration.MigrationAction.TransformKeys(
            at = $atDyn,
            transform = ${transform.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]]
          )"""

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[A, _]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self       = c.prefix
    val (steps, _) = selectorToSteps(c)(at.asInstanceOf[c.Expr[Any => Any]])
    val atDyn      = stepsToDynamicOpticTree(c)(steps)

    val action =
      q"""_root_.zio.blocks.schema.migration.MigrationAction.TransformValues(
            at = $atDyn,
            transform = ${transform.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]]
          )"""

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }
}
