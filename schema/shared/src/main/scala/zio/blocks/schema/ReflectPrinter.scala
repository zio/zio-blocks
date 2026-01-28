package zio.blocks.schema

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
   * Converts a TypeName to its SDL-friendly format.
   *   - For scala.* types: use just the simple name (e.g., String, Int, List)
   *   - For java.* types: keep the full namespace (e.g., java.time.Instant)
   *   - For custom types: use just the simple name (e.g., Person, Address)
   */
  private[schema] def sdlTypeName[A](typeName: TypeName[A]): String = {
    val shouldKeepNamespace = typeName.namespace.packages.headOption match {
      case Some("java") => true
      case _            => false
    }

    if (shouldKeepNamespace) {
      // Keep full qualification for java.* types
      typeName.toString
    } else {
      // Use simple name for scala.* and custom types
      if (typeName.params.isEmpty) {
        typeName.name
      } else {
        typeName.name + "[" + typeName.params.map(sdlTypeName(_)).mkString(", ") + "]"
      }
    }
  }

  /**
   * Prints a Record reflect in SDL format. Format: record TypeName { field1:
   * Type1, field2: Type2, ... }
   */
  def printRecord[F[_, _], A](record: Reflect.Record[F, A]): String =
    printRecord(record, Set.empty)

  private def printRecord[F[_, _], A](record: Reflect.Record[F, A], visited: Set[AnyRef]): String =
    if (record.fields.isEmpty) {
      s"record ${sdlTypeName(record.typeName)} {}"
    } else {
      val sb = new StringBuilder
      sb.append("record ").append(sdlTypeName(record.typeName)).append(" {\n")
      val newVisited = visited + record.asInstanceOf[AnyRef]
      record.fields.foreachElem { field =>
        val fieldStr = printTerm(field, indent = 2, newVisited)
        sb.append(fieldStr).append('\n')
      }
      sb.append("}")
      sb.toString
    }

  /**
   * Prints a Variant reflect in SDL format. Format: variant TypeName { | Case1, |
   * Case2(field: Type), ... }
   */
  def printVariant[F[_, _], A](variant: Reflect.Variant[F, A]): String =
    printVariant(variant, Set.empty)

  private def printVariant[F[_, _], A](variant: Reflect.Variant[F, A], visited: Set[AnyRef]): String =
    if (variant.cases.isEmpty) {
      s"variant ${sdlTypeName(variant.typeName)} {}"
    } else {
      val sb = new StringBuilder
      sb.append("variant ").append(sdlTypeName(variant.typeName)).append(" {\n")
      val newVisited = visited + variant.asInstanceOf[AnyRef]
      variant.cases.foreachElem { case_ =>
        val caseStr = printVariantCase(case_, indent = 2, newVisited)
        sb.append(caseStr).append('\n')
      }
      sb.append("}")
      sb.toString
    }

  /**
   * Prints a Sequence reflect in SDL format. Format: sequence
   * TypeName[ElementType]
   */
  def printSequence[F[_, _], A, C[_]](seq: Reflect.Sequence[F, A, C]): String =
    printSequence(seq, Set.empty)

  private def printSequence[F[_, _], A, C[_]](seq: Reflect.Sequence[F, A, C], visited: Set[AnyRef]): String = {
    val newVisited = visited + seq.asInstanceOf[AnyRef]
    val elementStr = printReflect(seq.element, indent = 0, isInline = true, newVisited)
    if (needsMultilineForElement(seq.element)) {
      val sb = new StringBuilder
      sb.append("sequence ").append(seq.typeName.name).append("[\n")
      sb.append(elementStr.linesIterator.map(line => indentString(2) + line).mkString("\n"))
      sb.append("\n]")
      sb.toString
    } else {
      s"sequence ${seq.typeName.name}[$elementStr]"
    }
  }

  /**
   * Prints a Map reflect in SDL format. Format: map TypeName[KeyType,
   * ValueType]
   */
  def printMap[F[_, _], K, V, M[_, _]](map: Reflect.Map[F, K, V, M]): String =
    printMap(map, Set.empty)

  private def printMap[F[_, _], K, V, M[_, _]](map: Reflect.Map[F, K, V, M], visited: Set[AnyRef]): String = {
    val newVisited = visited + map.asInstanceOf[AnyRef]
    val keyStr     = printReflect(map.key, indent = 0, isInline = true, newVisited)
    val valueStr   = printReflect(map.value, indent = 0, isInline = true, newVisited)

    if (needsMultilineForElement(map.key) || needsMultilineForElement(map.value)) {
      val sb = new StringBuilder
      sb.append("map ").append(map.typeName.name).append("[\n")
      if (needsMultilineForElement(map.key)) {
        sb.append(keyStr.linesIterator.map(line => indentString(2) + line).mkString("\n"))
      } else {
        sb.append(indentString(2)).append(keyStr)
      }
      sb.append(",\n")
      if (needsMultilineForElement(map.value)) {
        sb.append(valueStr.linesIterator.map(line => indentString(2) + line).mkString("\n"))
      } else {
        sb.append(indentString(2)).append(valueStr)
      }
      sb.append("\n]")
      sb.toString
    } else {
      s"map ${map.typeName.name}[$keyStr, $valueStr]"
    }
  }

  /**
   * Prints a Wrapper reflect in SDL format. Format: wrapper
   * TypeName(WrappedType)
   */
  def printWrapper[F[_, _], A, B](wrapper: Reflect.Wrapper[F, A, B]): String =
    printWrapper(wrapper, Set.empty)

  private def printWrapper[F[_, _], A, B](wrapper: Reflect.Wrapper[F, A, B], visited: Set[AnyRef]): String = {
    val newVisited = visited + wrapper.asInstanceOf[AnyRef]
    val wrappedStr = printReflect(wrapper.wrapped, indent = 0, isInline = true, newVisited)
    if (needsMultilineForElement(wrapper.wrapped)) {
      val sb = new StringBuilder
      sb.append("wrapper ").append(sdlTypeName(wrapper.typeName)).append("(\n")
      sb.append(wrappedStr.linesIterator.map(line => indentString(2) + line).mkString("\n"))
      sb.append("\n)")
      sb.toString
    } else {
      s"wrapper ${sdlTypeName(wrapper.typeName)}($wrappedStr)"
    }
  }

  /**
   * Prints a Term in SDL format.
   */
  def printTerm[F[_, _], S, A](term: Term[F, S, A]): String =
    printTerm(term, indent = 0, visited = Set.empty)

  /**
   * Prints a Term (field or case) in SDL format. Format: name: Type
   */
  private def printTerm[F[_, _], S, A](term: Term[F, S, A], indent: Int, visited: Set[AnyRef]): String = {
    val typeStr = printReflect(term.value, indent, isInline = false, visited)
    if (needsMultiline(term.value)) {
      val indentStr = indentString(indent)
      val lines     = typeStr.linesIterator.toList
      if (lines.length == 1) {
        s"${indentStr}${term.name}: ${lines.head}"
      } else {
        // Multi-line field: indent the type
        val typeIndented = lines.mkString("\n")
        s"${indentStr}${term.name}: $typeIndented"
      }
    } else {
      s"${indentString(indent)}${term.name}: $typeStr"
    }
  }

  /**
   * Prints a variant case in SDL format. Format: | CaseName or |
   * CaseName(fields)
   */
  private def printVariantCase[F[_, _], S, A](case_ : Term[F, S, A], indent: Int, visited: Set[AnyRef]): String =
    case_.value.asRecord match {
      case Some(record) if record.fields.isEmpty =>
        // Simple enum case with no payload
        s"${indentString(indent)}| ${case_.name}"

      case Some(record) if record.fields.length == 1 && !needsMultiline(record.fields(0).value) =>
        // Single-field case that fits on one line
        val field        = record.fields(0)
        val fieldTypeStr = printReflect(field.value, 0, isInline = true, visited)
        s"${indentString(indent)}| ${case_.name}(${field.name}: $fieldTypeStr)"

      case Some(record) =>
        // Multi-field case or complex single field
        val sb = new StringBuilder
        sb.append(indentString(indent)).append("| ").append(case_.name).append("(\n")
        record.fields.foreachElem { field =>
          val fieldStr = printTerm(field, indent + 4, visited)
          // Remove the indent from fieldStr since printTerm adds it
          val trimmed = fieldStr.stripPrefix(indentString(indent + 4))
          sb.append(indentString(indent + 4)).append(trimmed)
          if (field != record.fields.last) sb.append(',')
          sb.append('\n')
        }
        sb.append(indentString(indent + 2)).append(")")
        sb.toString

      case _ =>
        // Non-record case (shouldn't happen in normal schemas, but handle it)
        val typeStr = printReflect(case_.value, indent + 2, isInline = true, visited)
        s"${indentString(indent)}| ${case_.name}($typeStr)"
    }

  /**
   * Generic reflect printer that delegates to specific printers.
   */
  private def printReflect[F[_, _], A](
    reflect: Reflect[F, A],
    indent: Int,
    isInline: Boolean,
    visited: Set[AnyRef]
  ): String =
    reflect match {
      case p: Reflect.Primitive[F, A] =>
        printPrimitive(p)

      case d: Reflect.Deferred[F, A] =>
        // Check visited for the deferred's value, not the deferred itself
        // This allows us to detect recursion through deferred wrappers
        val deferredVisited = d.visited.get
        if (deferredVisited.containsKey(d)) {
          s"deferred => ${sdlTypeName(d.value.typeName)}"
        } else {
          deferredVisited.put(d, ())
          try printReflect(d.value, indent, isInline, visited)
          finally deferredVisited.remove(d)
        }

      case dyn: Reflect.Dynamic[F] @unchecked =>
        sdlTypeName(dyn.typeName)

      case r: Reflect.Record[F, A] =>
        if (visited.contains(r.asInstanceOf[AnyRef])) {
          s"deferred => ${sdlTypeName(r.typeName)}"
        } else if (isInline && !needsMultiline(r)) {
          printRecord(r, visited).replaceAll("\\n\\s*", " ").replaceAll("\\s+", " ")
        } else {
          val recordStr = printRecord(r, visited)
          if (indent > 0) {
            recordStr.linesIterator
              .map(line => if (line.trim.isEmpty) line else indentString(indent) + line)
              .mkString("\n")
          } else {
            recordStr
          }
        }

      case v: Reflect.Variant[F, A] =>
        if (visited.contains(v.asInstanceOf[AnyRef])) {
          s"deferred => ${sdlTypeName(v.typeName)}"
        } else if (isInline && !needsMultiline(v)) {
          printVariant(v, visited).replaceAll("\\n\\s*", " ").replaceAll("\\s+", " ")
        } else {
          val variantStr = printVariant(v, visited)
          if (indent > 0) {
            variantStr.linesIterator
              .map(line => if (line.trim.isEmpty) line else indentString(indent) + line)
              .mkString("\n")
          } else {
            variantStr
          }
        }

      case s: Reflect.Sequence[F, _, _] @unchecked =>
        val seqStr = printSequence(s.asInstanceOf[Reflect.Sequence[F, Any, List]], visited)
        if (indent > 0 && seqStr.contains("\n")) {
          seqStr.linesIterator.map(line => if (line.trim.isEmpty) line else indentString(indent) + line).mkString("\n")
        } else {
          seqStr
        }

      case m: Reflect.Map[F, _, _, _] @unchecked =>
        val mapStr = printMap(m.asInstanceOf[Reflect.Map[F, Any, Any, collection.immutable.Map]], visited)
        if (indent > 0 && mapStr.contains("\n")) {
          mapStr.linesIterator.map(line => if (line.trim.isEmpty) line else indentString(indent) + line).mkString("\n")
        } else {
          mapStr
        }

      case w: Reflect.Wrapper[F, A, _] =>
        printWrapper(w.asInstanceOf[Reflect.Wrapper[F, A, Any]], visited)
    }

  /**
   * Determines if a reflect needs multi-line rendering.
   */
  private def needsMultiline[F[_, _], A](reflect: Reflect[F, A]): Boolean = reflect match {
    case _: Reflect.Primitive[_, _]   => false
    case _: Reflect.Dynamic[_]        => false
    case _: Reflect.Deferred[_, _]    => false
    case r: Reflect.Record[_, _]      => r.fields.nonEmpty
    case v: Reflect.Variant[_, _]     => v.cases.nonEmpty
    case _: Reflect.Sequence[_, _, _] => false
    case _: Reflect.Map[_, _, _, _]   => false
    case w: Reflect.Wrapper[_, _, _]  => needsMultiline(w.wrapped)
  }

  /**
   * Determines if an element type needs multi-line rendering when in a
   * collection.
   */
  private def needsMultilineForElement[F[_, _], A](reflect: Reflect[F, A]): Boolean = reflect match {
    case _: Reflect.Primitive[_, _]   => false
    case _: Reflect.Dynamic[_]        => false
    case _: Reflect.Deferred[_, _]    => false
    case _: Reflect.Record[_, _]      => true
    case _: Reflect.Variant[_, _]     => true
    case _: Reflect.Sequence[_, _, _] => false
    case _: Reflect.Map[_, _, _, _]   => false
    case w: Reflect.Wrapper[_, _, _]  => needsMultilineForElement(w.wrapped)
  }

  private def indentString(spaces: Int): String = " " * spaces

  /**
   * Renders a validation as a suffix string (e.g., " @Positive", " @Length(min=3,
   * max=50)"). Returns empty string for Validation.None.
   */
  private def validationSuffix[A](validation: Validation[A]): String = validation match {
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
    sdlTypeName(primitive.typeName) + validationSuffix(primitive.primitiveType.validation)

  // Extension to make foreach work on IndexedSeq
  private implicit class IndexedSeqOps[A](seq: IndexedSeq[A]) {
    def foreachElem(f: A => Unit): Unit = {
      var i = 0
      while (i < seq.length) {
        f(seq(i))
        i += 1
      }
    }

    def last: A = seq(seq.length - 1)
  }
}
