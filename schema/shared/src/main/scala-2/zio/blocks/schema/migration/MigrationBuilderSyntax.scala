package zio.blocks.schema.migration

import scala.reflect.macros.whitebox
import zio.blocks.schema.SchemaExpr

/**
 * Macro implementations for MigrationBuilder methods.
 * Each macro returns a refined type that accumulates field names in the type parameters.
 */
object MigrationBuilderMacros {

  /**
   * Helper to extract field name from a selector expression.
   */
  private def extractFieldNameFromSelector(c: whitebox.Context)(selector: c.Tree): String = {
    import c.universe._

    def extractFromBody(body: c.Tree): String = body match {
      case Select(_, fieldName) => fieldName.decodedName.toString
      case _                    => c.abort(c.enclosingPosition, s"Cannot extract field name from selector: ${showRaw(body)}")
    }

    selector match {
      case q"($_) => $body" => extractFromBody(body)
      case _                => c.abort(c.enclosingPosition, s"Expected a lambda expression, got: ${showRaw(selector)}")
    }
  }

  /**
   * Create a FieldName[fieldName] type wrapped in a refined type with the current type.
   */
  private def createRefinedType(c: whitebox.Context)(currentType: c.Type, fieldName: String): c.Type = {
    import c.universe._

    val fieldNameLiteral = c.internal.constantType(Constant(fieldName))
    val fieldNameTpe     = appliedType(typeOf[FieldName[_]].typeConstructor, fieldNameLiteral)
    c.internal.refinedType(List(currentType, fieldNameTpe), c.internal.newScopeWith())
  }

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val shType = weakTypeOf[SH]
    val tpType = weakTypeOf[TP]

