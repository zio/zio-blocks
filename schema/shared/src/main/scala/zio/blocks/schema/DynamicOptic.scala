package zio.blocks.schema

case class DynamicOptic(nodes: IndexedSeq[DynamicOptic.Node]) {
  import DynamicOptic.Node

  def apply(that: DynamicOptic): DynamicOptic = new DynamicOptic(nodes ++ that.nodes)

  def apply[F[_, _], A](reflect: Reflect[F, A]): Option[Reflect[F, ?]] = reflect.get(this)

  def field(name: String): DynamicOptic = new DynamicOptic(nodes :+ Node.Field(name))

  def caseOf(name: String): DynamicOptic = new DynamicOptic(nodes :+ Node.Case(name))

  def at(index: Int): DynamicOptic = new DynamicOptic(nodes :+ Node.AtIndex(index))

  def atIndices(indices: Int*): DynamicOptic = new DynamicOptic(nodes :+ Node.AtIndices(indices))

  def atKey[K](key: K): DynamicOptic = new DynamicOptic(nodes :+ Node.AtMapKey(key))

  def atKeys[K](keys: K*): DynamicOptic = new DynamicOptic(nodes :+ Node.AtMapKeys(keys))

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
        case Node.Field(name)    => sb.append('.').append(name)
        case Node.Case(name)     => sb.append(".when[").append(name).append(']')
        case Node.AtIndex(index) => sb.append(".at(").append(index).append(')')
        case Node.AtMapKey(_)    => sb.append(".atKey(<key>)")
        case Node.AtIndices(_)   => sb.append(".atIndices(<indices>)")
        case Node.AtMapKeys(_)   => sb.append(".atKeys(<keys>)")
        case Node.Elements       => sb.append(".each")
        case Node.MapKeys        => sb.append(".eachKey")
        case Node.MapValues      => sb.append(".eachValue")
        case Node.Wrapped        => sb.append(".wrapped")
      }
      idx += 1
    }
    if (sb.isEmpty) "."
    else sb.toString
  }
}

object DynamicOptic {
  val root: DynamicOptic = new DynamicOptic(Vector.empty)

  val elements: DynamicOptic = new DynamicOptic(Vector(Node.Elements))

  val mapKeys: DynamicOptic = new DynamicOptic(Vector(Node.MapKeys))

  val mapValues: DynamicOptic = new DynamicOptic(Vector(Node.MapValues))

  val wrapped: DynamicOptic = new DynamicOptic(Vector(Node.Wrapped))

  val empty: DynamicOptic = new DynamicOptic(Vector.empty)

  sealed trait Node

  object Node {
    case class Field(name: String) extends Node

    case class Case(name: String) extends Node

    case class AtIndex(index: Int) extends Node

    case class AtMapKey[K](key: K) extends Node

    case class AtIndices(index: Seq[Int]) extends Node

    case class AtMapKeys[K](keys: Seq[K]) extends Node

    case object Elements extends Node

    case object MapKeys extends Node

    case object MapValues extends Node

    case object Wrapped extends Node
  }
}
