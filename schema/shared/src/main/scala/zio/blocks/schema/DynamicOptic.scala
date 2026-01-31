package zio.blocks.schema

case class DynamicOptic(nodes: IndexedSeq[DynamicOptic.Node]) {
  import DynamicOptic.Node

  def apply(that: DynamicOptic): DynamicOptic = new DynamicOptic(nodes ++ that.nodes)

  def apply[F[_, _], A](reflect: Reflect[F, A]): Option[Reflect[F, ?]] = reflect.get(this)

  def field(name: String): DynamicOptic = new DynamicOptic(nodes :+ Node.Field(name))

  def caseOf(name: String): DynamicOptic = new DynamicOptic(nodes :+ Node.Case(name))

  def at(index: Int): DynamicOptic = new DynamicOptic(nodes :+ Node.AtIndex(index))

  def atIndices(indices: Int*): DynamicOptic = new DynamicOptic(nodes :+ Node.AtIndices(indices))

  def atKey[K](key: K)(implicit schema: Schema[K]): DynamicOptic =
    new DynamicOptic(nodes :+ Node.AtMapKey(schema.toDynamicValue(key)))

  def atKeys[K](keys: K*)(implicit schema: Schema[K]): DynamicOptic =
    new DynamicOptic(nodes :+ Node.AtMapKeys(keys.map(schema.toDynamicValue)))

  def elements: DynamicOptic = new DynamicOptic(nodes :+ Node.Elements)

  def mapKeys: DynamicOptic = new DynamicOptic(nodes :+ Node.MapKeys)

  def mapValues: DynamicOptic = new DynamicOptic(nodes :+ Node.MapValues)

  def wrapped: DynamicOptic = new DynamicOptic(nodes :+ Node.Wrapped)

  override lazy val toString: String = {
    val sb  = new StringBuilder
    val len = nodes.length
    var idx = 0
    while (idx < len) {
      nodes(idx) match {
        case Node.Field(name)        => sb.append('.').append(name)
        case Node.Case(name)         => sb.append('<').append(name).append('>')
        case Node.AtIndex(index)     => sb.append('[').append(index).append(']')
        case Node.AtIndices(indices) =>
          sb.append('[')
          val idxLen = indices.length
          var i      = 0
          while (i < idxLen) {
            if (i > 0) sb.append(',')
            sb.append(indices(i))
            i += 1
          }
          sb.append(']')
        case Node.AtMapKey(key) =>
          sb.append('{')
          renderDynamicValue(sb, key)
          sb.append('}')
        case Node.AtMapKeys(keys) =>
          sb.append('{')
          val keyLen = keys.length
          var i      = 0
          while (i < keyLen) {
            if (i > 0) sb.append(", ")
            renderDynamicValue(sb, keys(i))
            i += 1
          }
          sb.append('}')
        case Node.Elements  => sb.append("[*]")
        case Node.MapKeys   => sb.append("{*:}")
        case Node.MapValues => sb.append("{*}")
        case Node.Wrapped   => sb.append(".~")
      }
      idx += 1
    }
    if (sb.isEmpty) "."
    else sb.toString
  }

  /**
   * Renders this optic path using Scala-style method syntax (e.g.,
   * `.when[Case]`, `.each`, `.atKey(<key>)`). This is used for typed Optic
   * error messages, as opposed to `toString` which uses the compact
   * interpolator syntax.
   */
  lazy val toScalaString: String = {
    val sb  = new StringBuilder
    val len = nodes.length
    var idx = 0
    while (idx < len) {
      nodes(idx) match {
        case Node.Field(name)        => sb.append('.').append(name)
        case Node.Case(name)         => sb.append(".when[").append(name).append(']')
        case Node.AtIndex(index)     => sb.append(".at(").append(index).append(')')
        case Node.AtIndices(indices) =>
          sb.append(".atIndices(")
          val idxLen = indices.length
          var i      = 0
          while (i < idxLen) {
            if (i > 0) sb.append(", ")
            sb.append(indices(i))
            i += 1
          }
          sb.append(')')
        case Node.AtMapKey(key) =>
          sb.append(".atKey(")
          renderDynamicValue(sb, key)
          sb.append(')')
        case Node.AtMapKeys(keys) =>
          sb.append(".atKeys(")
          val keyLen = keys.length
          var i      = 0
          while (i < keyLen) {
            if (i > 0) sb.append(", ")
            renderDynamicValue(sb, keys(i))
            i += 1
          }
          sb.append(')')
        case Node.Elements  => sb.append(".each")
        case Node.MapKeys   => sb.append(".eachKey")
        case Node.MapValues => sb.append(".eachValue")
        case Node.Wrapped   => sb.append(".wrapped")
      }
      idx += 1
    }
    if (sb.isEmpty) "."
    else sb.toString
  }

  private def renderDynamicValue(sb: StringBuilder, value: DynamicValue): Unit =
    value match {
      case DynamicValue.Primitive(pv) =>
        pv match {
          case PrimitiveValue.String(s) =>
            sb.append('"')
            var i = 0
            while (i < s.length) {
              s.charAt(i) match {
                case '\n' => sb.append("\\n")
                case '\t' => sb.append("\\t")
                case '\r' => sb.append("\\r")
                case '"'  => sb.append("\\\"")
                case '\\' => sb.append("\\\\")
                case c    => sb.append(c)
              }
              i += 1
            }
            sb.append('"')
          case PrimitiveValue.Boolean(b) => sb.append(b)
          case PrimitiveValue.Char(c)    =>
            sb.append('\'')
            c match {
              case '\n' => sb.append("\\\\n")
              case '\t' => sb.append("\\\\t")
              case '\r' => sb.append("\\\\r")
              case '\'' => sb.append("\\\\'")
              case '\\' => sb.append("\\\\\\\\")
              case char => sb.append(char)
            }
            sb.append('\'')
          case PrimitiveValue.Byte(b)   => sb.append(b)
          case PrimitiveValue.Short(s)  => sb.append(s)
          case PrimitiveValue.Int(i)    => sb.append(i)
          case PrimitiveValue.Long(l)   => sb.append(l)
          case PrimitiveValue.Float(f)  => sb.append(f)
          case PrimitiveValue.Double(d) => sb.append(d)
          case _                        => sb.append(pv.toString)
        }
      case _ => sb.append(value.toString)
    }
}

object DynamicOptic extends DynamicOpticCompanionVersionSpecific {
  val root: DynamicOptic = new DynamicOptic(Vector.empty)

  val elements: DynamicOptic = new DynamicOptic(Vector(Node.Elements))

  val mapKeys: DynamicOptic = new DynamicOptic(Vector(Node.MapKeys))

  val mapValues: DynamicOptic = new DynamicOptic(Vector(Node.MapValues))

  val wrapped: DynamicOptic = new DynamicOptic(Vector(Node.Wrapped))

  sealed trait Node

  object Node {
    case class Field(name: String) extends Node

    case class Case(name: String) extends Node

    case class AtIndex(index: Int) extends Node

    case class AtMapKey(key: DynamicValue) extends Node

    case class AtIndices(index: Seq[Int]) extends Node

    case class AtMapKeys(keys: Seq[DynamicValue]) extends Node

    case object Elements extends Node

    case object MapKeys extends Node

    case object MapValues extends Node

    case object Wrapped extends Node
  }
}
