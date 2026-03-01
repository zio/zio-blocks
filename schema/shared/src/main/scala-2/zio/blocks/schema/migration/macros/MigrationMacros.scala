package zio.blocks.schema.migration.macros

import scala.reflect.macros.whitebox
import zio.blocks.schema.migration.{MigrationBuilder, MigrationState, ToDynamicOptic}
// Explicitly import the 1-arg SchemaExpr to match the Builder
import zio.blocks.schema.migration.SchemaExpr

object MigrationMacros {

  def verifyMigrationImpl[A, B, S](c: whitebox.Context)(implicit
    evA: c.WeakTypeTag[A],
    evB: c.WeakTypeTag[B],
    evS: c.WeakTypeTag[S]
  ): c.Expr[zio.blocks.schema.migration.Migration[A, B]] = {
    import c.universe._

    val sourceSchema     = extractSchema(c)(evA.tpe.dealias)
    val targetSchema     = extractSchema(c)(evB.tpe.dealias)
    val historyType      = evS.tpe.dealias.widen
    val calculatedSchema = validateAndSimulate(c)(historyType, sourceSchema)

    if (calculatedSchema != targetSchema) {
      val missingKeys = targetSchema.keySet -- calculatedSchema.keySet
      val extraKeys   = calculatedSchema.keySet -- targetSchema.keySet

      val typeMismatches = targetSchema.collect {
        case (name, expectedType)
            if calculatedSchema.get(name).exists(got => got != expectedType && !got.endsWith(expectedType)) =>
          name -> (calculatedSchema(name), expectedType)
      }

      if (missingKeys.nonEmpty || extraKeys.nonEmpty || typeMismatches.nonEmpty) {
        val mismatchMsg = typeMismatches.map { case (name, (got, expected)) =>
          s"  - Field '$name': Expected '$expected', but got '$got'."
        }.mkString("\n")

        c.abort(
          c.enclosingPosition,
          s"""
             | STATIC MIGRATION VALIDATION FAILED!
             |-------------------------------------
             |Source Schema:     ${sourceSchema.keys.mkString(", ")}
             |Target Schema:     ${targetSchema.keys.mkString(", ")}
             |Calculated Schema: ${calculatedSchema.keys.mkString(", ")}
             |
             |Errors:
             |${if (missingKeys.nonEmpty) s"  - Missing Fields: ${missingKeys.mkString(", ")}" else ""}
             |${if (extraKeys.nonEmpty) s"  - Unexpected Fields: ${extraKeys.mkString(", ")}" else ""}
             |${if (typeMismatches.nonEmpty) s"  - TYPE MISMATCHES:\n$mismatchMsg" else ""}
             |""".stripMargin
        )
      }
    }
    val builder = c.prefix
    c.Expr[zio.blocks.schema.migration.Migration[A, B]](q"$builder.buildPartial")
  }

  private def validateAndSimulate(
    c: whitebox.Context
  )(state: c.Type, currentSchema: Map[String, String]): Map[String, String] = {
    import c.universe._
    val tpe = state.dealias.widen
    if (tpe <:< typeOf[MigrationState.Empty]) return currentSchema
    val args = tpe.typeArgs
    val sym  = tpe.typeSymbol

    if (sym == symbolOf[MigrationState.RenameField[_, _, _]]) {
      val from         = extractConstantName(c)(args(0))
      val to           = extractConstantName(c)(args(1))
      val prev         = args(2)
      val schemaBefore = validateAndSimulate(c)(prev, currentSchema)
      if (!schemaBefore.contains(from))
        c.abort(
          c.enclosingPosition,
          s" Invalid Rename: Field '$from' does not exist in [${schemaBefore.keys.mkString(", ")}]."
        )
      val typeSig = schemaBefore(from)
      (schemaBefore - from) + (to -> typeSig)

    } else if (sym == symbolOf[MigrationState.AddField[_, _, _]]) {
      val name = extractConstantName(c)(args(0)); val tpeSig = args(1).toString; val prev = args(2)
      validateAndSimulate(c)(prev, currentSchema) + (name -> tpeSig)

    } else if (sym == symbolOf[MigrationState.DropField[_, _]]) {
      val name = extractConstantName(c)(args(0)); val prev = args(1)
      validateAndSimulate(c)(prev, currentSchema) - name

    } else if (sym == symbolOf[MigrationState.ChangeType[_, _, _, _]]) {
      val name         = extractConstantName(c)(args(0))
      val toType       = extractConstantName(c)(args(2))
      val prev         = args(3)
      val schemaBefore = validateAndSimulate(c)(prev, currentSchema)
      if (!schemaBefore.contains(name))
        c.abort(c.enclosingPosition, s" Invalid ChangeType: Field '$name' does not exist.")
      schemaBefore + (name -> toType)

    } else if (sym == symbolOf[MigrationState.MandateField[_, _, _]]) {
      val name         = extractConstantName(c)(args(0))
      val prev         = args(2)
      val schemaBefore = validateAndSimulate(c)(prev, currentSchema)
      if (!schemaBefore.contains(name)) c.abort(c.enclosingPosition, s" Invalid Mandate: Field '$name' does not exist.")
      schemaBefore

    } else if (sym == symbolOf[MigrationState.OptionalizeField[_, _, _]]) {
      val name         = extractConstantName(c)(args(0))
      val prev         = args(2)
      val schemaBefore = validateAndSimulate(c)(prev, currentSchema)
      if (!schemaBefore.contains(name))
        c.abort(c.enclosingPosition, s" Invalid Optionalize: Field '$name' does not exist.")
      schemaBefore

    } else if (args.nonEmpty) validateAndSimulate(c)(args.last, currentSchema)
    else currentSchema
  }

