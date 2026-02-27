package zio.blocks.smithy

/**
 * Serializes a `SmithyModel` to valid Smithy IDL text.
 *
 * The printer produces output in canonical Smithy IDL order: version statement,
 * namespace, use statements, metadata, and shape definitions, each separated by
 * blank lines.
 *
 * @example
 *   {{{
 * val model = SmithyModel("2.0", "com.example", Nil, Map.empty, List(
 *   ShapeDefinition("MyString", StringShape("MyString"))
 * ))
 * val idl = SmithyPrinter.print(model)
 * // "$version: \"2.0\"\n\nnamespace com.example\n\nstring MyString\n"
 *   }}}
 */
object SmithyPrinter {

  private val DocumentationTraitId = ShapeId("smithy.api", "documentation")

  /**
   * Prints a SmithyModel as Smithy IDL text with default 4-space indentation.
   *
   * @param model
   *   the SmithyModel to serialize
   * @return
   *   a String containing valid Smithy IDL text
   */
  def print(model: SmithyModel): String = print(model, 4)

  /**
   * Prints a SmithyModel as Smithy IDL text with configurable indentation.
   *
   * @param model
   *   the SmithyModel to serialize
   * @param indent
   *   the number of spaces to use for indentation within shapes
   * @return
   *   a String containing valid Smithy IDL text
   */
  def print(model: SmithyModel, indent: Int): String = {
    val sb  = new StringBuilder
    val pad = " " * indent

    sb.append("$version: \"").append(model.version).append("\"\n")
    sb.append('\n')
    sb.append("namespace ").append(model.namespace).append('\n')

    if (model.useStatements.nonEmpty) {
      sb.append('\n')
      model.useStatements.foreach { id =>
        sb.append("use ").append(id.toString).append('\n')
      }
    }

    if (model.metadata.nonEmpty) {
      sb.append('\n')
      model.metadata.foreach { case (key, value) =>
        sb.append("metadata ").append(key).append(" = ")
        appendNodeValue(sb, value)
        sb.append('\n')
      }
    }

    if (model.shapes.nonEmpty) {
      sb.append('\n')
      val shapeStrings = model.shapes.map(sd => printShapeDefinition(sd, pad, model.namespace))
      sb.append(shapeStrings.mkString("\n\n"))
      sb.append('\n')
    }

    sb.toString
  }

  private def printShapeDefinition(sd: ShapeDefinition, pad: String, ns: String): String = {
    val sb = new StringBuilder
    appendShapeTraits(sb, sd.shape.traits, "")
    appendShape(sb, sd, pad, ns)
    sb.toString
  }

  private def appendShapeTraits(
    sb: StringBuilder,
    traits: List[TraitApplication],
    prefix: String
  ): Unit =
    traits.foreach { t =>
      if (isDocumentation(t))
        appendDocComment(sb, t, prefix)
      else {
        sb.append(prefix)
        appendTraitApplication(sb, t)
        sb.append('\n')
      }
    }

  private def isDocumentation(t: TraitApplication): Boolean =
    t.id == DocumentationTraitId

  private def appendDocComment(
    sb: StringBuilder,
    t: TraitApplication,
    prefix: String
  ): Unit =
    t.value match {
      case Some(NodeValue.StringValue(text)) =>
        text.split("\n", -1).foreach { line =>
          sb.append(prefix).append("/// ").append(line).append('\n')
        }
      case _ =>
        sb.append(prefix)
        appendTraitApplication(sb, t)
        sb.append('\n')
    }

  private def appendTraitApplication(sb: StringBuilder, t: TraitApplication): Unit = {
    sb.append('@')
    appendTraitId(sb, t.id)
    t.value.foreach { v =>
      sb.append('(')
      v match {
        case NodeValue.ObjectValue(fields) =>
          appendObjectFields(sb, fields)
        case other =>
          appendNodeValue(sb, other)
      }
      sb.append(')')
    }
  }

  private def appendTraitId(sb: StringBuilder, id: ShapeId): Unit =
    if (id.namespace == "smithy.api") sb.append(id.name)
    else sb.append(id.toString)

