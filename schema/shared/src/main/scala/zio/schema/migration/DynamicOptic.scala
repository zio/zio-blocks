package zio.schema.migration

/**
 * A pure data representation of a path into a `DynamicValue`.
 * Represents optical traversals without requiring runtime reflection or closures.
 */
sealed trait DynamicOptic extends Serializable { self =>
  import DynamicOptic._

  /**
   * Composes this optic with another optic.
   */
  final def ++(that: DynamicOptic): DynamicOptic =
    (self, that) match {
      case (End, other) => other
      case (other, End) => other
      case (RecordField(name, next1), other) => RecordField(name, next1 ++ other)
      case (SequenceElement(next1), other)   => SequenceElement(next1 ++ other)
      case (MapKey(next1), other)            => MapKey(next1 ++ other)
      case (MapValue(next1), other)          => MapValue(next1 ++ other)
      case (EnumCase(tag, next1), other)     => EnumCase(tag, next1 ++ other)
      case (Optional(next1), other)          => Optional(next1 ++ other)
    }

  /**
   * Modifies the leaf of this optic path. Used to algebraically compute structural inverses.
   */
  final def mapLeaf(f: End.type => DynamicOptic): DynamicOptic =
    self match {
      case End => f(End)
      case RecordField(name, next) => RecordField(name, next.mapLeaf(f))
      case SequenceElement(next)   => SequenceElement(next.mapLeaf(f))
      case MapKey(next)            => MapKey(next.mapLeaf(f))
      case MapValue(next)          => MapValue(next.mapLeaf(f))
      case EnumCase(tag, next)     => EnumCase(tag, next.mapLeaf(f))
      case Optional(next)          => Optional(next.mapLeaf(f))
    }

  /**
   * Retrieves the final node before End, returning its parent optic and the leaf node itself.
   */
  final def popLeaf: (DynamicOptic, DynamicOptic) = {
    def loop(optic: DynamicOptic): (DynamicOptic, DynamicOptic) = 
      optic match {
        case End => (End, End)
        case RecordField(name, End) => (End, RecordField(name, End))
        case RecordField(name, next) => 
          val (parent, leaf) = loop(next)
          (RecordField(name, parent), leaf)
        case SequenceElement(End) => (End, SequenceElement(End))
        case SequenceElement(next) =>
          val (parent, leaf) = loop(next)
          (SequenceElement(parent), leaf)
        case MapKey(End) => (End, MapKey(End))
        case MapKey(next) =>
          val (parent, leaf) = loop(next)
          (MapKey(parent), leaf)
        case MapValue(End) => (End, MapValue(End))
        case MapValue(next) =>
          val (parent, leaf) = loop(next)
          (MapValue(parent), leaf)
        case EnumCase(t, End) => (End, EnumCase(t, End))
        case EnumCase(t, next) =>
          val (parent, leaf) = loop(next)
          (EnumCase(t, parent), leaf)
        case Optional(End) => (End, Optional(End))
        case Optional(next) =>
          val (parent, leaf) = loop(next)
          (Optional(parent), leaf)
      }
    loop(self)
  }
}

object DynamicOptic {
  /** The terminal leaf of the optic path. */
  case object End extends DynamicOptic

  /** Navigates into a specific field of a structural record. */
  final case class RecordField(name: String, next: DynamicOptic = End) extends DynamicOptic

  /** Navigates into all elements of a sequence/collection. */
  final case class SequenceElement(next: DynamicOptic = End) extends DynamicOptic

  /** Navigates into all keys of a map. */
  final case class MapKey(next: DynamicOptic = End) extends DynamicOptic

  /** Navigates into all values of a map. */
  final case class MapValue(next: DynamicOptic = End) extends DynamicOptic

  /** Navigates into a specific case of an enum/union. */
  final case class EnumCase(tag: String, next: DynamicOptic = End) extends DynamicOptic

  /** Navigates into an optional value if it exists. */
  final case class Optional(next: DynamicOptic = End) extends DynamicOptic

  def empty: DynamicOptic = End
}
