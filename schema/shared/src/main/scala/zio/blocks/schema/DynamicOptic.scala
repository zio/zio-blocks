package zio.blocks.schema
import zio.blocks.schema.migration.MigrationError

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

  def modify(
    optic: DynamicOptic,
    value: DynamicValue
  )(f: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] = {
    def loop(
      nodes: List[DynamicOptic.Node],
      currentValue: DynamicValue
    ): Either[MigrationError, DynamicValue] =
      nodes match {
        case Nil => f(currentValue)
        case node :: tail =>
          node match {
            case DynamicOptic.Node.Field(name) =>
              currentValue match {
                case DynamicValue.Record(fields) =>
                  fields.indexWhere(_._1 == name) match {
                    case -1 =>
                      Left(MigrationError.PathError(optic, s"Field '$name' not found."))
                    case i =>
                      val (oldName, oldValue) = fields(i)
                      loop(tail, oldValue).map { newValue =>
                        DynamicValue.Record(fields.updated(i, oldName -> newValue))
                      }
                  }
                case _ => Left(MigrationError.PathError(optic, "Field optic can only be used on a record."))
              }
            case DynamicOptic.Node.Case(name) =>
              currentValue match {
                case DynamicValue.Variant(tag, value) if tag == name =>
                  loop(tail, value).map { newValue =>
                    DynamicValue.Variant(tag, newValue)
                  }
                case DynamicValue.Variant(tag, _) =>
                  Left(MigrationError.PathError(optic, s"Case '$name' does not match current sum type tag '$tag'."))
                case _ =>
                  Left(MigrationError.PathError(optic, "Case optic can only be used on a sum type."))
              }
            case DynamicOptic.Node.AtIndex(index) =>
              currentValue match {
                case DynamicValue.Sequence(values) =>
                  if (index >= 0 && index < values.length) {
                    loop(tail, values(index)).map { newValue =>
                      DynamicValue.Sequence(values.updated(index, newValue))
                    }
                  } else {
                    Left(MigrationError.PathError(optic, s"Index '$index' is out of bounds."))
                  }
                case _ =>
                  Left(MigrationError.PathError(optic, "AtIndex optic can only be used on a sequence type."))
              }
            case DynamicOptic.Node.Elements =>
              currentValue match {
                case DynamicValue.Sequence(values) =>
                  val initial: Either[MigrationError, Vector[DynamicValue]] = Right(Vector.empty)
                  values.foldLeft(initial) { (acc, value) =>
                    acc.flatMap(newValues => loop(tail, value).map(newValue => newValues :+ newValue))
                  }.map(DynamicValue.Sequence(_))

                case _ =>
                  Left(MigrationError.PathError(optic, "Elements optic can only be used on a sequence type."))
              }
            case DynamicOptic.Node.AtMapKey(key) =>
              currentValue match {
                case DynamicValue.Map(entries) =>
                  entries.indexWhere(_._1 == key) match {
                    case -1 =>
                      Left(MigrationError.PathError(optic, s"Key '$key' not found."))
                    case i =>
                      val (oldKey, oldValue) = entries(i)
                      loop(tail, oldValue).map { newValue =>
                        DynamicValue.Map(entries.updated(i, oldKey -> newValue))
                      }
                  }
                case _ =>
                  Left(MigrationError.PathError(optic, "AtMapKey optic can only be used on a dictionary type."))
              }
            case DynamicOptic.Node.MapValues =>
              currentValue match {
                case DynamicValue.Map(entries) =>
                  val initial: Either[MigrationError, Vector[(DynamicValue, DynamicValue)]] = Right(Vector.empty)
                  entries.foldLeft(initial) { (acc, entry) =>
                    val (key, value) = entry
                    acc.flatMap(newEntries => loop(tail, value).map(newValue => newEntries :+ (key -> newValue)))
                  }.map(DynamicValue.Map(_))
                case _ =>
                  Left(MigrationError.PathError(optic, "MapValues optic can only be used on a dictionary type."))
              }
            case DynamicOptic.Node.MapKeys =>
              currentValue match {
                case DynamicValue.Map(entries) =>
                  val initial: Either[MigrationError, Vector[(DynamicValue, DynamicValue)]] = Right(Vector.empty)
                  entries.foldLeft(initial) { (acc, entry) =>
                    val (key, value) = entry
                    acc.flatMap(newEntries => loop(tail, key).map(newKey => newEntries :+ (newKey -> value)))
                  }.map(DynamicValue.Map(_))
                case _ =>
                  Left(MigrationError.PathError(optic, "MapKeys optic can only be used on a dictionary type."))
              }
            case _ => Left(MigrationError.NotYetImplemented)
          }
      }
    loop(optic.nodes.toList, value)
  }

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

  def get(
    optic: DynamicOptic,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    def loop(
      nodes: List[DynamicOptic.Node],
      currentValue: DynamicValue
    ): Either[MigrationError, DynamicValue] =
      nodes match {
        case Nil => Right(currentValue)
        case node :: tail =>
          node match {
            case DynamicOptic.Node.Field(name) =>
              currentValue match {
                case DynamicValue.Record(fields) =>
                  fields.indexWhere(_._1 == name) match {
                    case -1 =>
                      Left(MigrationError.PathError(optic, s"Field '$name' not found."))
                    case i =>
                      loop(tail, fields(i)._2)
                  }
                case _ => Left(MigrationError.PathError(optic, "Field optic can only be used on a record."))
              }
            case DynamicOptic.Node.Case(name) =>
              currentValue match {
                case DynamicValue.Variant(tag, value) if tag == name =>
                  loop(tail, value)
                case DynamicValue.Variant(tag, _) =>
                  Left(MigrationError.PathError(optic, s"Case '$name' does not match current sum type tag '$tag'."))
                case _ =>
                  Left(MigrationError.PathError(optic, "Case optic can only be used on a sum type."))
              }
            case DynamicOptic.Node.AtIndex(index) =>
              currentValue match {
                case DynamicValue.Sequence(values) =>
                  if (index >= 0 && index < values.length) {
                    loop(tail, values(index))
                  } else {
                    Left(MigrationError.PathError(optic, s"Index '$index' is out of bounds."))
                  }
                case _ =>
                  Left(MigrationError.PathError(optic, "AtIndex optic can only be used on a sequence type."))
              }
            case DynamicOptic.Node.Elements =>
              currentValue match {
                case DynamicValue.Sequence(values) =>
                  val initial: Either[MigrationError, Vector[DynamicValue]] = Right(Vector.empty)
                  values.foldLeft(initial) { (acc, value) =>
                    acc.flatMap(extractedValues => loop(tail, value).map(newValue => extractedValues :+ newValue))
                  }.map(DynamicValue.Sequence(_))
                case _ =>
                  Left(MigrationError.PathError(optic, "Elements optic can only be used on a sequence type."))
              }
            case DynamicOptic.Node.AtMapKey(key) =>
              currentValue match {
                case DynamicValue.Map(entries) =>
                  entries.indexWhere(_._1 == key) match {
                    case -1 =>
                      Left(MigrationError.PathError(optic, s"Key '$key' not found."))
                    case i =>
                      loop(tail, entries(i)._2)
                  }
                case _ =>
                  Left(MigrationError.PathError(optic, "AtMapKey optic can only be used on a dictionary type."))
              }
            case DynamicOptic.Node.MapValues =>
              currentValue match {
                case DynamicValue.Map(entries) =>
                  val initial: Either[MigrationError, Vector[(DynamicValue, DynamicValue)]] = Right(Vector.empty)
                  entries.foldLeft(initial) { (acc, entry) =>
                    val (key, value) = entry
                    acc.flatMap(extractedEntries => loop(tail, value).map(newValue => extractedEntries :+ (key -> newValue)))
                  }.map(DynamicValue.Map(_))
                case _ =>
                  Left(MigrationError.PathError(optic, "MapValues optic can only be used on a dictionary type."))
              }
            case DynamicOptic.Node.MapKeys =>
               currentValue match {
                 case DynamicValue.Map(entries) =>
                   val initial: Either[MigrationError, Vector[(DynamicValue, DynamicValue)]] = Right(Vector.empty)
                   entries.foldLeft(initial) { (acc, entry) =>
                     val (key, value) = entry
                     acc.flatMap(extractedEntries => loop(tail, key).map(newKey => extractedEntries :+ (newKey -> value)))
                   }.map(DynamicValue.Map(_))
                 case _ =>
                   Left(MigrationError.PathError(optic, "MapKeys optic can only be used on a dictionary type."))
               }
             case _ => Left(MigrationError.NotYetImplemented)
          }
      }
    loop(optic.nodes.toList, value)
  }
}
