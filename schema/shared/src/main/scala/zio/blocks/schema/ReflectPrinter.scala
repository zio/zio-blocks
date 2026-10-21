/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import zio.blocks.typeid.{Owner, TypeId, TypeRepr}
import scala.annotation.tailrec

/**
 * Prints [[Reflect]] trees in a schema definition language; the `toString` of
 * every node uses it.
 *
 * Every node is printed inline, down to its leaves. A node that was already
 * printed earlier in the same call is printed as `deferred =>` and its type
 * name instead, which stops a recursive type. The printer keeps every printed
 * node in that set until the call ends, so a node that is shared but not
 * recursive, such as the schema of a case class used for two fields, also
 * prints as `deferred =>` the second time.
 */
private[schema] object ReflectPrinter {

  /**
   * Prints a primitive node as its type name, followed by its validation, if
   * any, as in `Int @Positive`.
   */
  private[schema] def printPrimitive[F[_, _], A](primitive: Reflect.Primitive[F, A]): String =
    sdlTypeName(primitive.typeId) + validationSuffix(primitive.primitiveType.validation)

  /**
   * Prints a record node as `record Name {`, then one `name: Type` line per
   * field, then `}`.
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
   * Prints a variant node as `variant Name {`, then one `| Case` line per case
   * with the case's fields, if any, in parentheses, then `}`.
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
   * Prints a sequence node as `sequence Name[Element]`, with the element on its
   * own lines when it takes more than one.
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
   * Prints a map node as `map Name[Key, Value]`, with the key and value on
   * their own lines when either takes more than one.
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
   * Prints a wrapper node as `wrapper Name(Wrapped)`, with the wrapped node on
   * its own lines when it takes more than one.
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

  /** Prints a term as `name: Type`. */
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

  /** Prints a dynamic node as its type name. */
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
   * Returns the printed name of a type: the full name for a type in a `java`
   * package other than `java.lang` (such as `java.time.Instant`), the simple
   * name for any other type, followed by the type arguments, if any.
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
