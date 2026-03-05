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
  def emitTypeRef(typeRef: TypeRef): String = typeRef.name match {
    case "|" => typeRef.typeArgs.map(emitTypeRef).mkString(" | ")
    case "&" => typeRef.typeArgs.map(emitTypeRef).mkString(" & ")
    case _   =>
      val safeName =
        if (typeRef.name.contains('.') || typeRef.name.exists(c => "()[]=>".contains(c)))
          typeRef.name
        else escapeName(typeRef.name)
      if (typeRef.typeArgs.isEmpty) safeName
      else s"${safeName}[${typeRef.typeArgs.map(emitTypeRef).mkString(", ")}]"
  }

  /**
   * Emits a type parameter with optional variance and bounds.
   *
   * @param tp
   *   The TypeParam to emit
   * @return
   *   The Scala type parameter string (e.g., "+A", "A <: Serializable")
   */
  def emitTypeParam(tp: TypeParam): String = {
    val sb = new StringBuilder
    tp.variance match {
      case Variance.Covariant     => sb.append("+")
      case Variance.Contravariant => sb.append("-")
      case Variance.Invariant     => ()
    }
    sb.append(escapeName(tp.name))
    tp.lowerBound.foreach(lb => sb.append(" >: ").append(emitTypeRef(lb)))
    tp.upperBound.foreach(ub => sb.append(" <: ").append(emitTypeRef(ub)))
    sb.toString
  }

  private def typeParamsToTypeRefs(tps: List[TypeParam]): List[TypeRef] =
    tps.map(tp => TypeRef(tp.name))

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
    val fieldStr   = s"${escapeName(field.name)}: $typeStr$defaultStr"
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
    case Import.GroupImport(path, names) =>
      s"import $path.{${names.mkString(", ")}}"
  }

  /**
   * Emits a Scala package declaration.
   *
   * @param pkg
   *   The package declaration to emit
   * @return
   *   The Scala package declaration string (e.g., "package com.example")
   */
  def emitPackageDecl(pkg: PackageDecl): String =
    if (pkg.path.isEmpty) "" else s"package ${pkg.path}"

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
    case Import.GroupImport(path, names)     => s"$path.${names.head}"
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
    val sb     = new StringBuilder
    val pkgStr = emitPackageDecl(file.packageDecl)
    if (pkgStr.nonEmpty) {
      sb.append(pkgStr).append("\n")
    }
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
      case cc: CaseClass     => emitCaseClass(cc, config, indent)
      case st: SealedTrait   => emitSealedTrait(st, config, indent)
      case en: Enum          => emitEnum(en, config, indent)
      case od: ObjectDef     => emitObjectDef(od, config, indent)
      case nt: Newtype       => emitNewtype(nt, config, indent)
      case t: Trait          => emitTrait(t, config, indent)
      case ac: AbstractClass => emitAbstractClass(ac, config, indent)
      case ot: OpaqueType    => emitOpaqueType(ot, config, indent)
      case ta: TypeAlias     => emitTypeAlias(ta, config, indent)
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

    sb.append(prefix).append("case class ").append(escapeName(cc.name))
    if (cc.typeParams.nonEmpty)
      sb.append("[").append(cc.typeParams.map(emitTypeParam).mkString(", ")).append("]")
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

    sb.append(prefix).append("sealed trait ").append(escapeName(st.name))
    if (st.typeParams.nonEmpty)
      sb.append("[").append(st.typeParams.map(emitTypeParam).mkString(", ")).append("]")
    if (st.extendsTypes.nonEmpty)
      sb.append(" extends ").append(st.extendsTypes.map(emitTypeRef).mkString(" with "))
    st.selfType.foreach { selfTp =>
      sb.append(" { self: ").append(emitTypeRef(selfTp)).append(" => }")
    }
    if (st.cases.nonEmpty || st.companion.isDefined) {
      val companionMembers                = st.companion.map(_.members).getOrElse(Nil)
      val caseMembers: List[ObjectMember] = st.cases.map {
        case SealedTraitCase.CaseClassCase(cc) =>
          val extended =
            if (cc.extendsTypes.exists(_.name == st.name)) cc
            else cc.copy(extendsTypes = cc.extendsTypes :+ TypeRef(st.name, typeParamsToTypeRefs(st.typeParams)))
          ObjectMember.NestedType(extended)
        case SealedTraitCase.CaseObjectCase(name) =>
          ObjectMember.NestedType(
            ObjectDef(
              name,
              extendsTypes = List(TypeRef(st.name, typeParamsToTypeRefs(st.typeParams))),
              isCaseObject = true
            )
          )
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

    sb.append(prefix).append("enum ").append(escapeName(en.name))
    if (en.extendsTypes.nonEmpty)
      sb.append(" extends ").append(en.extendsTypes.map(emitTypeRef).mkString(" with "))
    sb.append(" {\n")

    val simples       = en.cases.collect { case EnumCase.SimpleCase(n) => n }
    val parameterized = en.cases.collect { case p: EnumCase.ParameterizedCase => p }

    if (parameterized.isEmpty && simples.nonEmpty) {
      sb.append(inner).append("case ").append(simples.mkString(", ")).append("\n")
    } else {
      en.cases.foreach {
        case EnumCase.SimpleCase(n) =>
          sb.append(inner).append("case ").append(n).append("\n")
        case EnumCase.ParameterizedCase(n, fields, _) =>
          sb.append(inner).append("case ").append(n).append("(")
          sb.append(fields.map(f => emitFieldInline(f, config)).mkString(", "))
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
      case EnumCase.ParameterizedCase(n, fields, _) =>
        SealedTraitCase.CaseClassCase(CaseClass(n, fields))
    }
    val st = SealedTrait(
      name = en.name,
      cases = cases,
      extendsTypes = en.extendsTypes,
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
    if (obj.isCaseObject) sb.append("case object ")
    else sb.append("object ")
    sb.append(escapeName(obj.name))

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

    sb.append(prefix).append("object ").append(escapeName(nt.name))
    sb.append(" extends Newtype[").append(emitTypeRef(nt.wrappedType)).append("]")
    sb.append("\n")
    sb.append(prefix)
      .append("type ")
      .append(escapeName(nt.name))
      .append(" = ")
      .append(escapeName(nt.name))
      .append(".Type")
    sb.toString
  }

  /**
   * Emits a non-sealed trait definition.
   *
   * @param t
   *   The Trait IR node
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala trait code
   */
  def emitTrait(t: Trait, config: EmitterConfig, indent: Int = 0): String = {
    val sb     = new StringBuilder
    val prefix = ind(indent, config)

    t.doc.foreach { d =>
      sb.append(prefix).append("/** ").append(d).append(" */\n")
    }
    t.annotations.foreach { a =>
      sb.append(prefix).append(emitAnnotation(a)).append("\n")
    }

    sb.append(prefix).append("trait ").append(escapeName(t.name))
    if (t.typeParams.nonEmpty)
      sb.append("[").append(t.typeParams.map(emitTypeParam).mkString(", ")).append("]")
    if (t.extendsTypes.nonEmpty)
      sb.append(" extends ").append(t.extendsTypes.map(emitTypeRef).mkString(" with "))

    val hasSelfType = t.selfType.isDefined
    if (t.members.nonEmpty || hasSelfType) {
      sb.append(" {\n")
      t.selfType.foreach { st =>
        sb.append(ind(indent + 1, config)).append("self: ").append(emitTypeRef(st)).append(" =>\n")
      }
      t.members.foreach { member =>
        sb.append(emitObjectMember(member, config, indent + 1))
      }
      sb.append(prefix).append("}")
    }

    t.companion.foreach { comp =>
      sb.append("\n")
      sb.append(emitCompanionObject(t.name, comp, config, indent))
    }

    sb.toString
  }

  /**
   * Emits an abstract class definition.
   *
   * @param ac
   *   The AbstractClass IR node
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala abstract class code
   */
  def emitAbstractClass(ac: AbstractClass, config: EmitterConfig, indent: Int = 0): String = {
    val sb     = new StringBuilder
    val prefix = ind(indent, config)
    val inner  = ind(indent + 1, config)

    ac.doc.foreach { d =>
      sb.append(prefix).append("/** ").append(d).append(" */\n")
    }
    ac.annotations.foreach { a =>
      sb.append(prefix).append(emitAnnotation(a)).append("\n")
    }

    sb.append(prefix).append("abstract class ").append(escapeName(ac.name))
    if (ac.typeParams.nonEmpty)
      sb.append("[").append(ac.typeParams.map(emitTypeParam).mkString(", ")).append("]")

    if (ac.fields.nonEmpty) {
      sb.append("(")
      sb.append("\n")
      ac.fields.zipWithIndex.foreach { case (field, idx) =>
        val fieldStr   = emitField(field, config)
        val fieldLines = fieldStr.split("\n")
        fieldLines.init.foreach { line =>
          sb.append(inner).append(line).append("\n")
        }
        sb.append(inner).append(fieldLines.last)
        if (config.trailingCommas || idx < ac.fields.length - 1)
          sb.append(",")
        sb.append("\n")
      }
      sb.append(prefix).append(")")
    }

    if (ac.extendsTypes.nonEmpty)
      sb.append(" extends ").append(ac.extendsTypes.map(emitTypeRef).mkString(" with "))

    if (ac.members.nonEmpty) {
      sb.append(" {\n")
      ac.members.foreach { member =>
        sb.append(emitObjectMember(member, config, indent + 1))
      }
      sb.append(prefix).append("}")
    }

    ac.companion.foreach { comp =>
      sb.append("\n")
      sb.append(emitCompanionObject(ac.name, comp, config, indent))
    }

    sb.toString
  }

  /**
   * Emits an opaque type definition.
   *
   * In Scala 3 mode, emits `opaque type Name = UnderlyingType`. In Scala 2
   * mode, falls back to `type Name = UnderlyingType`.
   *
   * @param ot
   *   The OpaqueType IR node
   * @param config
   *   The emitter configuration
   * @param indent
   *   The current indentation level (defaults to 0)
   * @return
   *   The emitted Scala opaque type code
   */
  def emitOpaqueType(ot: OpaqueType, config: EmitterConfig, indent: Int = 0): String = {
    val sb     = new StringBuilder
    val prefix = ind(indent, config)

    ot.doc.foreach { d =>
      sb.append(prefix).append("/** ").append(d).append(" */\n")
    }
    ot.annotations.foreach { a =>
      sb.append(prefix).append(emitAnnotation(a)).append("\n")
    }

    sb.append(prefix)
    if (config.scala3Syntax) sb.append("opaque ") else ()
    sb.append("type ").append(escapeName(ot.name))
    ot.upperBound.foreach { ub =>
      sb.append(" <: ").append(emitTypeRef(ub))
    }
    sb.append(" = ").append(emitTypeRef(ot.underlyingType))

    ot.companion.foreach { comp =>
      sb.append("\n")
      sb.append(emitCompanionObject(ot.name, comp, config, indent))
    }

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

    sb.append("\n").append(prefix).append("object ").append(escapeName(name))
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
  def emitTypeAlias(ta: TypeAlias, config: EmitterConfig, indent: Int = 0): String = {
    val sb     = new StringBuilder
    val prefix = ind(indent, config)
    ta.doc.foreach { d =>
      sb.append(prefix).append("/** ").append(d).append(" */\n")
    }
    ta.annotations.foreach { a =>
      sb.append(prefix).append(emitAnnotation(a)).append("\n")
    }
    sb.append(prefix).append("type ").append(escapeName(ta.name))
    if (ta.typeParams.nonEmpty)
      sb.append("[").append(ta.typeParams.map(emitTypeParam).mkString(", ")).append("]")
    sb.append(" = ").append(emitTypeRef(ta.typeRef))
    sb.toString
  }

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
    if (method.isImplicit) {
      if (config.scala3Syntax) sb.append("given ")
      else sb.append("implicit ")
    }
    sb.append("def ").append(escapeName(method.name))

    if (method.typeParams.nonEmpty)
      sb.append("[").append(method.typeParams.map(emitTypeParam).mkString(", ")).append("]")

    method.params.foreach { paramList =>
      sb.append("(")
      paramList.modifier match {
        case ParamListModifier.Implicit =>
          sb.append(if (config.scala3Syntax) "using " else "implicit ")
        case ParamListModifier.Using =>
          sb.append(if (config.scala3Syntax) "using " else "implicit ")
        case ParamListModifier.Normal => ()
      }
      sb.append(paramList.params.map(emitMethodParam).mkString(", "))
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
      case vm: ObjectMember.ValMember =>
        val sb = new StringBuilder(prefix)
        if (vm.isOverride) sb.append("override ")
        if (vm.isImplicit) {
          if (config.scala3Syntax) sb.append("given ")
          else sb.append("implicit ")
        }
        if (vm.isLazy) sb.append("lazy ")
        sb.append(s"val ${escapeName(vm.name)}: ${emitTypeRef(vm.typeRef)} = ${vm.value}\n")
        sb.toString
      case ObjectMember.DefMember(method) =>
        emitMethod(method, config, indent) + "\n"
      case ObjectMember.TypeAlias(name, typeRef) =>
        s"${prefix}type ${escapeName(name)} = ${emitTypeRef(typeRef)}\n"
      case ObjectMember.NestedType(typeDef) =>
        emitTypeDefinition(typeDef, config, indent) + "\n"
      case ObjectMember.ExtensionBlock(on, methods) =>
        val sb    = new StringBuilder()
        val inner = ind(indent + 1, config)
        if (config.scala3Syntax) {
          if (methods.size == 1) {
            sb.append(s"${prefix}extension (${emitMethodParam(on)})\n")
            sb.append(inner).append(emitMethod(methods.head, config).trim).append("\n")
          } else {
            sb.append(s"${prefix}extension (${emitMethodParam(on)}) {\n")
            methods.foreach { m =>
              sb.append(inner).append(emitMethod(m, config).trim).append("\n")
            }
            sb.append(s"${prefix}}\n")
          }
        } else {
          val typeName  = emitTypeRef(on.typeRef)
          val className = s"${typeName}Ops"
          sb.append(s"${prefix}implicit class $className(val ${emitMethodParam(on)}) extends AnyVal {\n")
          methods.foreach { m =>
            sb.append(inner).append(emitMethod(m, config).trim).append("\n")
          }
          sb.append(s"${prefix}}\n")
        }
        sb.toString
    }
  }

  private def emitMethodParam(param: MethodParam): String = {
    val defaultStr   = param.defaultValue.fold("")(d => s" = $d")
    val typeStr      = emitTypeRef(param.typeRef)
    val typeWithMods =
      if (param.isByName) s"=> $typeStr"
      else if (param.isVarargs) s"$typeStr*"
      else typeStr
    s"${escapeName(param.name)}: $typeWithMods$defaultStr"
  }

  private def emitFieldInline(field: Field, @scala.annotation.unused config: EmitterConfig): String = {
    val typeStr    = emitTypeRef(field.typeRef)
    val defaultStr = field.defaultValue.fold("")(d => s" = $d")
    val annotStr   =
      if (field.annotations.isEmpty) ""
      else field.annotations.map(emitAnnotation).mkString(" ") + " "
    s"$annotStr${escapeName(field.name)}: $typeStr$defaultStr"
  }

  private val scalaKeywords: Set[String] = Set(
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "do",
    "else",
    "enum",
    "export",
    "extends",
    "false",
    "final",
    "finally",
    "for",
    "forSome",
    "given",
    "if",
    "implicit",
    "import",
    "lazy",
    "match",
    "new",
    "null",
    "object",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "then",
    "this",
    "throw",
    "trait",
    "true",
    "try",
    "type",
    "val",
    "var",
    "while",
    "with",
    "yield"
  )

  private def escapeName(name: String): String =
    if (scalaKeywords.contains(name) || !name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$"))
      s"`$name`"
    else name
}
