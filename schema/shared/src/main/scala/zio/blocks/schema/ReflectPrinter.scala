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
   * An identity-based set that uses reference equality (eq) instead of equals.
   * This is necessary because Reflect types (especially Deferred) have custom
   * equals implementations that consider structurally equal objects as equal,
   * which would cause incorrect cycle detection.
   */
  private final class IdentitySet(private val underlying: Set[Int]) extends AnyVal {
    def contains(obj: AnyRef): Boolean = underlying.contains(System.identityHashCode(obj))

    def +(obj: AnyRef): IdentitySet = new IdentitySet(underlying + System.identityHashCode(obj))

    def size: Int = underlying.size
  }

  private object IdentitySet {
    val empty: IdentitySet = new IdentitySet(Set.empty)
  }

  /**
   * Converts a TypeId to its SDL-friendly format.
   *   - For scala.* types: use just the simple name (e.g., String, Int, List)
   *   - For java.* types: keep the full namespace (e.g., java.time.Instant)
   *   - For custom types: use just the simple name (e.g., Person, Address)
   */
  private[schema] def sdlTypeName[A](typeId: TypeId[A]): String = {
    val packages            = typeId.owner.segments.collect { case Owner.Package(name) => name }
    val shouldKeepNamespace = packages match {
      case "java" :: "lang" :: _ => false
      case "java" :: _           => true
      case "scala" :: _          => false
      case _                     => false
    }
    val baseName =
      if (shouldKeepNamespace) typeId.fullName
      else typeId.name
    if (typeId.typeArgs.isEmpty) baseName
    else typeId.typeArgs.map(sdlTypeRepr).mkString(baseName + "[", ", ", "]")
  }

  private[this] def sdlTypeRepr(repr: TypeRepr): String = repr match {
    case TypeRepr.Ref(tid)           => sdlTypeName(tid.asInstanceOf[TypeId[Any]])
    case TypeRepr.ParamRef(param, _) => param.name
    case other                       => other.toString
  }

  /**
   * Renders a validation as a suffix string (e.g., " @Positive", " @Length(min=3,
   * max=50)"). Returns empty string for Validation.None.
   */
  private[this] def validationSuffix[A](validation: Validation[A]): String = validation match {
    case Validation.None                    => ""
    case Validation.Numeric.Positive        => " @Positive"
    case Validation.Numeric.Negative        => " @Negative"
    case Validation.Numeric.NonPositive     => " @NonPositive"
    case Validation.Numeric.NonNegative     => " @NonNegative"
    case Validation.Numeric.Range(min, max) =>
      val parts = List(min.map(m => s"min=$m"), max.map(m => s"max=$m")).flatten
      if (parts.isEmpty) "" else s" @Range(${parts.mkString(", ")})"
    case Validation.Numeric.Set(values)     => s" @Set(${values.mkString(", ")})"
    case Validation.String.NonEmpty         => " @NonEmpty"
    case Validation.String.Empty            => " @Empty"
    case Validation.String.Blank            => " @Blank"
    case Validation.String.NonBlank         => " @NonBlank"
    case Validation.String.Length(min, max) =>
      val parts = List(min.map(m => s"min=$m"), max.map(m => s"max=$m")).flatten
      if (parts.isEmpty) "" else s" @Length(${parts.mkString(", ")})"
    case Validation.String.Pattern(regex) => s" @Pattern(\"$regex\")"
  }

  /**
   * Prints a Primitive reflect including its validation suffix.
   */
  private[schema] def printPrimitive[F[_, _], A](primitive: Reflect.Primitive[F, A]): String =
    sdlTypeName(primitive.typeId) + validationSuffix(primitive.primitiveType.validation)

  /**
   * Prints a Record reflect in SDL format. Format: record TypeName { field1:
   * Type1, field2: Type2, ... }
   */
  def printRecord[F[_, _], A](record: Reflect.Record[F, A]): String = printRecord(record, IdentitySet.empty)

  private[this] def printRecord[F[_, _], A](record: Reflect.Record[F, A], visited: IdentitySet): String =
    if (record.fields.isEmpty) s"record ${sdlTypeName(record.typeId)} {}"
    else {
      val sb = new java.lang.StringBuilder
      sb.append("record ").append(sdlTypeName(record.typeId)).append(" {\n")
      val newVisited = visited + record
      record.fields.foreach { field =>
        val fieldStr = printTerm(field, indent = 1, newVisited)
        sb.append(fieldStr).append('\n')
      }
      sb.append('}')
      sb.toString
    }

  /**
   * Prints a Variant reflect in SDL format. Format: variant TypeName { | Case1, |
   * Case2(field: Type), ... }
   */
  def printVariant[F[_, _], A](variant: Reflect.Variant[F, A]): String = printVariant(variant, IdentitySet.empty)

  private[this] def printVariant[F[_, _], A](variant: Reflect.Variant[F, A], visited: IdentitySet): String =
    if (variant.cases.isEmpty) s"variant ${sdlTypeName(variant.typeId)} {}"
    else {
      val sb = new java.lang.StringBuilder
      sb.append("variant ").append(sdlTypeName(variant.typeId)).append(" {\n")
      val newVisited = visited + variant
      variant.cases.foreach { case_ =>
        val caseStr = printVariantCase(case_, indent = 1, newVisited)
        sb.append(caseStr).append('\n')
      }
      sb.append('}')
      sb.toString
    }

  /**
   * Prints a Sequence reflect in SDL format. Format: sequence
   * TypeName[ElementType]
   */
  def printSequence[F[_, _], A, C[_]](seq: Reflect.Sequence[F, A, C]): String = printSequence(seq, IdentitySet.empty)

  private[this] def printSequence[F[_, _], A, C[_]](seq: Reflect.Sequence[F, A, C], visited: IdentitySet): String = {
    val newVisited = visited + seq
    val elementStr = printReflect(seq.element, indent = 0, isInline = true, newVisited)
    if (needsMultilineForElement(seq.element)) {
      val sb = new java.lang.StringBuilder
      sb.append("sequence ").append(seq.typeId.name).append("[\n")
      elementStr.linesIterator.foreach {
        var isFirst = true
        line =>
          if (isFirst) isFirst = false
          else sb.append("\n")
          indentString(sb, 1)
          sb.append(line)
      }
      sb.append("\n]")
      sb.toString
    } else s"sequence ${seq.typeId.name}[$elementStr]"
  }

  /**
   * Prints a Map reflect in SDL format. Format: map TypeName[KeyType,
   * ValueType]
   */
  def printMap[F[_, _], K, V, M[_, _]](map: Reflect.Map[F, K, V, M]): String = printMap(map, IdentitySet.empty)

  private[this] def printMap[F[_, _], K, V, M[_, _]](map: Reflect.Map[F, K, V, M], visited: IdentitySet): String = {
    val newVisited = visited + map
    val keyStr     = printReflect(map.key, indent = 0, isInline = true, newVisited)
    val valueStr   = printReflect(map.value, indent = 0, isInline = true, newVisited)
    if (needsMultilineForElement(map.key) || needsMultilineForElement(map.value)) {
      val sb = new java.lang.StringBuilder
      sb.append("map ").append(map.typeId.name).append("[\n")
      if (needsMultilineForElement(map.key)) {
        keyStr.linesIterator.foreach {
          var isFirst = true
          line =>
            if (isFirst) isFirst = false
            else sb.append("\n")
            indentString(sb, 1)
            sb.append(line)
        }
      } else {
        indentString(sb, 1)
        sb.append(keyStr)
      }
      sb.append(",\n")
      if (needsMultilineForElement(map.value)) {
        valueStr.linesIterator.foreach {
          var isFirst = true
          line =>
            if (isFirst) isFirst = false
            else sb.append("\n")
            indentString(sb, 1)
            sb.append(line)
        }
      } else {
        indentString(sb, 1)
        sb.append(valueStr)
      }
      sb.append("\n]").toString
    } else s"map ${map.typeId.name}[$keyStr, $valueStr]"
  }

  /**
   * Prints a Wrapper reflect in SDL format. Format: wrapper
   * TypeName(WrappedType)
   */
  def printWrapper[F[_, _], A, B](wrapper: Reflect.Wrapper[F, A, B]): String = printWrapper(wrapper, IdentitySet.empty)

  private[this] def printWrapper[F[_, _], A, B](wrapper: Reflect.Wrapper[F, A, B], visited: IdentitySet): String = {
    val newVisited = visited + wrapper
    val wrappedStr = printReflect(wrapper.wrapped, indent = 0, isInline = true, newVisited)
    if (needsMultilineForElement(wrapper.wrapped)) {
      val sb = new java.lang.StringBuilder
      sb.append("wrapper ").append(sdlTypeName(wrapper.typeId)).append("(\n")
      wrappedStr.linesIterator.foreach {
        var isFirst = true
        line =>
          if (isFirst) isFirst = false
          else sb.append("\n")
          indentString(sb, 1)
          sb.append(line)
      }
      sb.append("\n)")
      sb.toString
    } else s"wrapper ${sdlTypeName(wrapper.typeId)}($wrappedStr)"
  }

  /**
   * Prints a Term in SDL format.
   */
  def printTerm[F[_, _], S, A](term: Term[F, S, A]): String = printTerm(term, indent = 0, visited = IdentitySet.empty)

  private[this] def printTerm[F[_, _], S, A](term: Term[F, S, A], indent: Int, visited: IdentitySet): String = {
    val typeStr = printReflect(term.value, indent, isInline = false, visited)
    val sb      = new java.lang.StringBuilder
    indentString(sb, indent)
    sb.append(term.name).append(": ")
    if (needsMultiline(term.value)) {
      sb.append(typeStr.linesIterator.toList.mkString("\n"))
    } else sb.append(typeStr)
    sb.toString
  }

  private[this] def printVariantCase[F[_, _], S, A](
    case_ : Term[F, S, A],
    indent: Int,
    visited: IdentitySet
  ): String = {
    val sb = new java.lang.StringBuilder
    indentString(sb, indent)
    sb.append("| ").append(case_.name)
    case_.value.asRecord match {
      case Some(record) if record.fields.isEmpty =>
      // Simple enum case with no payload
      case Some(record) if record.fields.length == 1 && !needsMultiline(record.fields(0).value) =>
        // Single-field case that fits on one line
        val field        = record.fields(0)
        val fieldTypeStr = printReflect(field.value, 0, isInline = true, visited)
        sb.append("(").append(field.name).append(": ").append(fieldTypeStr).append(")")
      case Some(record) =>
        // Multi-field case or complex single field
        sb.append("(\n")
        record.fields.foreach { field =>
          sb.append(printTerm(field, indent + 2, visited))
          if (field != record.fields.last) sb.append(',')
          sb.append('\n')
        }
        indentString(sb, indent + 1)
        sb.append(")")
      case _ =>
        // Non-record case (shouldn't happen in normal schemas, but handle it)
        val typeStr = printReflect(case_.value, indent + 1, isInline = true, visited)
        sb.append("(").append(typeStr).append(")")
    }
    sb.toString
  }

  @tailrec
  private[this] def printReflect[F[_, _], A](
    reflect: Reflect[F, A],
    indent: Int,
    isInline: Boolean,
    visited: IdentitySet
  ): String =
    reflect match {
      case p: Reflect.Primitive[F, A] => printPrimitive(p)
      case d: Reflect.Deferred[F, A]  =>
        if (visited.contains(d)) { s"deferred => ${sdlTypeName(d.typeId)}" }
        else printReflect(d.value, indent, isInline, visited + d)
      case dyn: Reflect.Dynamic[F] @unchecked => sdlTypeName(dyn.typeId)
      case r: Reflect.Record[F, A]            =>
        if (visited.contains(r)) s"deferred => ${sdlTypeName(r.typeId)}"
        else if (isInline && !needsMultiline(r)) {
          printRecord(r, visited).replaceAll("\\n\\s*", " ").replaceAll("\\s+", " ")
        } else {
          val recordStr = printRecord(r, visited)
          if (indent > 0) {
            val sb = new java.lang.StringBuilder
            recordStr.linesIterator.foreach {
              var isFirst = true
              line =>
                if (isFirst) isFirst = false
                else sb.append('\n')
                indentString(sb, indent)
                sb.append(line)
            }
            sb.toString
          } else recordStr
        }
      case v: Reflect.Variant[F, A] =>
        if (visited.contains(v)) s"deferred => ${sdlTypeName(v.typeId)}"
        else if (isInline && !needsMultiline(v)) {
          printVariant(v, visited).replaceAll("\\n\\s*", " ").replaceAll("\\s+", " ")
        } else {
          val variantStr = printVariant(v, visited)
          if (indent > 0) {
            val sb = new java.lang.StringBuilder
            variantStr.linesIterator.foreach {
              var isFirst = true
              line =>
                if (isFirst) isFirst = false
                else sb.append('\n')
                indentString(sb, indent)
                sb.append(line)
            }
            sb.toString
          } else variantStr
        }
      case s: Reflect.Sequence[F, _, _] @unchecked =>
        val seqStr = printSequence(s.asInstanceOf[Reflect.Sequence[F, Any, List]], visited)
        if (indent > 0 && seqStr.contains("\n")) {
          val sb = new java.lang.StringBuilder
          seqStr.linesIterator.foreach {
            var isFirst = true
            line =>
              if (isFirst) isFirst = false
              else sb.append('\n')
              indentString(sb, indent)
              sb.append(line)
          }
          sb.toString
        } else seqStr
      case m: Reflect.Map[F, _, _, _] @unchecked =>
        val mapStr = printMap(m.asInstanceOf[Reflect.Map[F, Any, Any, collection.immutable.Map]], visited)
        if (indent > 0 && mapStr.contains("\n")) {
          val sb = new java.lang.StringBuilder
          mapStr.linesIterator.foreach {
            var isFirst = true
            line =>
              if (isFirst) isFirst = false
              else sb.append('\n')
              indentString(sb, indent)
              sb.append(line)
          }
          sb.toString
        } else mapStr
      case w: Reflect.Wrapper[F, A, _] =>
        printWrapper(w.asInstanceOf[Reflect.Wrapper[F, A, Any]], visited)
    }

  private[this] def needsMultiline[F[_, _], A](reflect: Reflect[F, A]): Boolean =
    needsMultilineWithVisited(reflect, IdentitySet.empty)

  @tailrec
  private[this] def needsMultilineWithVisited[F[_, _], A](reflect: Reflect[F, A], visited: IdentitySet): Boolean =
    reflect match {
      case _: Reflect.Primitive[_, _] => false
      case _: Reflect.Dynamic[_]      => false
      case d: Reflect.Deferred[_, _]  =>
        if (visited.contains(d)) false
        else needsMultilineWithVisited(d.value, visited + d)
      case r: Reflect.Record[_, _]      => r.fields.nonEmpty
      case v: Reflect.Variant[_, _]     => v.cases.nonEmpty
      case _: Reflect.Sequence[_, _, _] => false
      case _: Reflect.Map[_, _, _, _]   => false
      case w: Reflect.Wrapper[_, _, _]  => needsMultilineWithVisited(w.wrapped, visited)
    }

  @tailrec
  private[this] def needsMultilineForElement[F[_, _], A](reflect: Reflect[F, A]): Boolean = reflect match {
    case _: Reflect.Primitive[_, _]   => false
    case _: Reflect.Dynamic[_]        => false
    case _: Reflect.Deferred[_, _]    => false
    case _: Reflect.Record[_, _]      => true
    case _: Reflect.Variant[_, _]     => true
    case _: Reflect.Sequence[_, _, _] => false
    case _: Reflect.Map[_, _, _, _]   => false
    case w: Reflect.Wrapper[_, _, _]  => needsMultilineForElement(w.wrapped)
  }

  private[this] def indentString(sb: java.lang.StringBuilder, indent: Int): Unit = {
    var n = indent
    while (n > 0) {
      sb.append(' ').append(' ')
      n -= 1
    }
  }
}
