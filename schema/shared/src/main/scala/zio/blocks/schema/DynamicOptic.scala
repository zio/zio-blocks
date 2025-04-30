package zio.blocks.schema

case class DynamicOptic(nodes: Vector[DynamicOptic.Node])

object DynamicOptic {
  sealed trait Node {
    def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]]
  }
  object Node {
    final case class Field(name: String) extends Node {
      def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]] =
        reflect match {
          case record: Reflect.Record[_, _] => record.fieldByName(name).map(_.value)
          case _                            => None
        }
    }
    final case class Case(name: String) extends Node {
      def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]] =
        reflect match {
          case variant: Reflect.Variant[F, _] => variant.caseByName(name).map(_.value)
          case _                              => None
        }
    }
    // final case class AtIndex(index: Int)    extends Node // TODO: For At support
    // final case class AtMapKey(key: String)  extends Node // TODO: For At support
    case object Elements extends Node {
      def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]] =
        reflect match {
          case sequence: Reflect.Sequence[_, _, _] => Some(sequence.element)
          case _                                   => None
        }
    }
    case object MapKeys extends Node {
      def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]] =
        reflect match {
          case map: Reflect.Map[_, _, _, _] => Some(map.key)
          case _                            => None
        }
    }
    case object MapValues extends Node {
      def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]] =
        reflect match {
          case map: Reflect.Map[_, _, _, _] => Some(map.value)
          case _                            => None
        }
    }
  }
}
