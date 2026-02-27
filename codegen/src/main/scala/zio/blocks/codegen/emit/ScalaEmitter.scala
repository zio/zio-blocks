package zio.blocks.codegen.emit

import zio.blocks.codegen.ir._

/**
 * Emits Scala source code from the codegen IR.
 *
 * This object provides methods for emitting type references, annotations,
 * fields, imports, and package declarations. Compound type emission (case
 * classes, sealed traits, etc.) is handled separately.
 */
object ScalaEmitter {

  /**
   * Emits a Scala type reference as a string.
   *
   * @param typeRef
   *   The type reference to emit
   * @return
   *   The Scala type string (e.g., "String", "List[Int]", "Map[String, Int]")
   */
  def emitTypeRef(typeRef: TypeRef): String =
    if (typeRef.typeArgs.isEmpty) typeRef.name
    else s"${typeRef.name}[${typeRef.typeArgs.map(emitTypeRef).mkString(", ")}]"

  /**
   * Emits a Scala annotation as a string.
   *
   * @param annotation
   *   The annotation to emit
   * @return
   *   The Scala annotation string (e.g., "@required", "@deprecated(message =
   *   \"use v2\")")
   */
  def emitAnnotation(annotation: Annotation): String =
    if (annotation.args.isEmpty) s"@${annotation.name}"
    else {
      val argsStr = annotation.args.map { case (k, v) => s"$k = $v" }.mkString(", ")
      s"@${annotation.name}($argsStr)"
    }

  /**
   * Emits a case class field as a string, including annotations and default
   * values.
   *
   * @param field
   *   The field to emit
   * @param config
   *   The emitter configuration
   * @return
   *   The field string, with each annotation on its own line above the field
   *   declaration
   */
  def emitField(field: Field, @scala.annotation.unused config: EmitterConfig): String = {
    val typeStr    = emitTypeRef(field.typeRef)
    val defaultStr = field.defaultValue.fold("")(d => s" = $d")
    val fieldStr   = s"${field.name}: $typeStr$defaultStr"
    if (field.annotations.isEmpty) fieldStr
    else {
      val annotStrs = field.annotations.map(emitAnnotation)
      (annotStrs :+ fieldStr).mkString("\n")
    }
  }

  /**
   * Emits a Scala import statement using Scala 3 syntax by default.
   *
   * @param imp
   *   The import to emit
   * @return
   *   The Scala import string
   */
  def emitImport(imp: Import): String = emitImport(imp, EmitterConfig.default)

  /**
   * Emits a Scala import statement respecting the given configuration.
   *
   * @param imp
   *   The import to emit
   * @param config
   *   The emitter configuration (controls Scala 2 vs 3 syntax)
   * @return
   *   The Scala import string
   */
  def emitImport(imp: Import, config: EmitterConfig): String = imp match {
    case Import.SingleImport(path, name) => s"import $path.$name"
    case Import.WildcardImport(path)     =>
      val wildcard = if (config.scala3Syntax) "*" else "_"
      s"import $path.$wildcard"
    case Import.RenameImport(path, from, to) =>
      val arrow = if (config.scala3Syntax) "as" else "=>"
      s"import $path.{$from $arrow $to}"
  }

  /**
   * Emits a Scala package declaration.
   *
   * @param pkg
   *   The package declaration to emit
   * @return
   *   The Scala package declaration string (e.g., "package com.example")
   */
  def emitPackageDecl(pkg: PackageDecl): String = s"package ${pkg.path}"

  /**
   * Organizes imports by deduplicating and optionally sorting them.
   *
   * @param imports
   *   The list of imports to organize
   * @param config
   *   The emitter configuration (controls whether sorting is applied)
   * @return
   *   The organized list of imports
   */
  def organizeImports(imports: List[Import], config: EmitterConfig): List[Import] = {
    val deduped = imports.distinct
    if (config.sortImports) deduped.sortBy(importSortKey)
    else deduped
  }

  private def importSortKey(imp: Import): String = imp match {
    case Import.SingleImport(path, name)     => s"$path.$name"
    case Import.WildcardImport(path)         => s"$path.*"
    case Import.RenameImport(path, from, to) => s"$path.$from"
  }
}
