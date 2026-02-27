package zio.blocks.codegen.emit

import zio.blocks.codegen.ir._

/**
 * Emits Scala source code from the codegen IR.
 *
 * This object provides methods for emitting type references, annotations,
 * fields, imports, package declarations, and compound types (case classes,
 * sealed traits, enums, objects, newtypes) as well as full file emission.
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

  private def ind(level: Int, config: EmitterConfig): String =
    " " * (level * config.indentWidth)

  /**
   * Emits a complete Scala source file as a string.
   *
   * @param file
   *   The ScalaFile IR node to emit
   * @param config
   *   The emitter configuration (defaults to EmitterConfig.default)
   * @return
   *   The complete Scala source code as a string
   */
  def emit(file: ScalaFile, config: EmitterConfig = EmitterConfig.default): String = {
    val sb = new StringBuilder
    sb.append(emitPackageDecl(file.packageDecl))
    sb.append("\n")
    val organized = organizeImports(file.imports, config)
    if (organized.nonEmpty) {
      sb.append("\n")
      organized.foreach { imp =>
        sb.append(emitImport(imp, config))
        sb.append("\n")
      }
    }
    file.types.foreach { typeDef =>
      sb.append("\n")
      sb.append(emitTypeDefinition(typeDef, config, 0))
      sb.append("\n")
    }
    sb.toString
  }

  /**
   * Emits any TypeDefinition by dispatching to the appropriate method.
   *
   * @param typeDef
   *   The type definition to emit
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala code for the type definition
   */
  def emitTypeDefinition(typeDef: TypeDefinition, config: EmitterConfig, indent: Int = 0): String =
    typeDef match {
      case cc: CaseClass   => emitCaseClass(cc, config, indent)
      case st: SealedTrait => emitSealedTrait(st, config, indent)
      case en: Enum        => emitEnum(en, config, indent)
      case od: ObjectDef   => emitObjectDef(od, config, indent)
      case nt: Newtype     => emitNewtype(nt, config, indent)
    }

  /**
   * Emits a case class definition.
   *
   * @param cc
   *   The CaseClass IR node
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala case class code
   */
  def emitCaseClass(cc: CaseClass, config: EmitterConfig, indent: Int = 0): String = {
    val sb     = new StringBuilder
    val prefix = ind(indent, config)
    val inner  = ind(indent + 1, config)

    cc.doc.foreach { d =>
      sb.append(prefix).append("/** ").append(d).append(" */\n")
    }
    cc.annotations.foreach { a =>
      sb.append(prefix).append(emitAnnotation(a)).append("\n")
    }

    sb.append(prefix).append("case class ").append(cc.name)
    if (cc.typeParams.nonEmpty)
      sb.append("[").append(cc.typeParams.map(emitTypeRef).mkString(", ")).append("]")
    sb.append("(")

    if (cc.fields.isEmpty) {
      sb.append(")")
    } else {
      sb.append("\n")
      cc.fields.zipWithIndex.foreach { case (field, idx) =>
        val fieldStr   = emitField(field, config)
        val fieldLines = fieldStr.split("\n")
        fieldLines.init.foreach { line =>
          sb.append(inner).append(line).append("\n")
        }
        sb.append(inner).append(fieldLines.last)
        if (config.trailingCommas || idx < cc.fields.length - 1)
          sb.append(",")
        sb.append("\n")
      }
      sb.append(prefix).append(")")
    }

    if (cc.extendsTypes.nonEmpty)
      sb.append(" extends ").append(cc.extendsTypes.map(emitTypeRef).mkString(" with "))

    if (config.scala3Syntax && cc.derives.nonEmpty)
      sb.append(" derives ").append(cc.derives.mkString(", "))

    cc.companion.foreach { comp =>
      sb.append("\n")
      sb.append(emitCompanionObject(cc.name, comp, config, indent))
    }

    sb.toString
  }

  /**
   * Emits a sealed trait definition.
   *
   * @param st
   *   The SealedTrait IR node
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala sealed trait code
   */
  def emitSealedTrait(st: SealedTrait, config: EmitterConfig, indent: Int = 0): String = {
    val sb     = new StringBuilder
    val prefix = ind(indent, config)

    st.doc.foreach { d =>
      sb.append(prefix).append("/** ").append(d).append(" */\n")
    }
    st.annotations.foreach { a =>
      sb.append(prefix).append(emitAnnotation(a)).append("\n")
    }

    sb.append(prefix).append("sealed trait ").append(st.name)
    if (st.typeParams.nonEmpty)
      sb.append("[").append(st.typeParams.map(emitTypeRef).mkString(", ")).append("]")

    if (st.cases.nonEmpty || st.companion.isDefined) {
      val companionMembers                = st.companion.map(_.members).getOrElse(Nil)
      val caseMembers: List[ObjectMember] = st.cases.map {
        case SealedTraitCase.CaseClassCase(cc) =>
          val extended =
            if (cc.extendsTypes.exists(_.name == st.name)) cc
            else cc.copy(extendsTypes = cc.extendsTypes :+ TypeRef(st.name))
          ObjectMember.NestedType(extended)
        case SealedTraitCase.CaseObjectCase(name) =>
          ObjectMember.NestedType(ObjectDef(name, extendsTypes = List(TypeRef(st.name))))
      }
      val allMembers = caseMembers ++ companionMembers
      sb.append("\n")
      sb.append(emitCompanionObject(st.name, CompanionObject(allMembers), config, indent))
    }

    sb.toString
  }

  /**
   * Emits an enum definition. Uses Scala 3 enum syntax when config.scala3Syntax
   * is true; otherwise emits as sealed trait + companion.
   *
   * @param en
   *   The Enum IR node
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala enum code
   */
  def emitEnum(en: Enum, config: EmitterConfig, indent: Int = 0): String =
    if (config.scala3Syntax) emitEnumScala3(en, config, indent)
    else emitEnumAsSealed(en, config, indent)

  private def emitEnumScala3(en: Enum, config: EmitterConfig, indent: Int): String = {
    val sb     = new StringBuilder
    val prefix = ind(indent, config)
    val inner  = ind(indent + 1, config)

    en.doc.foreach { d =>
      sb.append(prefix).append("/** ").append(d).append(" */\n")
    }
    en.annotations.foreach { a =>
      sb.append(prefix).append(emitAnnotation(a)).append("\n")
    }

    sb.append(prefix).append("enum ").append(en.name)
    if (en.extendsTypes.nonEmpty)
      sb.append(" extends ").append(en.extendsTypes.map(emitTypeRef).mkString(" with "))
    sb.append(" {\n")

    val simples       = en.cases.collect { case EnumCase.SimpleCase(n) => n }
    val parameterized = en.cases.collect { case p: EnumCase.ParameterizedCase => p }
    val hasBoth       = simples.nonEmpty && parameterized.nonEmpty

    if (!hasBoth && parameterized.isEmpty && simples.nonEmpty) {
      sb.append(inner).append("case ").append(simples.mkString(", ")).append("\n")
    } else {
      en.cases.foreach {
        case EnumCase.SimpleCase(n) =>
          sb.append(inner).append("case ").append(n).append("\n")
        case EnumCase.ParameterizedCase(n, fields) =>
          sb.append(inner).append("case ").append(n).append("(")
          sb.append(fields.map(f => emitField(f, config)).mkString(", "))
          sb.append(")\n")
      }
    }

    sb.append(prefix).append("}")
    sb.toString
  }

  private def emitEnumAsSealed(en: Enum, config: EmitterConfig, indent: Int): String = {
    val cases: List[SealedTraitCase] = en.cases.map {
      case EnumCase.SimpleCase(n) =>
        SealedTraitCase.CaseObjectCase(n)
      case EnumCase.ParameterizedCase(n, fields) =>
        SealedTraitCase.CaseClassCase(CaseClass(n, fields))
    }
    val st = SealedTrait(
      name = en.name,
      cases = cases,
      annotations = en.annotations,
      doc = en.doc
    )
    emitSealedTrait(st, config, indent)
  }

  /**
   * Emits an object definition.
   *
   * @param obj
   *   The ObjectDef IR node
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala object code
   */
  def emitObjectDef(obj: ObjectDef, config: EmitterConfig, indent: Int = 0): String = {
    val sb     = new StringBuilder
    val prefix = ind(indent, config)

    obj.doc.foreach { d =>
      sb.append(prefix).append("/** ").append(d).append(" */\n")
    }
    obj.annotations.foreach { a =>
      sb.append(prefix).append(emitAnnotation(a)).append("\n")
    }

    // case object for sealed trait subtypes, otherwise plain object
    sb.append(prefix)
    if (obj.members.isEmpty && obj.extendsTypes.nonEmpty) sb.append("case object ")
    else sb.append("object ")
    sb.append(obj.name)

    if (obj.extendsTypes.nonEmpty)
      sb.append(" extends ").append(obj.extendsTypes.map(emitTypeRef).mkString(" with "))

    if (obj.members.nonEmpty) {
      sb.append(" {\n")
      obj.members.foreach { member =>
        sb.append(emitObjectMember(member, config, indent + 1))
      }
      sb.append(prefix).append("}")
    }

    sb.toString
  }

  /**
   * Emits a newtype definition (object extending Newtype[T] + type alias).
   *
   * @param nt
   *   The Newtype IR node
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala newtype code
   */
  def emitNewtype(nt: Newtype, config: EmitterConfig, indent: Int = 0): String = {
    val sb     = new StringBuilder
    val prefix = ind(indent, config)

    nt.doc.foreach { d =>
      sb.append(prefix).append("/** ").append(d).append(" */\n")
    }
    nt.annotations.foreach { a =>
      sb.append(prefix).append(emitAnnotation(a)).append("\n")
    }

    sb.append(prefix).append("object ").append(nt.name)
    sb.append(" extends Newtype[").append(emitTypeRef(nt.wrappedType)).append("]")
    sb.append("\n")
    sb.append(prefix).append("type ").append(nt.name).append(" = ").append(nt.name).append(".Type")
    sb.toString
  }

  /**
   * Emits a companion object for a type.
   *
   * @param name
   *   The name of the companion object (same as the type it accompanies)
   * @param companion
   *   The CompanionObject IR node
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala companion object code
   */
  def emitCompanionObject(
    name: String,
    companion: CompanionObject,
    config: EmitterConfig,
    indent: Int = 0
  ): String = {
    val sb     = new StringBuilder
    val prefix = ind(indent, config)

    sb.append("\n").append(prefix).append("object ").append(name)
    if (companion.members.isEmpty) {
      sb.toString
    } else {
      sb.append(" {\n")
      companion.members.foreach { member =>
        sb.append(emitObjectMember(member, config, indent + 1))
      }
      sb.append(prefix).append("}")
      sb.toString
    }
  }

  /**
   * Emits a method definition.
   *
   * @param method
   *   The Method IR node
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala method code
   */
  def emitMethod(method: Method, config: EmitterConfig, indent: Int = 0): String = {
    val sb     = new StringBuilder
    val prefix = ind(indent, config)

    method.doc.foreach { d =>
      sb.append(prefix).append("/** ").append(d).append(" */\n")
    }
    method.annotations.foreach { a =>
      sb.append(prefix).append(emitAnnotation(a)).append("\n")
    }

    sb.append(prefix)
    if (method.isOverride) sb.append("override ")
    sb.append("def ").append(method.name)

    if (method.typeParams.nonEmpty)
      sb.append("[").append(method.typeParams.map(emitTypeRef).mkString(", ")).append("]")

    method.params.foreach { paramList =>
      sb.append("(")
      sb.append(paramList.map(emitMethodParam).mkString(", "))
      sb.append(")")
    }

    sb.append(": ").append(emitTypeRef(method.returnType))

    method.body.foreach { b =>
      sb.append(" = ").append(b)
    }

    sb.toString
  }

  /**
   * Emits an object member (val, def, type alias, or nested type).
   *
   * @param member
   *   The ObjectMember IR node
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala member code
   */
  def emitObjectMember(member: ObjectMember, config: EmitterConfig, indent: Int = 0): String = {
    val prefix = ind(indent, config)
    member match {
      case ObjectMember.ValMember(name, typeRef, value) =>
        s"${prefix}val $name: ${emitTypeRef(typeRef)} = $value\n"
      case ObjectMember.DefMember(method) =>
        emitMethod(method, config, indent) + "\n"
      case ObjectMember.TypeAlias(name, typeRef) =>
        s"${prefix}type $name = ${emitTypeRef(typeRef)}\n"
      case ObjectMember.NestedType(typeDef) =>
        emitTypeDefinition(typeDef, config, indent) + "\n"
    }
  }

  private def emitMethodParam(param: MethodParam): String = {
    val defaultStr = param.defaultValue.fold("")(d => s" = $d")
    s"${param.name}: ${emitTypeRef(param.typeRef)}$defaultStr"
  }
}