    val fieldName = extractFieldNameFromSelector(c)(target.tree)
    val newTPType = createRefinedType(c)(tpType, fieldName)

    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val targetPath = $targetPath
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $shType, $newTPType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.AddField(
          targetPath,
          $default
        )
      )
    }"""
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val shType = weakTypeOf[SH]
    val tpType = weakTypeOf[TP]

    val fieldName = extractFieldNameFromSelector(c)(source.tree)
    val newSHType = createRefinedType(c)(shType, fieldName)

    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])

    q"""{
      val sourcePath = $sourcePath
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newSHType, $tpType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.DropField(
          sourcePath,
          $defaultForReverse
        )
      )
    }"""
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val shType = weakTypeOf[SH]
    val tpType = weakTypeOf[TP]

    val fromFieldName = extractFieldNameFromSelector(c)(from.tree)
    val toFieldName   = extractFieldNameFromSelector(c)(to.tree)
    val newSHType     = createRefinedType(c)(shType, fromFieldName)
    val newTPType     = createRefinedType(c)(tpType, toFieldName)

    val fromPath = SelectorMacros.toPathImpl[A, Any](c)(from.asInstanceOf[c.Expr[A => Any]])
    val toPath   = SelectorMacros.toPathImpl[B, Any](c)(to.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val fromPath = $fromPath
      val toPath = $toPath
      val toName = toPath.nodes.lastOption match {
        case _root_.scala.Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Target selector must end with a field access")
      }
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newSHType, $newTPType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Rename(fromPath, toName)
      )
    }"""
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val shType = weakTypeOf[SH]
    val tpType = weakTypeOf[TP]

    val fromFieldName = extractFieldNameFromSelector(c)(from.tree)
    val toFieldName   = extractFieldNameFromSelector(c)(to.tree)
    val newSHType     = createRefinedType(c)(shType, fromFieldName)
    val newTPType     = createRefinedType(c)(tpType, toFieldName)

    val fromPath = SelectorMacros.toPathImpl[A, Any](c)(from.asInstanceOf[c.Expr[A => Any]])
    val toPath   = SelectorMacros.toPathImpl[B, Any](c)(to.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val fromPath = $fromPath
      val toPath = $toPath
      locally(toPath)
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newSHType, $newTPType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformValue(
          fromPath,
          $transform
        )
      )
    }"""
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Option[_]],
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val shType = weakTypeOf[SH]
    val tpType = weakTypeOf[TP]

    val sourceFieldName = extractFieldNameFromSelector(c)(source.tree)
    val targetFieldName = extractFieldNameFromSelector(c)(target.tree)
    val newSHType       = createRefinedType(c)(shType, sourceFieldName)
    val newTPType       = createRefinedType(c)(tpType, targetFieldName)

    val sourcePath = SelectorMacros.toPathImpl[A, Option[_]](c)(source.asInstanceOf[c.Expr[A => Option[_]]])
    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val sourcePath = $sourcePath
      val targetPath = $targetPath
      locally(targetPath)
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newSHType, $newTPType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Mandate(
          sourcePath,
          $default
        )
      )
    }"""
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    target: c.Expr[B => Option[_]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val shType = weakTypeOf[SH]
    val tpType = weakTypeOf[TP]

    val sourceFieldName = extractFieldNameFromSelector(c)(source.tree)
    val targetFieldName = extractFieldNameFromSelector(c)(target.tree)
    val newSHType       = createRefinedType(c)(shType, sourceFieldName)
    val newTPType       = createRefinedType(c)(tpType, targetFieldName)

    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    val targetPath = SelectorMacros.toPathImpl[B, Option[_]](c)(target.asInstanceOf[c.Expr[B => Option[_]]])

    q"""{
      val sourcePath = $sourcePath
      val targetPath = $targetPath
      locally(targetPath)
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newSHType, $newTPType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Optionalize(sourcePath)
      )
    }"""
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    target: c.Expr[B => Any],
    converter: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val shType = weakTypeOf[SH]
    val tpType = weakTypeOf[TP]

    val sourceFieldName = extractFieldNameFromSelector(c)(source.tree)
    val targetFieldName = extractFieldNameFromSelector(c)(target.tree)
    val newSHType       = createRefinedType(c)(shType, sourceFieldName)
    val newTPType       = createRefinedType(c)(tpType, targetFieldName)

    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val sourcePath = $sourcePath
      val targetPath = $targetPath
      locally(targetPath)
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newSHType, $newTPType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.ChangeType(
          sourcePath,
          $converter
        )
      )
    }"""
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Iterable[_]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val shType = weakTypeOf[SH]
    val tpType = weakTypeOf[TP]

    val path = SelectorMacros.toPathImpl[A, Iterable[_]](c)(at.asInstanceOf[c.Expr[A => Iterable[_]]])

    q"""{
      val path = $path
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $shType, $tpType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformElements(
          path,
          $transform
        )
      )
    }"""
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val shType = weakTypeOf[SH]
    val tpType = weakTypeOf[TP]

    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])

    q"""{
      val path = $path
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $shType, $tpType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformKeys(
          path,
          $transform
        )
      )
    }"""
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val shType = weakTypeOf[SH]
    val tpType = weakTypeOf[TP]

    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])

    q"""{
      val path = $path
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $shType, $tpType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformValues(
          path,
          $transform
        )
      )
    }"""
  }
}

/**
 * Macro implementations for MigrationComplete validation.
 */
object MigrationValidationMacros {

  def validateMigrationImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](
    c: whitebox.Context
  ): c.Tree = {
    import c.universe._

    val sourceType = weakTypeOf[A]
    val targetType = weakTypeOf[B]
    val shType     = weakTypeOf[SH]
    val tpType     = weakTypeOf[TP]

    val sourceFields   = extractCaseClassFields(c)(sourceType)
    val targetFields   = extractCaseClassFields(c)(targetType)
    val handledFields  = extractIntersectionElements(c)(shType)
    val providedFields = extractIntersectionElements(c)(tpType)

    val sourceFieldTypes = extractCaseClassFieldTypes(c)(sourceType)
    val targetFieldTypes = extractCaseClassFieldTypes(c)(targetType)
    val autoMapped       = computeAutoMapped(c)(sourceFields, targetFields, sourceFieldTypes, targetFieldTypes)

    val coveredSource = handledFields ++ autoMapped
    val coveredTarget = providedFields ++ autoMapped

    val missingTarget   = targetFields -- coveredTarget
    val unhandledSource = sourceFields -- coveredSource

    if (missingTarget.nonEmpty || unhandledSource.nonEmpty) {
      val errors = new StringBuilder("Migration incomplete:\n")

      if (missingTarget.nonEmpty) {
        errors.append(s"\n  Target fields not provided: ${missingTarget.mkString(", ")}\n")
        errors.append("  Use addField, renameField, or transformField to provide these fields.\n")
      }

      if (unhandledSource.nonEmpty) {
        errors.append(s"\n  Source fields not handled: ${unhandledSource.mkString(", ")}\n")
        errors.append("  Use dropField, renameField, or transformField to handle these fields.\n")
      }

      errors.append("\n  Alternatively, use .buildPartial to skip validation.")

      c.abort(c.enclosingPosition, errors.toString)
    }

    q"_root_.zio.blocks.schema.migration.MigrationComplete.unsafeCreate[$sourceType, $targetType, $shType, $tpType]"
  }

  private def extractCaseClassFields(c: whitebox.Context)(tpe: c.universe.Type): Set[String] = {
    import c.universe._

    val sym = tpe.typeSymbol
    if (sym.isClass && sym.asClass.isCaseClass) {
      val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
      ctor.paramLists.headOption.getOrElse(Nil).map(_.name.decodedName.toString).toSet
    } else {
      Set.empty
    }
  }

  private def extractCaseClassFieldTypes(c: whitebox.Context)(tpe: c.universe.Type): Map[String, c.universe.Type] = {
    import c.universe._

    val sym = tpe.typeSymbol
    if (sym.isClass && sym.asClass.isCaseClass) {
      val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
      ctor.paramLists.headOption
        .getOrElse(Nil)
        .map { param =>
          param.name.decodedName.toString -> param.typeSignature.asSeenFrom(tpe, sym)
        }
        .toMap
    } else {
      Map.empty
    }
  }

  private def extractIntersectionElements(c: whitebox.Context)(tpe: c.universe.Type): Set[String] = {
    import c.universe._

    def extract(t: Type): Set[String] = t.dealias match {
      case RefinedType(parents, _) =>
        parents.flatMap(extract).toSet
      case ConstantType(Constant(s: String)) =>
        Set(s)
      case t if t =:= typeOf[Any] =>
        Set.empty
      case TypeRef(_, sym, List(arg)) if sym.name.toString == "FieldName" =>
        extract(arg)
      case _ =>
        Set.empty
    }

    extract(tpe)
  }

  private def computeAutoMapped(c: whitebox.Context)(
    sourceFields: Set[String],
    targetFields: Set[String],
    sourceFieldTypes: Map[String, c.universe.Type],
    targetFieldTypes: Map[String, c.universe.Type]
  ): Set[String] = {
    val commonFields = sourceFields.intersect(targetFields)
    commonFields.filter { fieldName =>
      (sourceFieldTypes.get(fieldName), targetFieldTypes.get(fieldName)) match {
        case (Some(srcType), Some(tgtType)) =>
          srcType <:< tgtType || tgtType <:< srcType || srcType =:= tgtType
        case _ => false
      }
    }
  }
}
