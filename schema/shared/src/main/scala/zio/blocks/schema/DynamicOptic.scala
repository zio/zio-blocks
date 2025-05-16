package zio.blocks.schema

final case class DynamicOptic(nodes: IndexedSeq[DynamicOptic.Node]) {
  import DynamicOptic.Node

  def apply(that: DynamicOptic): DynamicOptic = new DynamicOptic(nodes ++ that.nodes)

  def apply[F[_, _], A](reflect: Reflect[F, A]): Option[Reflect[F, A]] =
    reflect.get(this).asInstanceOf[Option[Reflect[F, A]]]

  def field(name: String): DynamicOptic  = new DynamicOptic(nodes :+ Node.Field(name))
  def caseOf(name: String): DynamicOptic = new DynamicOptic(nodes :+ Node.Case(name))
  def elements: DynamicOptic             = new DynamicOptic(nodes :+ Node.Elements)
  def mapKeys: DynamicOptic              = new DynamicOptic(nodes :+ Node.MapKeys)
  def mapValues: DynamicOptic            = new DynamicOptic(nodes :+ Node.MapValues)

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
  val root: DynamicOptic      = new DynamicOptic(Vector.empty)
  val elements: DynamicOptic  = new DynamicOptic(Vector(Node.Elements))
  val mapKeys: DynamicOptic   = new DynamicOptic(Vector(Node.MapKeys))
  val mapValues: DynamicOptic = new DynamicOptic(Vector(Node.MapValues))

  sealed trait Node
  object Node {
    final case class Field(name: String) extends Node
    final case class Case(name: String)  extends Node
    final case object Elements           extends Node
    final case object MapKeys            extends Node
    final case object MapValues          extends Node
    // case class AtIndex(index: Int)    extends Node // TODO: For At support
    // case class AtMapKey(key: String)  extends Node // TODO: For At support
  }
}
