package zio.blocks.schema

case class DynamicOptic(nodes: IndexedSeq[DynamicOptic.Node]) {
  import DynamicOptic.Node

  def apply(that: DynamicOptic): DynamicOptic = DynamicOptic(nodes ++ that.nodes)

  def apply[F[_, _], A](reflect: Reflect[F, A]): Option[Reflect[F, A]] =
    nodes
      .foldLeft[Option[Reflect[F, ?]]](Some(reflect)) {
        case (Some(reflect), node) => node(reflect)
        case (None, _)             => None
      }
      .map(_.asInstanceOf[Reflect[F, A]])

  def field(name: String): DynamicOptic = DynamicOptic(nodes :+ Node.Field(name))

  def caseOf(name: String): DynamicOptic = DynamicOptic(nodes :+ Node.Case(name))

  def elements: DynamicOptic = DynamicOptic(nodes :+ Node.Elements)

  def mapKeys: DynamicOptic = DynamicOptic(nodes :+ Node.MapKeys)

  def mapValues: DynamicOptic = DynamicOptic(nodes :+ Node.MapValues)

  override lazy val toString: String = {
    val sb  = new StringBuilder
    var idx = 0
    while (idx < nodes.length) {
      nodes(idx) match {
        case Node.Field(name) => sb.append(s".$name")
        case Node.Case(name)  => sb.append(s".when[$name]")
        case Node.Elements    => sb.append(".each")
        case Node.MapKeys     => sb.append(".mapKeys")
        case Node.MapValues   => sb.append(".mapValues")
      }
      idx += 1
    }
    sb.toString()
  }
}

object DynamicOptic {
  val root: DynamicOptic = DynamicOptic(Vector.empty)

  val elements: DynamicOptic = DynamicOptic(Vector(Node.Elements))

  val mapKeys: DynamicOptic = DynamicOptic(Vector(Node.MapKeys))

  val mapValues: DynamicOptic = DynamicOptic(Vector(Node.MapValues))

  sealed trait Node {
    def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]]
  }

  object Node {
    val root = DynamicOptic(Vector.empty)
    case class Field(name: String) extends Node {
      def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]] =
        reflect match {
          case record: Reflect.Record[F, _] @scala.unchecked => record.fieldByName(name).map(_.value)
          case _                                             => None
        }
    }

    case class Case(name: String) extends Node {
      def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]] =
        reflect match {
          case variant: Reflect.Variant[F, _] @scala.unchecked => variant.caseByName(name).map(_.value)
          case _                                               => None
        }
    }

    // case class AtIndex(index: Int)    extends Node // TODO: For At support
    // case class AtMapKey(key: String)  extends Node // TODO: For At support

    case object Elements extends Node {
      def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]] =
        reflect match {
          case sequence: Reflect.Sequence[F, _, _] @scala.unchecked => new Some(sequence.element)
          case _                                                    => None
        }
    }

    case object MapKeys extends Node {
      def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]] =
        reflect match {
          case map: Reflect.Map[F, _, _, _] @scala.unchecked => new Some(map.key)
          case _                                             => None
        }
    }

    case object MapValues extends Node {
      def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]] =
        reflect match {
          case map: Reflect.Map[F, _, _, _] @scala.unchecked => new Some(map.value)
          case _                                             => None
        }
    }
  }
}
