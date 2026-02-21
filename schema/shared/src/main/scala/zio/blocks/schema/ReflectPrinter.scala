package zio.blocks.schema

import zio.blocks.typeid.{Owner, TypeId, TypeRepr}
import scala.annotation.tailrec

/**
 * Printer for rendering Reflect types in SDL (Schema Definition Language)
 * format.
 *
 * The SDL format is fully recursive and inline - every type is expanded to its
 * leaves. This makes schemas self-contained and easy to understand without
 * external lookups.
 */
private[schema] object ReflectPrinter {

  /**
   * Prints a Primitive reflect including its validation suffix.
   */
  private[schema] def printPrimitive[F[_, _], A](primitive: Reflect.Primitive[F, A]): String =
    sdlTypeName(primitive.typeId) + validationSuffix(primitive.primitiveType.validation)

  /**
   * Prints a Record reflect in SDL format. Format: record TypeName { field1:
   * Type1, field2: Type2, ... }
   */
  def printRecord[F[_, _], A](record: Reflect.Record[F, A]): String =
    printRecord(record, 0, new java.util.IdentityHashMap())

  private[this] def printRecord[F[_, _], A](
    record: Reflect.Record[F, A],
    indent: Int,
    visited: java.util.IdentityHashMap[Any, Any]
  ): String = {
    visited.put(record, record)
    val sb = new java.lang.StringBuilder
    sb.append("record ").append(sdlTypeName(record.typeId)).append(" {")
    record.fields.foreach {
      var isFirst = true
      field =>
        if (isFirst) {
          sb.append('\n')
          isFirst = false
        }
        indentString(sb, indent + 1)
        sb.append(printTerm(field, indent + 1, visited)).append('\n')
    }
    indentString(sb, indent)
    sb.append('}').toString
  }

  /**
   * Prints a Variant reflect in SDL format. Format: variant TypeName { | Case1, |
   * Case2(field: Type), ... }
   */
  def printVariant[F[_, _], A](variant: Reflect.Variant[F, A]): String =
    printVariant(variant, 0, new java.util.IdentityHashMap())

  private[this] def printVariant[F[_, _], A](
    variant: Reflect.Variant[F, A],
    indent: Int,
    visited: java.util.IdentityHashMap[Any, Any]
  ): String = {
    visited.put(variant, variant)
    val sb = new java.lang.StringBuilder
    sb.append("variant ").append(sdlTypeName(variant.typeId)).append(" {\n")
    variant.cases.foreach { case_ =>
      sb.append(printVariantCase(case_, indent + 1, visited)).append('\n')
    }
    indentString(sb, indent)
    sb.append('}').toString
  }

  /**
   * Prints a Sequence reflect in SDL format. Format: sequence
   * TypeName[ElementType]
   */
  def printSequence[F[_, _], A, C[_]](seq: Reflect.Sequence[F, A, C]): String =
    printSequence(seq, 0, new java.util.IdentityHashMap())

  private[this] def printSequence[F[_, _], A, C[_]](
    seq: Reflect.Sequence[F, A, C],
    indent: Int,
    visited: java.util.IdentityHashMap[Any, Any]
  ): String = {
    visited.put(seq, seq)
    val sb = new java.lang.StringBuilder
    sb.append("sequence ").append(seq.typeId.name).append('[')
    val elementStr  = printReflect(seq.element, indent + 1, visited)
    val isMultiline = elementStr.contains('\n')
    if (isMultiline) {
      sb.append('\n')
      indentString(sb, indent + 1)
    }
    sb.append(elementStr)
    if (isMultiline) {
      sb.append('\n')
      indentString(sb, indent)
    }
    sb.append(']').toString
  }

  /**
   * Prints a Map reflect in SDL format. Format: map TypeName[KeyType,
   * ValueType]
   */
  def printMap[F[_, _], K, V, M[_, _]](map: Reflect.Map[F, K, V, M]): String =
    printMap(map, 0, new java.util.IdentityHashMap())

  private[this] def printMap[F[_, _], K, V, M[_, _]](
    map: Reflect.Map[F, K, V, M],
    indent: Int,
    visited: java.util.IdentityHashMap[Any, Any]
  ): String = {
    visited.put(map, map)
    val sb = new java.lang.StringBuilder
    sb.append("map ").append(map.typeId.name).append('[')
    val keyStr      = printReflect(map.key, indent + 1, visited)
    val valueStr    = printReflect(map.value, indent + 1, visited)
    val isMultiline = keyStr.contains('\n') || valueStr.contains('\n')
    if (isMultiline) {
      sb.append('\n')
      indentString(sb, indent + 1)
    }
    sb.append(keyStr).append(',')
    if (isMultiline) {
      sb.append('\n')
      indentString(sb, indent + 1)
    } else sb.append(' ')
    sb.append(valueStr)
    if (isMultiline) {
      sb.append('\n')
      indentString(sb, indent)
    }
    sb.append(']').toString
  }

  /**
   * Prints a Wrapper reflect in SDL format. Format: wrapper
   * TypeName(WrappedType)
   */
  def printWrapper[F[_, _], A, B](wrapper: Reflect.Wrapper[F, A, B]): String =
    printWrapper(wrapper, 0, new java.util.IdentityHashMap())

  private[this] def printWrapper[F[_, _], A, B](
    wrapper: Reflect.Wrapper[F, A, B],
    indent: Int,
    visited: java.util.IdentityHashMap[Any, Any]
  ): String = {
    visited.put(wrapper, wrapper)
    val sb = new java.lang.StringBuilder
    sb.append("wrapper ").append(sdlTypeName(wrapper.typeId)).append('(')
    val wrappedStr  = printReflect(wrapper.wrapped, indent + 1, visited)
    val isMultiline = wrappedStr.contains('\n')
    if (isMultiline) {
      sb.append('\n')
      indentString(sb, indent + 1)
    }
    sb.append(wrappedStr)
    if (isMultiline) {
      sb.append('\n')
      indentString(sb, indent)
    }
    sb.append(')')
    sb.toString
  }

  /**
   * Prints a Term in SDL format.
   */
  private[schema] def printTerm[F[_, _], S, A](term: Term[F, S, A]): String =
    printTerm(term, 0, new java.util.IdentityHashMap())

  private[this] def printTerm[F[_, _], S, A](
    term: Term[F, S, A],
    indent: Int,
    visited: java.util.IdentityHashMap[Any, Any]
  ): String = {
    val sb = new java.lang.StringBuilder
    sb.append(term.name).append(": ").append(printReflect(term.value, indent, visited)).toString
  }

  private[schema] def printDynamic[F[_, _]](dynamic: Reflect.Dynamic[F]): String = sdlTypeName(dynamic.typeId)

  private[this] def printVariantCase[F[_, _], S, A](
    case_ : Term[F, S, A],
    indent: Int,
    visited: java.util.IdentityHashMap[Any, Any]
  ): String = {
    val sb = new java.lang.StringBuilder
    indentString(sb, indent)
    sb.append("| ").append(case_.name)
    case_.value.asRecord match {
      case Some(record) =>
        if (record.fields.length == 1 && !needsMultiline(record.fields(0).value)) {
          sb.append('(').append(printTerm(record.fields(0), indent, visited)).append(')')
        } else if (record.fields.nonEmpty) {
          sb.append('(')
          record.fields.foreach {
            var isFirst = true
            field =>
              if (isFirst) isFirst = false
              else sb.append(',')
              sb.append('\n')
              indentString(sb, indent + 2)
              sb.append(printTerm(field, indent + 2, visited))
          }
          sb.append('\n')
          indentString(sb, indent + 1)
          sb.append(')')
        }
      case _ => // arbitrary cases of union types
        sb.append('(').append(printReflect(case_.value, indent + 1, visited)).append(')')
    }
    sb.toString
  }

  @tailrec
  private[this] def printReflect[F[_, _], A](
    reflect: Reflect[F, A],
    indent: Int,
    visited: java.util.IdentityHashMap[Any, Any]
  ): String =
    if (visited.containsKey(reflect)) s"deferred => ${sdlTypeName(reflect.typeId)}"
    else {
      reflect match {
        case p: Reflect.Primitive[F, A]              => printPrimitive(p)
        case r: Reflect.Record[F, A]                 => printRecord(r, indent, visited)
        case v: Reflect.Variant[F, A]                => printVariant(v, indent, visited)
        case s: Reflect.Sequence[F, _, _] @unchecked =>
          printSequence(s.asInstanceOf[Reflect.Sequence[F, Any, List]], indent, visited)
        case m: Reflect.Map[F, _, _, _] @unchecked =>
          printMap(m.asInstanceOf[Reflect.Map[F, Any, Any, Map]], indent, visited)
        case w: Reflect.Wrapper[F, A, _]      => printWrapper(w.asInstanceOf[Reflect.Wrapper[F, A, Any]], indent, visited)
        case d: Reflect.Dynamic[F] @unchecked => sdlTypeName(d.typeId)
        case d: Reflect.Deferred[F, A]        =>
          visited.put(d, d)
          printReflect(d.value, indent, visited)
      }
    }

  /**
   * Converts a TypeId to its SDL-friendly format.
   *   - For scala.* types: use just the simple name (e.g., String, Int, List)
   *   - For java.* types: keep the full namespace (e.g., java.time.Instant)
   *   - For custom types: use just the simple name (e.g., Person, Address)
   */
  private[this] def sdlTypeName[A](typeId: TypeId[A]): String = {
    val packages = typeId.owner.segments.collect { case Owner.Package(name) => name }
    val baseName = packages match {
      case "java" :: "lang" :: _ => typeId.name
      case "java" :: _           => typeId.fullName
      case _                     => typeId.name
    }
    if (typeId.typeArgs.isEmpty) baseName
    else typeId.typeArgs.map(sdlTypeRepr).mkString(baseName + '[', ", ", "]")
  }

  private[this] def sdlTypeRepr(repr: TypeRepr): String = repr match {
    case r: TypeRepr.Ref => sdlTypeName(r.id.asInstanceOf[TypeId[Any]])
    case _               => repr.toString
  }

  /**
   * Renders a validation as a suffix string (e.g., " @Positive", " @Length(min=3,
   * max=50)"). Returns empty string for Validation.None.
   */
  private[this] def validationSuffix[A](validation: Validation[A]): String = validation match {
    case _: Validation.None.type                   => ""
    case _: Validation.Numeric.Positive.type       => " @Positive"
    case _: Validation.Numeric.Negative.type       => " @Negative"
    case _: Validation.Numeric.NonPositive.type    => " @NonPositive"
    case _: Validation.Numeric.NonNegative.type    => " @NonNegative"
    case r: Validation.Numeric.Range[A] @unchecked =>
      r.min match {
        case Some(min) =>
          r.max match {
            case Some(max) => s" @Range(min=$min, max=$max)"
            case _         => s" @Range(min=$min)"
          }
        case _ =>
          r.max match {
            case Some(max) => s" @Range(max=$max)"
            case _         => ""
          }
      }
    case s: Validation.Numeric.Set[A] @unchecked => s.values.mkString(" @Set(", ", ", ")")
    case _: Validation.String.NonEmpty.type      => " @NonEmpty"
    case _: Validation.String.Empty.type         => " @Empty"
    case _: Validation.String.Blank.type         => " @Blank"
    case _: Validation.String.NonBlank.type      => " @NonBlank"
    case l: Validation.String.Length             =>
      l.min match {
        case Some(min) =>
          l.max match {
            case Some(max) => s" @Length(min=$min, max=$max)"
            case _         => s" @Length(min=$min)"
          }
        case _ =>
          l.max match {
            case Some(max) => s" @Length(max=$max)"
            case _         => ""
          }
      }
    case p: Validation.String.Pattern => s" @Pattern(\"${p.regex}\")"
  }

  private[this] def needsMultiline[F[_, _], A](reflect: Reflect[F, A]): Boolean =
    needsMultilineWithVisited(reflect, new java.util.IdentityHashMap())

  @tailrec
  private[this] def needsMultilineWithVisited[F[_, _], A](
    reflect: Reflect[F, A],
    visited: java.util.IdentityHashMap[Any, Any]
  ): Boolean = reflect match {
    case _: Reflect.Primitive[_, _]   => false
    case r: Reflect.Record[_, _]      => r.fields.nonEmpty
    case v: Reflect.Variant[_, _]     => v.cases.nonEmpty
    case _: Reflect.Sequence[_, _, _] => false
    case _: Reflect.Map[_, _, _, _]   => false
    case w: Reflect.Wrapper[_, _, _]  => needsMultilineWithVisited(w.wrapped, visited)
    case _: Reflect.Dynamic[_]        => false
    case d: Reflect.Deferred[_, _]    =>
      if (visited.containsKey(d)) false
      else {
        visited.put(d, d)
        needsMultilineWithVisited(d.value, visited)
      }
  }

  private[this] def indentString(sb: java.lang.StringBuilder, indent: Int): Unit = {
    var n = indent
    while (n > 0) {
      sb.append(' ').append(' ')
      n -= 1
    }
  }
}
