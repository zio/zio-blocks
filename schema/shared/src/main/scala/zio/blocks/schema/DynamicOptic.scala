package zio.blocks.schema

case class DynamicOptic(nodes: IndexedSeq[DynamicOptic.Node]) {
  import DynamicOptic.Node

  def apply(that: DynamicOptic): DynamicOptic = DynamicOptic(nodes ++ that.nodes)

  def apply[F[_, _], A](reflect: Reflect[F, A]): Option[Reflect[F, A]] =
    reflect.get(this).asInstanceOf[Option[Reflect[F, A]]]

  def field(name: String): DynamicOptic = DynamicOptic(nodes :+ Node.Field(name))

  def caseOf(name: String): DynamicOptic = DynamicOptic(nodes :+ Node.Case(name))

  def elements: DynamicOptic = DynamicOptic(nodes :+ Node.Elements)

  def mapKeys: DynamicOptic = DynamicOptic(nodes :+ Node.MapKeys)

  def mapValues: DynamicOptic = DynamicOptic(nodes :+ Node.MapValues)

  override lazy val toString: String = {
    val sb  = new StringBuilder
    val len = nodes.length
    var idx = 0
    while (idx < len) {
      nodes(idx) match {
        case Node.Field(name) => sb.append('.').append(name)
        case Node.Case(name)  => sb.append(".when[").append(name).append(']')
        case Node.Elements    => sb.append(".each")
        case Node.MapKeys     => sb.append(".eachKey")
        case Node.MapValues   => sb.append(".eachValue")
      }
      idx += 1
    }
    sb.toString
  }
}

object DynamicOptic {
  val root: DynamicOptic = DynamicOptic(Vector.empty)

  val elements: DynamicOptic = DynamicOptic(Vector(Node.Elements))

  val mapKeys: DynamicOptic = DynamicOptic(Vector(Node.MapKeys))

  val mapValues: DynamicOptic = DynamicOptic(Vector(Node.MapValues))

  sealed trait Node

  object Node {
    case class Field(name: String) extends Node

    case class Case(name: String) extends Node

    // case class AtIndex(index: Int)    extends Node // TODO: For At support

    // case class AtMapKey(key: String)  extends Node // TODO: For At support

    case object Elements extends Node

    case object MapKeys extends Node

    case object MapValues extends Node
  }
}
