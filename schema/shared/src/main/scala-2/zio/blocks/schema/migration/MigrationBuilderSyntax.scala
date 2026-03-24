package zio.blocks.schema.migration

import scala.reflect.macros.whitebox
import zio.blocks.schema.SchemaExpr

object MigrationBuilderMacros {

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

  private def createRefinedType(c: whitebox.Context)(currentType: c.Type, fieldName: String): c.Type = {
    import c.universe._

    val fieldNameLiteral = c.internal.constantType(Constant(fieldName))
    val fieldNameTpe     = appliedType(typeOf[Changeset.FieldName[_]].typeConstructor, fieldNameLiteral)
    c.internal.refinedType(List(currentType, fieldNameTpe), c.internal.newScopeWith())
  }

  private def addToChangeset(c: whitebox.Context)(currentType: c.Type, opType: c.Type): c.Type =
    c.internal.refinedType(List(currentType, opType), c.internal.newScopeWith())

  private def createAddFieldType(c: whitebox.Context)(fieldName: String): c.Type = {
    import c.universe._
    val fieldNameLiteral = c.internal.constantType(Constant(fieldName))
    appliedType(typeOf[Changeset.AddField[_]].typeConstructor, fieldNameLiteral)
  }

  private def createDropFieldType(c: whitebox.Context)(fieldName: String): c.Type = {
    import c.universe._
    val fieldNameLiteral = c.internal.constantType(Constant(fieldName))
    appliedType(typeOf[Changeset.DropField[_]].typeConstructor, fieldNameLiteral)
  }

  private def createRenameFieldType(c: whitebox.Context)(from: String, to: String): c.Type = {
    import c.universe._
    val fromLiteral = c.internal.constantType(Constant(from))
    val toLiteral   = c.internal.constantType(Constant(to))
    appliedType(typeOf[Changeset.RenameField[_, _]].typeConstructor, fromLiteral, toLiteral)
  }

  private def createTransformFieldType(c: whitebox.Context)(from: String, to: String): c.Type = {
    import c.universe._
    val fromLiteral = c.internal.constantType(Constant(from))
    val toLiteral   = c.internal.constantType(Constant(to))
    appliedType(typeOf[Changeset.TransformField[_, _]].typeConstructor, fromLiteral, toLiteral)
  }

  private def createMandateFieldType(c: whitebox.Context)(source: String, target: String): c.Type = {
    import c.universe._
    val sourceLiteral = c.internal.constantType(Constant(source))
    val targetLiteral = c.internal.constantType(Constant(target))
    appliedType(typeOf[Changeset.MandateField[_, _]].typeConstructor, sourceLiteral, targetLiteral)
  }

  private def createOptionalizeFieldType(c: whitebox.Context)(source: String, target: String): c.Type = {
    import c.universe._
    val sourceLiteral = c.internal.constantType(Constant(source))
    val targetLiteral = c.internal.constantType(Constant(target))
    appliedType(typeOf[Changeset.OptionalizeField[_, _]].typeConstructor, sourceLiteral, targetLiteral)
  }

  private def createChangeFieldTypeType(c: whitebox.Context)(source: String, target: String): c.Type = {
    import c.universe._
    val sourceLiteral = c.internal.constantType(Constant(source))
    val targetLiteral = c.internal.constantType(Constant(target))
    appliedType(typeOf[Changeset.ChangeFieldType[_, _]].typeConstructor, sourceLiteral, targetLiteral)
  }

