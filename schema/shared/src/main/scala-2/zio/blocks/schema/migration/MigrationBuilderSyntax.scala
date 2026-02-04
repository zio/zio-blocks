package zio.blocks.schema.migration

import scala.reflect.macros.whitebox
import zio.blocks.schema.SchemaExpr

/**
 * Macro implementations for MigrationBuilder methods.
 */
object MigrationBuilderMacros {

  /**
   * Macro implementation for .build that validates all fields are handled.
   *
   * This macro:
   *   1. Extracts source and target field names from case class types A and B
   *   2. Walks the builder expression tree to find all builder method calls
   *   3. Extracts field names from selectors in those calls
   *   4. Computes auto-mapped fields (same name + compatible type)
   *   5. Reports compile errors if any fields are missing/unhandled
   */
  def buildImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
    import c.universe._

    val sourceType = weakTypeOf[A]
    val targetType = weakTypeOf[B]

    // Extract field names from case class types
    val sourceFields = extractCaseClassFields(c)(sourceType)
    val targetFields = extractCaseClassFields(c)(targetType)

    // Extract field types for auto-mapping check
    val sourceFieldTypes = extractCaseClassFieldTypes(c)(sourceType)
    val targetFieldTypes = extractCaseClassFieldTypes(c)(targetType)

    // Walk the builder chain to find handled/provided fields
    val (handledSource, providedTarget) = extractFieldsFromBuilderChain(c)(c.prefix.tree)

    // Compute auto-mapped fields (same name + compatible type)
    val autoMapped = computeAutoMapped(c)(sourceFields, targetFields, sourceFieldTypes, targetFieldTypes)

    // Check completeness
    val coveredSource = handledSource ++ autoMapped
    val coveredTarget = providedTarget ++ autoMapped

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

      c.error(c.enclosingPosition, errors.toString)
    }

    // Generate the Migration
    q"""new _root_.zio.blocks.schema.migration.Migration(
      ${c.prefix}.sourceSchema,
      ${c.prefix}.targetSchema,
      new _root_.zio.blocks.schema.migration.DynamicMigration(${c.prefix}.actions)
    )"""
  }

  /**
   * Extract field names from a case class type.
   */
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

  /**
   * Extract field names and types from a case class type.
   */
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

  /**
   * Compute auto-mapped fields: fields with same name and compatible types in
   * both source and target.
   */
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

  private def extractFieldsFromBuilderChain(c: whitebox.Context)(
    tree: c.universe.Tree
  ): (Set[String], Set[String]) = {
    import c.universe._

    var handledSource  = Set.empty[String]
    var providedTarget = Set.empty[String]
    var pathBindings   = Map.empty[String, String]

    def extractFieldNameFromPath(t: Tree): Option[String] = t match {
      case Apply(Select(_, TermName("field")), List(Literal(Constant(fieldName: String)))) =>
        Some(fieldName)
      case _ => None
    }

    def traverse(t: Tree): Unit = t match {
      case ValDef(_, TermName(name), _, rhs)
          if name == "targetPath" || name == "sourcePath" ||
            name == "fromPath" || name == "toPath" =>
        extractFieldNameFromPath(rhs).foreach { fieldName =>
          pathBindings += (name -> fieldName)
        }

      case Apply(Select(_, TermName("apply")), _)
          if t.tpe != null && t.tpe.typeSymbol.fullName.contains("MigrationAction") =>
        val actionType = t.symbol.owner.name.toString
        actionType match {
          case "AddField" =>
            pathBindings.get("targetPath").foreach(providedTarget += _)
          case "DropField" =>
            pathBindings.get("sourcePath").foreach(handledSource += _)
          case "Rename" =>
            pathBindings.get("fromPath").foreach(handledSource += _)
            pathBindings.get("toPath").orElse(pathBindings.get("toName")).foreach(providedTarget += _)
          case "TransformValue" =>
            pathBindings.get("fromPath").foreach(handledSource += _)
            pathBindings.get("toPath").foreach(providedTarget += _)
          case "Mandate" =>
            pathBindings.get("sourcePath").foreach(handledSource += _)
            pathBindings.get("targetPath").foreach(providedTarget += _)
          case "Optionalize" =>
            pathBindings.get("sourcePath").foreach(handledSource += _)
            pathBindings.get("targetPath").foreach(providedTarget += _)
          case "ChangeType" =>
            pathBindings.get("sourcePath").foreach(handledSource += _)
            pathBindings.get("targetPath").foreach(providedTarget += _)
          case _ => ()
        }

      case Block(stats, expr) =>
        pathBindings = Map.empty
        stats.foreach(traverse)
        traverse(expr)

      case _ =>
        t.children.foreach(traverse)
    }

    traverse(tree)
    (handledSource, providedTarget)
  }

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[A])
    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])
    q"""{
      val targetPath = $targetPath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.AddField(
        targetPath,
        $default
      ))
    }"""
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    q"""{
      val sourcePath = $sourcePath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.DropField(
        sourcePath,
        $defaultForReverse
      ))
    }"""
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Tree = {
    import c.universe._
    val fromPath = SelectorMacros.toPathImpl[A, Any](c)(from.asInstanceOf[c.Expr[A => Any]])
    val toPath   = SelectorMacros.toPathImpl[B, Any](c)(to.asInstanceOf[c.Expr[B => Any]])
    q"""{
      val fromPath = $fromPath
      val toPath = $toPath
      val toName = toPath.nodes.lastOption match {
        case _root_.scala.Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Target selector must end with a field access")
      }
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.Rename(fromPath, toName))
    }"""
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val fromPath = SelectorMacros.toPathImpl[A, Any](c)(from.asInstanceOf[c.Expr[A => Any]])
    val toPath   = SelectorMacros.toPathImpl[B, Any](c)(to.asInstanceOf[c.Expr[B => Any]])
    q"""{
      val fromPath = $fromPath
      val toPath = $toPath
      locally(toPath)
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformValue(
        fromPath,
        $transform
      ))
    }"""
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Option[_]],
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val sourcePath = SelectorMacros.toPathImpl[A, Option[_]](c)(source.asInstanceOf[c.Expr[A => Option[_]]])
    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])
    q"""{
      val sourcePath = $sourcePath
      val targetPath = $targetPath
      locally(targetPath)
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.Mandate(
        sourcePath,
        $default
      ))
    }"""
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    target: c.Expr[B => Option[_]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    val targetPath = SelectorMacros.toPathImpl[B, Option[_]](c)(target.asInstanceOf[c.Expr[B => Option[_]]])
    q"""{
      val sourcePath = $sourcePath
      val targetPath = $targetPath
      locally(targetPath)
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.Optionalize(sourcePath))
    }"""
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    target: c.Expr[B => Any],
    converter: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])
    q"""{
      val sourcePath = $sourcePath
      val targetPath = $targetPath
      locally(targetPath)
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.ChangeType(
        sourcePath,
        $converter
      ))
    }"""
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Iterable[_]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val path = SelectorMacros.toPathImpl[A, Iterable[_]](c)(at.asInstanceOf[c.Expr[A => Iterable[_]]])
    q"""{
      val path = $path
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformElements(
        path,
        $transform
      ))
    }"""
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])
    q"""{
      val path = $path
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformKeys(
        path,
        $transform
      ))
    }"""
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])
    q"""{
      val path = $path
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformValues(
        path,
        $transform
      ))
    }"""
  }
}
