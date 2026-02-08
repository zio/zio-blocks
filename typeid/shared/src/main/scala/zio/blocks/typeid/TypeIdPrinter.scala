package zio.blocks.typeid

/**
 * Provides idiomatic Scala type rendering for TypeId, TypeRepr, and TypeParam.
 *
 * This object converts type representations into readable Scala syntax strings,
 * making them suitable for display in error messages, documentation, and
 * debugging output.
 */
object TypeIdPrinter {

  /**
   * Render a TypeId to idiomatic Scala syntax.
   */
  def render(typeId: TypeId[_]): String = {
    val baseName = if (shouldUseFullName(typeId.owner)) typeId.fullName else typeId.name
    if (typeId.typeArgs.nonEmpty)
      s"$baseName[${typeId.typeArgs.map(render).mkString(", ")}]"
    else if (typeId.typeParams.nonEmpty)
      s"$baseName[${typeId.typeParams.map(render).mkString(", ")}]"
    else
      baseName
  }

  /**
   * Render a TypeRepr to idiomatic Scala syntax.
   */
  def render(repr: TypeRepr): String = repr match {
    case TypeRepr.Ref(typeId) =>
      render(typeId)

    case TypeRepr.ParamRef(param, _) =>
      param.name

    case TypeRepr.Applied(tycon, args) =>
      s"${renderAsConstructor(tycon)}[${args.map(render).mkString(", ")}]"

    case TypeRepr.Intersection(types) =>
      types.map(render).mkString(" & ")

    case TypeRepr.Union(types) =>
      types.map(render).mkString(" | ")

    case TypeRepr.Tuple(elems) =>
      if (elems.forall(_.label.isEmpty))
        s"(${elems.map(e => render(e.tpe)).mkString(", ")})"
      else
        s"(${elems.map(e => e.label.fold(render(e.tpe))(l => s"$l: ${render(e.tpe)}")).mkString(", ")})"

    case TypeRepr.Function(Nil, result) =>
      s"() => ${render(result)}"

    case TypeRepr.Function(List(single), result) =>
      s"${render(single)} => ${render(result)}"

    case TypeRepr.Function(params, result) =>
      s"(${params.map(render).mkString(", ")}) => ${render(result)}"

    case TypeRepr.ContextFunction(params, result) =>
      if (params.size == 1)
        s"${render(params.head)} ?=> ${render(result)}"
      else
        s"(${params.map(render).mkString(", ")}) ?=> ${render(result)}"

    case TypeRepr.TypeLambda(params, body) =>
      s"[${params.map(render).mkString(", ")}] =>> ${render(body)}"

    case TypeRepr.ByName(underlying) =>
      s"=> ${render(underlying)}"

    case TypeRepr.Repeated(element) =>
      s"${render(element)}*"

    case TypeRepr.Wildcard(bounds) =>
      (bounds.lower, bounds.upper) match {
        case (None, None)       => "?"
        case (None, Some(u))    => s"? <: ${render(u)}"
        case (Some(l), None)    => s"? >: ${render(l)}"
        case (Some(l), Some(u)) => s"? >: ${render(l)} <: ${render(u)}"
      }

    case TypeRepr.Singleton(path) =>
      s"${path.asString}.type"

    case TypeRepr.ThisType(_) =>
      "this.type"

    case TypeRepr.TypeProjection(qualifier, name) =>
      s"${render(qualifier)}#$name"

    case TypeRepr.TypeSelect(qualifier, name) =>
      s"${render(qualifier)}.$name"

    case TypeRepr.Annotated(underlying, annotations) =>
      s"${render(underlying)} ${annotations.map(a => s"@${a.name}").mkString(" ")}"

    case TypeRepr.Constant.IntConst(v) =>
      v.toString

    case TypeRepr.Constant.LongConst(v) =>
      s"${v}L"

    case TypeRepr.Constant.FloatConst(v) =>
      s"${v}f"

    case TypeRepr.Constant.DoubleConst(v) =>
      v.toString

    case TypeRepr.Constant.BooleanConst(v) =>
      v.toString

    case TypeRepr.Constant.CharConst(v) =>
      val escaped = v match {
        case '\n' => "\\n"
        case '\t' => "\\t"
        case '\r' => "\\r"
        case '\\' => "\\\\"
        case '\'' => "\\'"
        case c    => c.toString
      }
      s"'$escaped'"

    case TypeRepr.Constant.StringConst(v) =>
      val escaped = v
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
        .replace("\r", "\\r")
      s"\"$escaped\""

    case TypeRepr.Constant.NullConst =>
      "null"

    case TypeRepr.Constant.UnitConst =>
      "()"

    case TypeRepr.Constant.ClassOfConst(tpe) =>
      s"classOf[${render(tpe)}]"

    case TypeRepr.AnyType =>
      "Any"

    case TypeRepr.NothingType =>
      "Nothing"

    case TypeRepr.NullType =>
      "Null"

    case TypeRepr.UnitType =>
      "Unit"

    case TypeRepr.AnyKindType =>
      "AnyKind"

    case TypeRepr.Structural(parents, members) =>
      renderStructural(parents, members)
  }