  private def appendShape(sb: StringBuilder, sd: ShapeDefinition, pad: String, ns: String): Unit =
    sd.shape match {
      case _: BlobShape       => sb.append("blob ").append(sd.name)
      case _: BooleanShape    => sb.append("boolean ").append(sd.name)
      case _: StringShape     => sb.append("string ").append(sd.name)
      case _: ByteShape       => sb.append("byte ").append(sd.name)
      case _: ShortShape      => sb.append("short ").append(sd.name)
      case _: IntegerShape    => sb.append("integer ").append(sd.name)
      case _: LongShape       => sb.append("long ").append(sd.name)
      case _: FloatShape      => sb.append("float ").append(sd.name)
      case _: DoubleShape     => sb.append("double ").append(sd.name)
      case _: BigIntegerShape => sb.append("bigInteger ").append(sd.name)
      case _: BigDecimalShape => sb.append("bigDecimal ").append(sd.name)
      case _: TimestampShape  => sb.append("timestamp ").append(sd.name)
      case _: DocumentShape   => sb.append("document ").append(sd.name)

      case s: EnumShape    => appendEnumShape(sb, sd.name, s, pad)
      case s: IntEnumShape => appendIntEnumShape(sb, sd.name, s, pad)

      case s: ListShape      => appendListShape(sb, sd.name, s, pad, ns)
      case s: MapShape       => appendMapShape(sb, sd.name, s, pad, ns)
      case s: StructureShape => appendStructureShape(sb, sd.name, s, pad, ns)
      case s: UnionShape     => appendUnionShape(sb, sd.name, s, pad, ns)

      case s: ServiceShape   => appendServiceShape(sb, sd.name, s, pad, ns)
      case s: OperationShape => appendOperationShape(sb, sd.name, s, pad, ns)
      case s: ResourceShape  => appendResourceShape(sb, sd.name, s, pad, ns)
    }

  private def appendStructureShape(
    sb: StringBuilder,
    name: String,
    s: StructureShape,
    pad: String,
    ns: String
  ): Unit = {
    sb.append("structure ").append(name)
    if (s.members.isEmpty) {
      sb.append(" {}")
    } else {
      sb.append(" {\n")
      s.members.foreach { m =>
        appendShapeTraits(sb, m.traits, pad)
        sb.append(pad).append(m.name).append(": ").append(renderShapeRef(m.target, ns)).append('\n')
      }
      sb.append('}')
    }
  }

  private def appendUnionShape(
    sb: StringBuilder,
    name: String,
    s: UnionShape,
    pad: String,
    ns: String
  ): Unit = {
    sb.append("union ").append(name)
    if (s.members.isEmpty) {
      sb.append(" {}")
    } else {
      sb.append(" {\n")
      s.members.foreach { m =>
        appendShapeTraits(sb, m.traits, pad)
        sb.append(pad).append(m.name).append(": ").append(renderShapeRef(m.target, ns)).append('\n')
      }
      sb.append('}')
    }
  }

  private def appendListShape(
    sb: StringBuilder,
    name: String,
    s: ListShape,
    pad: String,
    ns: String
  ): Unit = {
    sb.append("list ").append(name).append(" {\n")
    appendShapeTraits(sb, s.member.traits, pad)
    sb.append(pad).append(s.member.name).append(": ").append(renderShapeRef(s.member.target, ns)).append('\n')
    sb.append('}')
  }

  private def appendMapShape(
    sb: StringBuilder,
    name: String,
    s: MapShape,
    pad: String,
    ns: String
  ): Unit = {
    sb.append("map ").append(name).append(" {\n")
    appendShapeTraits(sb, s.key.traits, pad)
    sb.append(pad).append(s.key.name).append(": ").append(renderShapeRef(s.key.target, ns)).append('\n')
    appendShapeTraits(sb, s.value.traits, pad)
    sb.append(pad).append(s.value.name).append(": ").append(renderShapeRef(s.value.target, ns)).append('\n')
    sb.append('}')
  }

  private def appendEnumShape(
    sb: StringBuilder,
    name: String,
    s: EnumShape,
    pad: String
  ): Unit = {
    sb.append("enum ").append(name)
    if (s.members.isEmpty) {
      sb.append(" {}")
    } else {
      sb.append(" {\n")
      s.members.foreach { m =>
        appendShapeTraits(sb, m.traits, pad)
        sb.append(pad).append(m.name)
        m.value.foreach(v => sb.append(" = \"").append(escapeString(v)).append('"'))
        sb.append('\n')
      }
      sb.append('}')
    }
  }

  private def appendIntEnumShape(
    sb: StringBuilder,
    name: String,
    s: IntEnumShape,
    pad: String
  ): Unit = {
    sb.append("intEnum ").append(name)
    if (s.members.isEmpty) {
      sb.append(" {}")
    } else {
      sb.append(" {\n")
      s.members.foreach { m =>
        appendShapeTraits(sb, m.traits, pad)
        sb.append(pad).append(m.name).append(" = ").append(m.value).append('\n')
      }
      sb.append('}')
    }
  }

