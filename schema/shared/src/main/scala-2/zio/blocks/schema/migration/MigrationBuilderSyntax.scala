package zio.blocks.schema.migration

import scala.reflect.macros.whitebox
import zio.blocks.schema.SchemaExpr

/**
 * Macro implementations for MigrationBuilder methods. Each macro returns a
 * refined type that accumulates field names in the type parameters.
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
   * Create a FieldName[fieldName] type wrapped in a refined type with the
   * current type.
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
          $default.toDynamic
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
          $defaultForReverse.toDynamic
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
          $transform.toDynamic
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
          $default.toDynamic
        )
      )
    }"""
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](
    c: whitebox.Context
  )(
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

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](
    c: whitebox.Context
  )(
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
          $converter.toDynamic
        )
      )
    }"""
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](
    c: whitebox.Context
  )(
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
          $transform.toDynamic
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
          $transform.toDynamic
        )
      )
    }"""
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, SH: c.WeakTypeTag, TP: c.WeakTypeTag](
    c: whitebox.Context
  )(
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
          $transform.toDynamic
        )
      )
    }"""
  }

  def migrateFieldExplicitImpl[
    A: c.WeakTypeTag,
    B: c.WeakTypeTag,
    F1: c.WeakTypeTag,
    F2: c.WeakTypeTag,
    SH: c.WeakTypeTag,
    TP: c.WeakTypeTag
  ](c: whitebox.Context)(
    selector: c.Expr[A => F1],
    migration: c.Expr[Migration[F1, F2]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val f1Type = weakTypeOf[F1]
    val f2Type = weakTypeOf[F2]
    val shType = weakTypeOf[SH]
    val tpType = weakTypeOf[TP]

    val sourceFieldName = extractFieldNameFromSelector(c)(selector.tree)

    val nestedSourceFields = extractCaseClassFields(c)(f1Type)
    val nestedTargetFields = extractCaseClassFields(c)(f2Type)

    var newSHType = shType
    newSHType = createRefinedType(c)(newSHType, sourceFieldName)
    for (nestedField <- nestedSourceFields) {
      val dotPath = s"$sourceFieldName.$nestedField"
      newSHType = createRefinedType(c)(newSHType, dotPath)
    }

    var newTPType = tpType
    newTPType = createRefinedType(c)(newTPType, sourceFieldName)
    for (nestedField <- nestedTargetFields) {
      val dotPath = s"$sourceFieldName.$nestedField"
      newTPType = createRefinedType(c)(newTPType, dotPath)
    }

    val sourcePath = SelectorMacros.toPathImpl[A, F1](c)(selector.asInstanceOf[c.Expr[A => F1]])

    q"""{
      val sourcePath = $sourcePath
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newSHType, $newTPType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.ApplyMigration(
          sourcePath,
          $migration.dynamicMigration
        )
      )
    }"""
  }

  def migrateFieldImplicitImpl[
    A: c.WeakTypeTag,
    B: c.WeakTypeTag,
    F1: c.WeakTypeTag,
    F2: c.WeakTypeTag,
    SH: c.WeakTypeTag,
    TP: c.WeakTypeTag
  ](c: whitebox.Context)(
    selector: c.Expr[A => F1]
  )(
    migration: c.Expr[Migration[F1, F2]]
  ): c.Tree =
    migrateFieldExplicitImpl[A, B, F1, F2, SH, TP](c)(selector, migration)
  private def extractCaseClassFields(c: whitebox.Context)(tpe: c.universe.Type): List[String] = {
    import c.universe._

    val sym = tpe.typeSymbol
    if (sym.isClass && sym.asClass.isCaseClass) {
      val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
      ctor.paramLists.headOption.getOrElse(Nil).map(_.name.decodedName.toString)
    } else {
      Nil
    }
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

    val sourceFields   = extractCaseClassFieldsWithNested(c)(sourceType, "")
    val targetFields   = extractCaseClassFieldsWithNested(c)(targetType, "")
    val handledFields  = extractIntersectionElements(c)(shType)
    val providedFields = extractIntersectionElements(c)(tpType)

    val autoMapped = computeAutoMappedWithNested(c)(sourceType, targetType, "")

    val allExplicitlyHandled = handledFields ++ providedFields
    val crossTypeAutoMapped  = computeCrossTypeAutoMapped(c)(sourceType, targetType, allExplicitlyHandled, "")

    var coveredSource = handledFields ++ autoMapped ++ crossTypeAutoMapped
    var coveredTarget = providedFields ++ autoMapped ++ crossTypeAutoMapped

    coveredSource = inferParentCoverage(sourceFields, coveredSource)
    coveredTarget = inferParentCoverage(targetFields, coveredTarget)

    val missingTarget   = targetFields -- coveredTarget
    val unhandledSource = sourceFields -- coveredSource

    if (missingTarget.nonEmpty || unhandledSource.nonEmpty) {
      val errors = new StringBuilder("Migration incomplete:\n")

      if (missingTarget.nonEmpty) {
        errors.append(s"\n  Target fields not provided: ${missingTarget.mkString(", ")}\n")
        errors.append("  Use addField, renameField, transformField, or migrateField to provide these fields.\n")
      }

      if (unhandledSource.nonEmpty) {
        errors.append(s"\n  Source fields not handled: ${unhandledSource.mkString(", ")}\n")
        errors.append("  Use dropField, renameField, transformField, or migrateField to handle these fields.\n")
      }

      errors.append("\n  Alternatively, use .buildPartial to skip validation.")

      c.abort(c.enclosingPosition, errors.toString)
    }

    q"_root_.zio.blocks.schema.migration.MigrationComplete.unsafeCreate[$sourceType, $targetType, $shType, $tpType]"
  }

  private def extractCaseClassFieldsWithNested(
    c: whitebox.Context
  )(tpe: c.universe.Type, prefix: String): Set[String] = {
    import c.universe._

    val sym = tpe.typeSymbol
    if (sym.isClass && sym.asClass.isCaseClass) {
      val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
      ctor.paramLists.headOption
        .getOrElse(Nil)
        .flatMap { param =>
          val fieldName =
            if (prefix.isEmpty) param.name.decodedName.toString else s"$prefix.${param.name.decodedName.toString}"
          val fieldType = param.typeSignature.asSeenFrom(tpe, sym)
          val fieldSym  = fieldType.typeSymbol

          if (fieldSym.isClass && fieldSym.asClass.isCaseClass) {
            Set(fieldName) ++ extractNestedFieldNames(c)(fieldType, fieldName)
          } else {
            Set(fieldName)
          }
        }
        .toSet
    } else {
      Set.empty
    }
  }

  private def extractNestedFieldNames(c: whitebox.Context)(tpe: c.universe.Type, prefix: String): Set[String] = {
    import c.universe._

    val sym = tpe.typeSymbol
    if (sym.isClass && sym.asClass.isCaseClass) {
      val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
      ctor.paramLists.headOption
        .getOrElse(Nil)
        .flatMap { param =>
          val fieldName = s"$prefix.${param.name.decodedName.toString}"
          val fieldType = param.typeSignature.asSeenFrom(tpe, sym)
          val fieldSym  = fieldType.typeSymbol

          if (fieldSym.isClass && fieldSym.asClass.isCaseClass) {
            Set(fieldName) ++ extractNestedFieldNames(c)(fieldType, fieldName)
          } else {
            Set(fieldName)
          }
        }
        .toSet
    } else {
      Set.empty
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

  private def computeAutoMappedWithNested(
    c: whitebox.Context
  )(sourceType: c.universe.Type, targetType: c.universe.Type, prefix: String): Set[String] = {
    import c.universe._

    val sourceSym = sourceType.typeSymbol
    val targetSym = targetType.typeSymbol

    if (sourceSym.isClass && sourceSym.asClass.isCaseClass && targetSym.isClass && targetSym.asClass.isCaseClass) {
      val sourceCtor = sourceType.decl(termNames.CONSTRUCTOR).asMethod
      val targetCtor = targetType.decl(termNames.CONSTRUCTOR).asMethod

      val sourceFieldTypes: Map[String, Type] = sourceCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(sourceType, sourceSym))
        .toMap

      val targetFieldTypes: Map[String, Type] = targetCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(targetType, targetSym))
        .toMap

      val commonFields = sourceFieldTypes.keySet.intersect(targetFieldTypes.keySet)

      commonFields.flatMap { fieldName =>
        val fullFieldName = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
        (sourceFieldTypes.get(fieldName), targetFieldTypes.get(fieldName)) match {
          case (Some(srcType), Some(tgtType)) if srcType =:= tgtType =>
            Set(fullFieldName) ++ computeAutoMappedNested(c)(srcType, tgtType, fullFieldName)
          case (Some(srcType), Some(tgtType)) if srcType <:< tgtType || tgtType <:< srcType =>
            Set(fullFieldName)
          case _ => Set.empty[String]
        }
      }
    } else {
      Set.empty
    }
  }

  private def computeAutoMappedNested(
    c: whitebox.Context
  )(srcType: c.universe.Type, tgtType: c.universe.Type, prefix: String): Set[String] = {
    import c.universe._

    val srcSym = srcType.typeSymbol
    val tgtSym = tgtType.typeSymbol

    if (srcSym.isClass && srcSym.asClass.isCaseClass && tgtSym.isClass && tgtSym.asClass.isCaseClass) {
      val srcCtor = srcType.decl(termNames.CONSTRUCTOR).asMethod
      val tgtCtor = tgtType.decl(termNames.CONSTRUCTOR).asMethod

      val srcFields: Map[String, Type] = srcCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(srcType, srcSym))
        .toMap

      val tgtFields: Map[String, Type] = tgtCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(tgtType, tgtSym))
        .toMap

      val commonFields = srcFields.keySet.intersect(tgtFields.keySet)

      commonFields.flatMap { fieldName =>
        val fullFieldName = s"$prefix.$fieldName"
        (srcFields.get(fieldName), tgtFields.get(fieldName)) match {
          case (Some(srcFieldType), Some(tgtFieldType)) if srcFieldType =:= tgtFieldType =>
            Set(fullFieldName) ++ computeAutoMappedNested(c)(srcFieldType, tgtFieldType, fullFieldName)
          case (Some(srcFieldType), Some(tgtFieldType))
              if srcFieldType <:< tgtFieldType || tgtFieldType <:< srcFieldType =>
            Set(fullFieldName)
          case _ => Set.empty[String]
        }
      }
    } else {
      Set.empty
    }
  }

  private def computeCrossTypeAutoMapped(
    c: whitebox.Context
  )(
    sourceType: c.universe.Type,
    targetType: c.universe.Type,
    explicitlyHandled: Set[String],
    prefix: String
  ): Set[String] = {
    import c.universe._

    val sourceSym = sourceType.typeSymbol
    val targetSym = targetType.typeSymbol

    val sourceFieldTypes: Map[String, Type] =
      if (sourceSym.isClass && sourceSym.asClass.isCaseClass) {
        val ctor = sourceType.decl(termNames.CONSTRUCTOR).asMethod
        ctor.paramLists.headOption
          .getOrElse(Nil)
          .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(sourceType, sourceSym))
          .toMap
      } else Map.empty

    val targetFieldTypes: Map[String, Type] =
      if (targetSym.isClass && targetSym.asClass.isCaseClass) {
        val ctor = targetType.decl(termNames.CONSTRUCTOR).asMethod
        ctor.paramLists.headOption
          .getOrElse(Nil)
          .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(targetType, targetSym))
          .toMap
      } else Map.empty

    val commonFields = sourceFieldTypes.keySet.intersect(targetFieldTypes.keySet)

    commonFields.flatMap { fieldName =>
      val fullFieldName = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
      (sourceFieldTypes.get(fieldName), targetFieldTypes.get(fieldName)) match {
        case (Some(srcType), Some(tgtType)) if !(srcType =:= tgtType) =>
          val hasExplicitChild = explicitlyHandled.exists(_.startsWith(s"$fullFieldName."))
          if (hasExplicitChild)
            crossTypeAutoMapLeaves(c)(srcType, tgtType, fullFieldName)
          else Set.empty[String]
        case _ => Set.empty[String]
      }
    }
  }

  private def crossTypeAutoMapLeaves(
    c: whitebox.Context
  )(srcType: c.universe.Type, tgtType: c.universe.Type, prefix: String): Set[String] = {
    import c.universe._

    val srcSym = srcType.typeSymbol
    val tgtSym = tgtType.typeSymbol

    val srcFields: Map[String, Type] =
      if (srcSym.isClass && srcSym.asClass.isCaseClass) {
        val ctor = srcType.decl(termNames.CONSTRUCTOR).asMethod
        ctor.paramLists.headOption
          .getOrElse(Nil)
          .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(srcType, srcSym))
          .toMap
      } else Map.empty

    val tgtFields: Map[String, Type] =
      if (tgtSym.isClass && tgtSym.asClass.isCaseClass) {
        val ctor = tgtType.decl(termNames.CONSTRUCTOR).asMethod
        ctor.paramLists.headOption
          .getOrElse(Nil)
          .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(tgtType, tgtSym))
          .toMap
      } else Map.empty

    val common = srcFields.keySet.intersect(tgtFields.keySet)

    common.flatMap { fieldName =>
      val fullName = s"$prefix.$fieldName"
      (srcFields(fieldName), tgtFields(fieldName)) match {
        case (s, t) if s =:= t => Set(fullName)
        case _                 => Set.empty[String]
      }
    }
  }

  private def inferParentCoverage(allFields: Set[String], covered: Set[String]): Set[String] = {
    var result  = covered
    val parents = allFields.flatMap { f =>
      val parts = f.split('.')
      if (parts.length > 1) Some(parts.init.mkString(".")) else None
    }
    for (parent <- parents) {
      val children = allFields.filter(_.startsWith(s"$parent."))
      if (children.nonEmpty && children.forall(result.contains))
        result = result + parent
    }
    result
  }
}