  private def createMigrateFieldType(c: whitebox.Context)(name: String): c.Type = {
    import c.universe._
    val nameLiteral = c.internal.constantType(Constant(name))
    appliedType(typeOf[Changeset.MigrateField[_]].typeConstructor, nameLiteral)
  }

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val fieldName    = extractFieldNameFromSelector(c)(target.tree)
    val addFieldType = createAddFieldType(c)(fieldName)
    val newCSType    = addToChangeset(c)(csType, addFieldType)

    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val targetPath = $targetPath
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.AddField(
          targetPath,
          $default.toDynamic
        )
      )
    }"""
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val fieldName     = extractFieldNameFromSelector(c)(source.tree)
    val dropFieldType = createDropFieldType(c)(fieldName)
    val newCSType     = addToChangeset(c)(csType, dropFieldType)

    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])

    q"""{
      val sourcePath = $sourcePath
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.DropField(
          sourcePath,
          $defaultForReverse.toDynamic
        )
      )
    }"""
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val fromFieldName   = extractFieldNameFromSelector(c)(from.tree)
    val toFieldName     = extractFieldNameFromSelector(c)(to.tree)
    val renameFieldType = createRenameFieldType(c)(fromFieldName, toFieldName)
    val newCSType       = addToChangeset(c)(csType, renameFieldType)

    val fromPath = SelectorMacros.toPathImpl[A, Any](c)(from.asInstanceOf[c.Expr[A => Any]])
    val toPath   = SelectorMacros.toPathImpl[B, Any](c)(to.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val fromPath = $fromPath
      val toPath = $toPath
      val toName = toPath.nodes.lastOption match {
        case _root_.scala.Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Target selector must end with a field access")
      }
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.RenameField(fromPath, toName)
      )
    }"""
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val fromFieldName      = extractFieldNameFromSelector(c)(from.tree)
    val toFieldName        = extractFieldNameFromSelector(c)(to.tree)
    val transformFieldType = createTransformFieldType(c)(fromFieldName, toFieldName)
    val newCSType          = addToChangeset(c)(csType, transformFieldType)

    val fromPath = SelectorMacros.toPathImpl[A, Any](c)(from.asInstanceOf[c.Expr[A => Any]])
    val toPath   = SelectorMacros.toPathImpl[B, Any](c)(to.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val fromPath = $fromPath
      val toPath = $toPath
      locally(toPath)
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformField(
          fromPath,
          $transform.toDynamic
        )
      )
    }"""
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Option[_]],
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val sourceFieldName  = extractFieldNameFromSelector(c)(source.tree)
    val targetFieldName  = extractFieldNameFromSelector(c)(target.tree)
    val mandateFieldType = createMandateFieldType(c)(sourceFieldName, targetFieldName)
    val newCSType        = addToChangeset(c)(csType, mandateFieldType)

    val sourcePath = SelectorMacros.toPathImpl[A, Option[_]](c)(source.asInstanceOf[c.Expr[A => Option[_]]])
    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val sourcePath = $sourcePath
      val targetPath = $targetPath
      locally(targetPath)
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.MandateField(
          sourcePath,
          $default.toDynamic
        )
      )
    }"""
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[A => Any],
    target: c.Expr[B => Option[_]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val sourceFieldName      = extractFieldNameFromSelector(c)(source.tree)
    val targetFieldName      = extractFieldNameFromSelector(c)(target.tree)
    val optionalizeFieldType = createOptionalizeFieldType(c)(sourceFieldName, targetFieldName)
    val newCSType            = addToChangeset(c)(csType, optionalizeFieldType)

    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    val targetPath = SelectorMacros.toPathImpl[B, Option[_]](c)(target.asInstanceOf[c.Expr[B => Option[_]]])

    q"""{
      val sourcePath = $sourcePath
      val targetPath = $targetPath
      locally(targetPath)
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.OptionalizeField(sourcePath)
      )
    }"""
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[A => Any],
    target: c.Expr[B => Any],
    converter: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val sourceFieldName     = extractFieldNameFromSelector(c)(source.tree)
    val targetFieldName     = extractFieldNameFromSelector(c)(target.tree)
    val changeFieldTypeType = createChangeFieldTypeType(c)(sourceFieldName, targetFieldName)
    val newCSType           = addToChangeset(c)(csType, changeFieldTypeType)

    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val sourcePath = $sourcePath
      val targetPath = $targetPath
      locally(targetPath)
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.ChangeFieldType(
          sourcePath,
          $converter.toDynamic
        )
      )
    }"""
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Iterable[_]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val path = SelectorMacros.toPathImpl[A, Iterable[_]](c)(at.asInstanceOf[c.Expr[A => Iterable[_]]])

    q"""{
      val path = $path
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $csType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformElements(
          path,
          $transform.toDynamic
        )
      )
    }"""
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])

    q"""{
      val path = $path
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $csType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformKeys(
          path,
          $transform.toDynamic
        )
      )
    }"""
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])

    q"""{
      val path = $path
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $csType](
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
    CS: c.WeakTypeTag
  ](c: whitebox.Context)(
    selector: c.Expr[A => F1],
    migration: c.Expr[Migration[F1, F2]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val f1Type = weakTypeOf[F1]
    val f2Type = weakTypeOf[F2]
    val csType = weakTypeOf[CS]

    val sourceFieldName = extractFieldNameFromSelector(c)(selector.tree)

    val nestedSourceFields = extractCaseClassFields(c)(f1Type)
    val nestedTargetFields = extractCaseClassFields(c)(f2Type)

    val migrateFieldType = createMigrateFieldType(c)(sourceFieldName)
    var newCSType        = addToChangeset(c)(csType, migrateFieldType)

    for (nestedField <- nestedSourceFields) {
      val dotPath = s"$sourceFieldName.$nestedField"
      newCSType = createRefinedType(c)(newCSType, dotPath)
    }

    for (nestedField <- nestedTargetFields) {
      val dotPath = s"$sourceFieldName.$nestedField"
      newCSType = createRefinedType(c)(newCSType, dotPath)
    }

    val sourcePath = SelectorMacros.toPathImpl[A, F1](c)(selector.asInstanceOf[c.Expr[A => F1]])

    q"""{
      val sourcePath = $sourcePath
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.MigrateField(
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
    CS: c.WeakTypeTag
  ](c: whitebox.Context)(
    selector: c.Expr[A => F1]
  )(
    migration: c.Expr[Migration[F1, F2]]
  ): c.Tree =
    migrateFieldExplicitImpl[A, B, F1, F2, CS](c)(selector, migration)

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

object MigrationValidationMacros {

  def validateMigrationImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](
    c: whitebox.Context
  ): c.Tree = {
    import c.universe._

    val sourceType = weakTypeOf[A]
    val targetType = weakTypeOf[B]
    val csType     = weakTypeOf[CS]

    val sourceFields = extractCaseClassFieldsWithNested(c)(sourceType, "")
    val targetFields = extractCaseClassFieldsWithNested(c)(targetType, "")

    val (handledFields, providedFields) = extractHandledAndProvided(c)(csType)

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

    q"_root_.zio.blocks.schema.migration.MigrationComplete.unsafeCreate[$sourceType, $targetType, $csType]"
  }

  private def extractHandledAndProvided(c: whitebox.Context)(tpe: c.universe.Type): (Set[String], Set[String]) = {
    import c.universe._

    var handled  = Set.empty[String]
    var provided = Set.empty[String]

    def extractString(t: Type): Option[String] = t.dealias match {
      case ConstantType(Constant(s: String)) => Some(s)
      case _                                 => None
    }

    def extract(t: Type): Unit = t.dealias match {
      case RefinedType(parents, _) =>
        parents.foreach(extract)
      case t if t =:= typeOf[Any] => ()
      case TypeRef(_, sym, args)  =>
        sym.name.toString match {
          case "AddField" =>
            args.headOption.flatMap(extractString).foreach(n => provided += n)
          case "DropField" =>
            args.headOption.flatMap(extractString).foreach(n => handled += n)
          case "RenameField" =>
            args.headOption.flatMap(extractString).foreach(n => handled += n)
            args.lift(1).flatMap(extractString).foreach(n => provided += n)
          case "TransformField" =>
            args.headOption.flatMap(extractString).foreach(n => handled += n)
            args.lift(1).flatMap(extractString).foreach(n => provided += n)
          case "MandateField" =>
            args.headOption.flatMap(extractString).foreach(n => handled += n)
            args.lift(1).flatMap(extractString).foreach(n => provided += n)
          case "OptionalizeField" =>
            args.headOption.flatMap(extractString).foreach(n => handled += n)
            args.lift(1).flatMap(extractString).foreach(n => provided += n)
          case "ChangeFieldType" =>
            args.headOption.flatMap(extractString).foreach(n => handled += n)
            args.lift(1).flatMap(extractString).foreach(n => provided += n)
          case "MigrateField" =>
            args.headOption.flatMap(extractString).foreach { n =>
              handled += n
              provided += n
            }
          case "FieldName" =>
            args.headOption.flatMap(extractString).foreach { n =>
              handled += n
              provided += n
            }
          case "RenameCase" | "TransformCase" | "TransformElements" | "TransformKeys" | "TransformValues" =>
            ()
          case _ => ()
        }
      case _ => ()
    }

    extract(tpe)
    (handled, provided)
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
            crossTypeAutoMapLeaves(c)(srcType, tgtType, explicitlyHandled, fullFieldName)
          else Set.empty[String]
        case _ => Set.empty[String]
      }
    }
  }

  private def crossTypeAutoMapLeaves(
    c: whitebox.Context
  )(srcType: c.universe.Type, tgtType: c.universe.Type, explicitlyHandled: Set[String], prefix: String): Set[String] = {
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
        case (s, t)            =>
          val hasExplicitChild = explicitlyHandled.exists(_.startsWith(s"$fullName."))
          if (hasExplicitChild)
            crossTypeAutoMapLeaves(c)(s, t, explicitlyHandled, fullName)
          else Set.empty[String]
      }
    }
  }

  private def inferParentCoverage(allFields: Set[String], covered: Set[String]): Set[String] = {
    val parents = allFields.flatMap { f =>
      val parts = f.split('.')
      if (parts.length > 1) Some(parts.init.mkString(".")) else None
    }
    var result  = covered
    var changed = true
    while (changed) {
      changed = false
      for (parent <- parents) {
        if (!result.contains(parent)) {
          val children = allFields.filter(_.startsWith(s"$parent."))
          if (children.nonEmpty && children.forall(result.contains)) {
            result = result + parent
            changed = true
          }
        }
      }
    }
    result
  }
}