  private def appendServiceShape(
    sb: StringBuilder,
    name: String,
    s: ServiceShape,
    pad: String,
    ns: String
  ): Unit = {
    sb.append("service ").append(name).append(" {\n")
    s.version.foreach { v =>
      sb.append(pad).append("version: \"").append(escapeString(v)).append("\"\n")
    }
    if (s.operations.nonEmpty)
      appendShapeIdList(sb, "operations", s.operations, pad, ns)
    if (s.resources.nonEmpty)
      appendShapeIdList(sb, "resources", s.resources, pad, ns)
    if (s.errors.nonEmpty)
      appendShapeIdList(sb, "errors", s.errors, pad, ns)
    sb.append('}')
  }

  private def appendOperationShape(
    sb: StringBuilder,
    name: String,
    s: OperationShape,
    pad: String,
    ns: String
  ): Unit = {
    sb.append("operation ").append(name).append(" {\n")
    s.input.foreach { id =>
      sb.append(pad).append("input: ").append(renderShapeRef(id, ns)).append('\n')
    }
    s.output.foreach { id =>
      sb.append(pad).append("output: ").append(renderShapeRef(id, ns)).append('\n')
    }
    if (s.errors.nonEmpty)
      appendShapeIdList(sb, "errors", s.errors, pad, ns)
    sb.append('}')
  }

  private def appendResourceShape(
    sb: StringBuilder,
    name: String,
    s: ResourceShape,
    pad: String,
    ns: String
  ): Unit = {
    sb.append("resource ").append(name).append(" {\n")
    if (s.identifiers.nonEmpty) {
      sb.append(pad).append("identifiers: {")
      val entries = s.identifiers.toList
      entries.zipWithIndex.foreach { case ((k, v), i) =>
        sb.append(k).append(": ").append(renderShapeRef(v, ns))
        if (i < entries.length - 1) sb.append(", ")
      }
      sb.append("}\n")
    }
    s.create.foreach(id => sb.append(pad).append("create: ").append(renderShapeRef(id, ns)).append('\n'))
    s.read.foreach(id => sb.append(pad).append("read: ").append(renderShapeRef(id, ns)).append('\n'))
    s.update.foreach(id => sb.append(pad).append("update: ").append(renderShapeRef(id, ns)).append('\n'))
    s.delete.foreach(id => sb.append(pad).append("delete: ").append(renderShapeRef(id, ns)).append('\n'))
    s.list.foreach(id => sb.append(pad).append("list: ").append(renderShapeRef(id, ns)).append('\n'))
    if (s.operations.nonEmpty)
      appendShapeIdList(sb, "operations", s.operations, pad, ns)
    if (s.collectionOperations.nonEmpty)
      appendShapeIdList(sb, "collectionOperations", s.collectionOperations, pad, ns)
    if (s.resources.nonEmpty)
      appendShapeIdList(sb, "resources", s.resources, pad, ns)
    sb.append('}')
  }

  private def appendShapeIdList(
    sb: StringBuilder,
    label: String,
    ids: List[ShapeId],
    pad: String,
    ns: String
  ): Unit = {
    sb.append(pad).append(label).append(": [")
    ids.zipWithIndex.foreach { case (id, i) =>
      sb.append(renderShapeRef(id, ns))
      if (i < ids.length - 1) sb.append(", ")
    }
    sb.append("]\n")
  }

  private def renderShapeRef(id: ShapeId, modelNamespace: String): String =
    if (id.namespace.isEmpty || id.namespace == "smithy.api" || id.namespace == modelNamespace) id.name
    else id.toString

  private def appendNodeValue(sb: StringBuilder, v: NodeValue): Unit =
    v match {
      case NodeValue.StringValue(s) =>
        sb.append('"').append(escapeString(s)).append('"')
      case NodeValue.NumberValue(n) =>
        sb.append(n.toString)
      case NodeValue.BooleanValue(b) =>
        sb.append(if (b) "true" else "false")
      case NodeValue.NullValue =>
        sb.append("null")
      case NodeValue.ArrayValue(vs) =>
        sb.append('[')
        vs.zipWithIndex.foreach { case (item, i) =>
          appendNodeValue(sb, item)
          if (i < vs.length - 1) sb.append(", ")
        }
        sb.append(']')
      case NodeValue.ObjectValue(fs) =>
        sb.append('{')
        appendObjectFields(sb, fs)
        sb.append('}')
    }

  private def appendObjectFields(sb: StringBuilder, fields: List[(String, NodeValue)]): Unit =
    fields.zipWithIndex.foreach { case ((key, value), i) =>
      sb.append(key).append(": ")
      appendNodeValue(sb, value)
      if (i < fields.length - 1) sb.append(", ")
    }

  private def escapeString(s: String): String = {
    val sb = new StringBuilder(s.length)
    var i  = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c    => sb.append(c)
      }
      i += 1
    }
    sb.toString
  }
}