  /**
   * Render a TypeParam to idiomatic Scala syntax.
   */
  def render(param: TypeParam): String = {
    val varianceStr = param.variance.symbol
    val kindSuffix  =
      if (param.kind.isProperType) ""
      else "[" + List.fill(param.kind.arity)("_").mkString(", ") + "]"
    s"$varianceStr${param.name}$kindSuffix"
  }

  // ========== Private Helpers ==========

  /**
   * Renders a type constructor without its formal type parameters. Used inside
   * Applied types where the actual args replace formal params.
   */
  private def renderAsConstructor(repr: TypeRepr): String = repr match {
    case TypeRepr.Ref(typeId) =>
      if (shouldUseFullName(typeId.owner)) typeId.fullName else typeId.name
    case other => render(other)
  }

  /**
   * Determines whether to use the full name or simple name for a type based on
   * its owner.
   */
  private def shouldUseFullName(owner: Owner): Boolean = {
    val packages = owner.segments.collect { case Owner.Package(name) => name }
    packages match {
      case "java" :: "lang" :: _ => false // java.lang.String → "String"
      case "java" :: _           => true  // java.util.UUID → "java.util.UUID"
      case "scala" :: _          => false // scala.Int → "Int"
      case _                     => false // com.example.Foo → "Foo"
    }
  }

  /**
   * Renders a structural/refinement type.
   */
  private def renderStructural(parents: List[TypeRepr], members: List[Member]): String = {
    val parentStr = if (parents.isEmpty) "" else parents.map(render).mkString(" with ") + " "
    val memberStr = members.map(renderMember).mkString("; ")
    if (memberStr.isEmpty) parentStr.trim
    else s"$parentStr{ $memberStr }"
  }

  /**
   * Renders a structural type member.
   */
  private def renderMember(member: Member): String = member match {
    case Member.Val(name, tpe, isVar) =>
      val keyword = if (isVar) "var" else "val"
      s"$keyword $name: ${render(tpe)}"

    case Member.Def(name, typeParams, paramLists, result) =>
      val tpStr = if (typeParams.isEmpty) "" else s"[${typeParams.map(render).mkString(", ")}]"
      val psStr = paramLists.map(ps => s"(${ps.map(p => s"${p.name}: ${render(p.tpe)}").mkString(", ")})").mkString
      s"def $name$tpStr$psStr: ${render(result)}"

    case Member.TypeMember(name, typeParams, lower, upper) =>
      val tpStr     = if (typeParams.isEmpty) "" else s"[${typeParams.map(render).mkString(", ")}]"
      val boundsStr = (lower, upper) match {
        case (Some(l), Some(u)) if l == u => s" = ${render(l)}"
        case (Some(l), Some(u))           => s" >: ${render(l)} <: ${render(u)}"
        case (Some(l), None)              => s" >: ${render(l)}"
        case (None, Some(u))              => s" <: ${render(u)}"
        case _                            => ""
      }
      s"type $name$tpStr$boundsStr"
  }
}
