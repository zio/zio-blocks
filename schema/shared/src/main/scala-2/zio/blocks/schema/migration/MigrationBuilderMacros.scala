package zio.blocks.schema.migration

import zio.blocks.schema.{Schema, SchemaExpr}
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.MigrationAction._

import scala.reflect.macros.blackbox

private[migration] object MigrationBuilderMacros {

  // ----------------------------
  // Selector -> DynamicOptic
  // ----------------------------

  private sealed trait Step
  private object Step {
    final case class Field(name: String) extends Step
    case object Each extends Step
    final case class Case(name: String) extends Step
  }

  private def abort(c: blackbox.Context, msg: String): Nothing = {
    c.abort(c.enclosingPosition, msg)
  }

  private def collectSteps(
    c: blackbox.Context
  )(body: c.Tree, paramSym: c.Symbol): List[Step] = {
    import c.universe._

    def fail(t: Tree): Nothing =
      abort(
        c,
        "migration selector must be a simple path like `_.a.b.c`, `_.xs.each.y`, or `_.e.when[Case].x`, got: " + showCode(t)
      )

    def loop(t: Tree, acc: List[Step]): List[Step] =
      t match {
        // Support `.when[Case]` (type application, no term args)
        case TypeApply(Select(qual, TermName("when")), List(caseTpt)) =>
          val caseName = caseTpt.tpe.typeSymbol.name.decodedName.toString
          loop(qual, Step.Case(caseName) :: acc)

        // Support `.each`
        case Select(qual, TermName("each")) =>
          loop(qual, Step.Each :: acc)

        // Plain field selection: _.a or _.a.b.c
        case Select(qual, name) =>
          loop(qual, Step.Field(name.decodedName.toString) :: acc)

        // Stop when we reach the lambda param
        case id: Ident if id.symbol == paramSym =>
          acc

        // allow typed wrappers
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

  private def stepsToDynamicOpticTree(c: blackbox.Context)(steps: List[Step]): c.Tree = {
    import c.universe._

    val nodeTrees: List[Tree] = steps.map {
      case Step.Field(n) => q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($n)"
      case Step.Each     => q"_root_.zio.blocks.schema.DynamicOptic.Node.Elements"
      case Step.Case(n)  => q"_root_.zio.blocks.schema.DynamicOptic.Node.Case($n)"
    }

    q"_root_.zio.blocks.schema.DynamicOptic(_root_.scala.Vector(..$nodeTrees))"
  }

  private def requireEndsInField(c: blackbox.Context)(steps: List[Step], ctx: String): String =
    steps.lastOption match {
      case Some(Step.Field(n)) => n
      case other =>
        abort(c, s"$ctx: selector must end in a field, got: $other")
    }

  private def parentStepsOfField(c: blackbox.Context)(steps: List[Step], ctx: String): List[Step] = {
    requireEndsInField(c)(steps, ctx) // validates
    steps.dropRight(1)
  }

  private def summonSchemaFor(c: blackbox.Context)(tpe: c.Type): c.Tree = {
    import c.universe._
    val schemaTpe = appliedType(typeOf[Schema[_]].typeConstructor, tpe)
    val inst = c.inferImplicitValue(schemaTpe, silent = true)
    if (inst == EmptyTree)
      abort(c, s"could not summon Schema[$tpe] (needed to capture default-from-schema)")
    inst
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

    val self = c.prefix
    val (steps, fieldTpe) = selectorToSteps(c)(target.asInstanceOf[c.Expr[Any => Any]])
    val _ = requireEndsInField(c)(steps, "addField(target)")

    val atField = stepsToDynamicOpticTree(c)(steps)

    val fieldSchema = summonSchemaFor(c)(fieldTpe)

    val capturedDefault =
      q"_root_.zio.blocks.schema.migration.MigrationSchemaExpr.captureDefaultIfMarker[$weakTypeOf[A], $fieldTpe](" +
        q"${default.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[$weakTypeOf[A], $fieldTpe]], " +
        q"$fieldSchema.asInstanceOf[_root_.zio.blocks.schema.Schema[$fieldTpe]])"

    val action =
      q"_root_.zio.blocks.schema.migration.MigrationAction.AddField(" +
        q"at = $atField, " +
        q"default = $capturedDefault.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]])"

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

    val self = c.prefix
    val (steps, fieldTpe) = selectorToSteps(c)(source.asInstanceOf[c.Expr[Any => Any]])
    val _ = requireEndsInField(c)(steps, "dropField(source)")

    val atField = stepsToDynamicOpticTree(c)(steps)

    val fieldSchema = summonSchemaFor(c)(fieldTpe)

    val capturedReverse =
      q"_root_.zio.blocks.schema.migration.MigrationSchemaExpr.captureDefaultIfMarker[$weakTypeOf[B], $fieldTpe](" +
        q"${defaultForReverse.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[$weakTypeOf[B], $fieldTpe]], " +
        q"$fieldSchema.asInstanceOf[_root_.zio.blocks.schema.Schema[$fieldTpe]])"

    val action =
      q"_root_.zio.blocks.schema.migration.MigrationAction.DropField(" +
        q"at = $atField, " +
        q"defaultForReverse = $capturedReverse.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]])"

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

    val self = c.prefix
    val (fromSteps, _) = selectorToSteps(c)(from.asInstanceOf[c.Expr[Any => Any]])
    val (toSteps, _)   = selectorToSteps(c)(to.asInstanceOf[c.Expr[Any => Any]])

    val fromName = requireEndsInField(c)(fromSteps, "renameField(from)")
    val toName   = requireEndsInField(c)(toSteps, "renameField(to)")

    val parent = parentStepsOfField(c)(fromSteps, "renameField(from)")
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

    val self = c.prefix
    val (fromSteps, _) = selectorToSteps(c)(from.asInstanceOf[c.Expr[Any => Any]])
    val (toSteps, _)   = selectorToSteps(c)(to.asInstanceOf[c.Expr[Any => Any]])

    val fromName = requireEndsInField(c)(fromSteps, "transformField(from)")
    val toName   = requireEndsInField(c)(toSteps, "transformField(to)")
    val parent   = parentStepsOfField(c)(fromSteps, "transformField(from)")

    val atOld = stepsToDynamicOpticTree(c)(parent :+ Step.Field(fromName))
    val atNew = stepsToDynamicOpticTree(c)(parent :+ Step.Field(toName))

    val renameAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Rename(at = $atOld, to = $toName)"

    val transformAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.TransformValue(" +
        q"at = $atNew, transform = ${transform.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]])"

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($renameAction).copyAppended($transformAction)")
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

    val (sourceSteps, _)    = selectorToSteps(c)(source.asInstanceOf[c.Expr[Any => Any]])
    val (targetSteps, tTpe) = selectorToSteps(c)(target.asInstanceOf[c.Expr[Any => Any]])

    val sourceName = requireEndsInField(c)(sourceSteps, "mandateField(source)")
    val targetName = requireEndsInField(c)(targetSteps, "mandateField(target)")
    val parent     = parentStepsOfField(c)(sourceSteps, "mandateField(source)")

    val atOld = stepsToDynamicOpticTree(c)(parent :+ Step.Field(sourceName))
    val atNew = stepsToDynamicOpticTree(c)(parent :+ Step.Field(targetName))

    val fieldSchema = summonSchemaFor(c)(tTpe)

    val capturedDefault =
      q"_root_.zio.blocks.schema.migration.MigrationSchemaExpr.captureDefaultIfMarker[$weakTypeOf[A], $tTpe](" +
        q"${default.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[$weakTypeOf[A], $tTpe]], " +
        q"$fieldSchema.asInstanceOf[_root_.zio.blocks.schema.Schema[$tTpe]])"

    val renameAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Rename(at = $atOld, to = $targetName)"

    val mandateAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Mandate(" +
        q"at = $atNew, default = $capturedDefault.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]])"

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($renameAction).copyAppended($mandateAction)")
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

    val (sourceSteps, _) = selectorToSteps(c)(source.asInstanceOf[c.Expr[Any => Any]])
    val (targetSteps, _) = selectorToSteps(c)(target.asInstanceOf[c.Expr[Any => Any]])

    val sourceName = requireEndsInField(c)(sourceSteps, "optionalizeField(source)")
    val targetName = requireEndsInField(c)(targetSteps, "optionalizeField(target)")
    val parent     = parentStepsOfField(c)(sourceSteps, "optionalizeField(source)")

    val atOld = stepsToDynamicOpticTree(c)(parent :+ Step.Field(sourceName))
    val atNew = stepsToDynamicOpticTree(c)(parent :+ Step.Field(targetName))

    val renameAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Rename(at = $atOld, to = $targetName)"
    val optAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Optionalize(at = $atNew)"

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($renameAction).copyAppended($optAction)")
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

    val (sourceSteps, _) = selectorToSteps(c)(source.asInstanceOf[c.Expr[Any => Any]])
    val (targetSteps, _) = selectorToSteps(c)(target.asInstanceOf[c.Expr[Any => Any]])

    val sourceName = requireEndsInField(c)(sourceSteps, "changeFieldType(source)")
    val targetName = requireEndsInField(c)(targetSteps, "changeFieldType(target)")
    val parent     = parentStepsOfField(c)(sourceSteps, "changeFieldType(source)")

    val atOld = stepsToDynamicOpticTree(c)(parent :+ Step.Field(sourceName))
    val atNew = stepsToDynamicOpticTree(c)(parent :+ Step.Field(targetName))

    val renameAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.Rename(at = $atOld, to = $targetName)"

    val changeAction =
      q"_root_.zio.blocks.schema.migration.MigrationAction.ChangeType(" +
        q"at = $atNew, converter = ${converter.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]])"

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($renameAction).copyAppended($changeAction)")
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

    val self = c.prefix
    val (steps, _) = selectorToSteps(c)(at.asInstanceOf[c.Expr[Any => Any]])
    val atDyn = stepsToDynamicOpticTree(c)(steps)

    val action =
      q"_root_.zio.blocks.schema.migration.MigrationAction.RenameCase(at = $atDyn, from = ${from.tree}, to = ${to.tree})"

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }

  // ----------------------------
  // transformCaseAt
  // ----------------------------

  def transformCaseAtImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CaseA: c.WeakTypeTag, CaseB: c.WeakTypeTag](
    c: blackbox.Context
  )(
    at: c.Expr[A => CaseA],
    caseMigration: c.Expr[MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]]
  )(
    sa: c.Expr[Schema[CaseA]],
    sb: c.Expr[Schema[CaseB]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self = c.prefix
    val (steps, _) = selectorToSteps(c)(at.asInstanceOf[c.Expr[Any => Any]])
    val atDyn = stepsToDynamicOpticTree(c)(steps)

    val nested =
      q"${caseMigration.tree}(new _root_.zio.blocks.schema.migration.MigrationBuilder[${weakTypeOf[CaseA]}, ${weakTypeOf[CaseB]}]($sa, $sb, _root_.scala.Vector.empty))"

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

    val self = c.prefix
    val (steps, _) = selectorToSteps(c)(at.asInstanceOf[c.Expr[Any => Any]])
    val atDyn = stepsToDynamicOpticTree(c)(steps)

    val action =
      q"_root_.zio.blocks.schema.migration.MigrationAction.TransformElements(" +
        q"at = $atDyn, transform = ${transform.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]])"

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[A, _]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self = c.prefix
    val (steps, _) = selectorToSteps(c)(at.asInstanceOf[c.Expr[Any => Any]])
    val atDyn = stepsToDynamicOpticTree(c)(steps)

    val action =
      q"_root_.zio.blocks.schema.migration.MigrationAction.TransformKeys(" +
        q"at = $atDyn, transform = ${transform.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]])"

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[A, _]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val self = c.prefix
    val (steps, _) = selectorToSteps(c)(at.asInstanceOf[c.Expr[Any => Any]])
    val atDyn = stepsToDynamicOpticTree(c)(steps)

    val action =
      q"_root_.zio.blocks.schema.migration.MigrationAction.TransformValues(" +
        q"at = $atDyn, transform = ${transform.tree}.asInstanceOf[_root_.zio.blocks.schema.SchemaExpr[Any, Any]])"

    c.Expr[MigrationBuilder[A, B]](q"$self.copyAppended($action)")
  }
}