  // --- Implementations ---

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S <: MigrationState.State: c.WeakTypeTag, T: c.WeakTypeTag](
    c: whitebox.Context
  )(target: c.Expr[ToDynamicOptic[B, T]], default: c.Expr[SchemaExpr[_]]): c.Expr[MigrationBuilder[A, B, _]] = {
    import c.universe._
    val nameStr  = extractNameFromOptic(c)(target.tree)
    val nameType = c.internal.constantType(Constant(nameStr))
    val newState = appliedType(symbolOf[MigrationState.AddField[_, _, _]], List(nameType, weakTypeOf[T], weakTypeOf[S]))
    val builder  = c.prefix
    c.Expr[MigrationBuilder[A, B, _]](
      q"$builder.withState[$newState](_root_.zio.blocks.schema.migration.MigrationAction.AddField($target.apply(), $default))"
    )
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S <: MigrationState.State: c.WeakTypeTag, T](
    c: whitebox.Context
  )(
    source: c.Expr[ToDynamicOptic[A, T]],
    defaultForReverse: c.Expr[SchemaExpr[_]]
  ): c.Expr[MigrationBuilder[A, B, _]] = {
    import c.universe._
    val nameStr  = extractNameFromOptic(c)(source.tree)
    val nameType = c.internal.constantType(Constant(nameStr))
    val newState = appliedType(symbolOf[MigrationState.DropField[_, _]], List(nameType, weakTypeOf[S]))
    val builder  = c.prefix
    c.Expr[MigrationBuilder[A, B, _]](
      q"$builder.withState[$newState](_root_.zio.blocks.schema.migration.MigrationAction.DropField($source.apply(), $defaultForReverse))"
    )
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S <: MigrationState.State: c.WeakTypeTag, T1, T2](
    c: whitebox.Context
  )(from: c.Expr[ToDynamicOptic[A, T1]], to: c.Expr[ToDynamicOptic[B, T2]]): c.Expr[MigrationBuilder[A, B, _]] = {
    import c.universe._
    val fromStr  = extractNameFromOptic(c)(from.tree); val toStr = extractNameFromOptic(c)(to.tree)
    val newState = appliedType(
      symbolOf[MigrationState.RenameField[_, _, _]],
      List(c.internal.constantType(Constant(fromStr)), c.internal.constantType(Constant(toStr)), weakTypeOf[S])
    )
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B, _]](
      q"$builder.withState[$newState](_root_.zio.blocks.schema.migration.MigrationAction.Rename($from.apply(), $toStr))"
    )
  }

  def transformFieldImpl[
    A: c.WeakTypeTag,
    B: c.WeakTypeTag,
    S <: MigrationState.State: c.WeakTypeTag,
    T1: c.WeakTypeTag,
    T2: c.WeakTypeTag
  ](c: whitebox.Context)(
    from: c.Expr[ToDynamicOptic[A, T1]],
    to: c.Expr[ToDynamicOptic[B, T2]],
    transform: c.Expr[SchemaExpr[_]]
  ): c.Expr[MigrationBuilder[A, B, _]] = {
    import c.universe._
    val nameStr   = extractNameFromOptic(c)(from.tree)
    val toNameStr = extractNameFromOptic(c)(to.tree)

    if (nameStr != toNameStr)
      c.abort(
        c.enclosingPosition,
        s"transformField requires same field name ($nameStr vs $toNameStr). Use renameField if names differ."
      )

    val fromT    = weakTypeOf[T1].toString
    val toT      = weakTypeOf[T2].toString
    val newState = appliedType(
      symbolOf[MigrationState.ChangeType[_, _, _, _]],
      List(
        c.internal.constantType(Constant(nameStr)),
        c.internal.constantType(Constant(fromT)),
        c.internal.constantType(Constant(toT)),
        weakTypeOf[S]
      )
    )
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B, _]](
      q"$builder.withState[$newState](_root_.zio.blocks.schema.migration.MigrationAction.TransformValue($from.apply(), $transform))"
    )
  }

  // [FIX] Validating that source and target field names match to utilize the 'target' parameter
  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S <: MigrationState.State: c.WeakTypeTag, T: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[ToDynamicOptic[A, Option[T]]],
    target: c.Expr[ToDynamicOptic[B, T]],
    default: c.Expr[SchemaExpr[_]]
  ): c.Expr[MigrationBuilder[A, B, _]] = {
    import c.universe._
    val nameStr   = extractNameFromOptic(c)(source.tree)
    val targetStr = extractNameFromOptic(c)(target.tree)

    if (nameStr != targetStr)
      c.abort(c.enclosingPosition, s"mandateField requires same field name ($nameStr vs $targetStr).")

    val nameType = c.internal.constantType(Constant(nameStr))
    val newState =
      appliedType(symbolOf[MigrationState.MandateField[_, _, _]], List(nameType, weakTypeOf[T], weakTypeOf[S]))
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B, _]](
      q"$builder.withState[$newState](_root_.zio.blocks.schema.migration.MigrationAction.Mandate($source.apply(), $default))"
    )
  }

  // [FIX] Validating that source and target field names match to utilize the 'target' parameter
  def optionalizeFieldImpl[
    A: c.WeakTypeTag,
    B: c.WeakTypeTag,
    S <: MigrationState.State: c.WeakTypeTag,
    T: c.WeakTypeTag
  ](c: whitebox.Context)(
    source: c.Expr[ToDynamicOptic[A, T]],
    target: c.Expr[ToDynamicOptic[B, Option[T]]]
  ): c.Expr[MigrationBuilder[A, B, _]] = {
    import c.universe._
    val nameStr   = extractNameFromOptic(c)(source.tree)
    val targetStr = extractNameFromOptic(c)(target.tree)

    if (nameStr != targetStr)
      c.abort(c.enclosingPosition, s"optionalizeField requires same field name ($nameStr vs $targetStr).")

    val nameType = c.internal.constantType(Constant(nameStr))
    val newState =
      appliedType(symbolOf[MigrationState.OptionalizeField[_, _, _]], List(nameType, weakTypeOf[T], weakTypeOf[S]))
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B, _]](
      q"$builder.withState[$newState](_root_.zio.blocks.schema.migration.MigrationAction.Optionalize($source.apply()))"
    )
  }

  // --- Helpers ---
  private def extractSchema(c: whitebox.Context)(tpe: c.Type): Map[String, String] = {
    import c.universe._
    val ignored = Set(
      "getClass",
      "hashCode",
      "equals",
      "toString",
      "notify",
      "notifyAll",
      "wait",
      "asInstanceOf",
      "isInstanceOf",
      "productArity",
      "productElement",
      "productElementName",
      "productElementNames",
      "productIterator",
      "productPrefix",
      "canEqual",
      "##"
    )
    tpe.members.sorted.flatMap { symbol =>
      symbol match {
        case m: MethodSymbol if m.isPublic && !m.isConstructor =>
          val name      = m.name.decodedName.toString.trim
          val isGarbage = ignored.contains(name) || name.startsWith("copy$") || name.contains("$")
          if (!isGarbage && (m.isCaseAccessor || m.paramLists.flatten.isEmpty)) Some(name -> m.returnType.toString)
          else None
        case _ => None
      }
    }.toMap
  }

  private def extractConstantName(c: whitebox.Context)(tpe: c.Type): String = {
    import c.universe._
    tpe match {
      case ConstantType(Constant(s: String)) => s
      case _                                 => tpe.toString.split('.').last.replace("\"", "").trim
    }
  }

  private def extractNameFromOptic(c: whitebox.Context)(tree: c.Tree): String = {
    import c.universe._
    def findSelectOn(t: Tree, argName: String): Option[String] = t match {
      case Select(Ident(TermName(name)), TermName(field)) if name == argName => Some(field.trim)
      case Block(_, expr)                                                    => findSelectOn(expr, argName)
      case Apply(fun, args)                                                  => args.flatMap(a => findSelectOn(a, argName)).headOption.orElse(findSelectOn(fun, argName))
      case TypeApply(fun, _)                                                 => findSelectOn(fun, argName)
      case _                                                                 => None
    }
    def dive(t: Tree): Option[String] = t match {
      case Function(List(ValDef(_, TermName(argName), _, _)), body) => findSelectOn(body, argName)
      case Block(_, expr)                                           => dive(expr)
      case Apply(fun, args)                                         => args.map(dive).find(_.isDefined).flatten.orElse(dive(fun))
      case TypeApply(fun, _)                                        => dive(fun)
      case _                                                        => None
    }
    dive(tree).getOrElse {
      val str = tree.toString()
      if (str.contains("\"")) str.split("\"")(1)
      else str.split('.').last.replaceAll("[^a-zA-Z0-9]", "").trim
    }
  }
}
