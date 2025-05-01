package zio.blocks.schema

case class DynamicOptic(nodes: IndexedSeq[DynamicOptic.Node])

object DynamicOptic {
  sealed trait Node {
    def apply[F[_, _]](reflect: Reflect[F, _]): Option[Reflect[F, _]]
  }

  object Node {
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
