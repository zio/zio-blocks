package zio.blocks.schema.registry

import zio.blocks.schema.TypeName
import zio.blocks.schema.binding.Binding
import scala.collection.mutable

trait TypeRegistry {
  def lookup[A](typeName: TypeName[A]): Option[Binding[?, A]]
}

object TypeRegistry {
  case class Collection() extends TypeRegistry {
    private[this] val map = new mutable.HashMap[TypeName[?], Binding[?, ?]]

    override def lookup[A](typeName: TypeName[A]): Option[Binding[?, A]] =
      map.get(typeName).asInstanceOf[Option[Binding[?, A]]]

    def add[A](typeName: TypeName[A], binding: Binding[?, A]): Unit = map.put(typeName, binding)

    def remove[A](typeName: TypeName[A]): Unit = map.remove(typeName)
  }

  def empty: TypeRegistry = new Collection()

  def default: TypeRegistry = {
    val collection = new Collection()
    collection.add(TypeName.unit, Binding.Primitive.unit)
    collection.add(TypeName.boolean, Binding.Primitive.boolean)
    collection.add(TypeName.byte, Binding.Primitive.byte)
    collection.add(TypeName.short, Binding.Primitive.short)
    collection.add(TypeName.int, Binding.Primitive.int)
    collection.add(TypeName.long, Binding.Primitive.long)
    collection.add(TypeName.float, Binding.Primitive.float)
    collection.add(TypeName.double, Binding.Primitive.double)
    collection.add(TypeName.char, Binding.Primitive.char)
    collection.add(TypeName.string, Binding.Primitive.string)
    collection.add(TypeName.bigInt, Binding.Primitive.bigInt)
    collection.add(TypeName.bigDecimal, Binding.Primitive.bigDecimal)
    collection.add(TypeName.dayOfWeek, Binding.Primitive.dayOfWeek)
    collection.add(TypeName.duration, Binding.Primitive.duration)
    collection.add(TypeName.instant, Binding.Primitive.instant)
    collection.add(TypeName.localDate, Binding.Primitive.localDate)
    collection.add(TypeName.localDateTime, Binding.Primitive.localDateTime)
    collection.add(TypeName.localTime, Binding.Primitive.localTime)
    collection.add(TypeName.month, Binding.Primitive.month)
    collection.add(TypeName.monthDay, Binding.Primitive.monthDay)
    collection.add(TypeName.offsetDateTime, Binding.Primitive.offsetDateTime)
    collection.add(TypeName.offsetTime, Binding.Primitive.offsetTime)
    collection.add(TypeName.period, Binding.Primitive.period)
    collection.add(TypeName.year, Binding.Primitive.year)
    collection.add(TypeName.yearMonth, Binding.Primitive.yearMonth)
    collection.add(TypeName.zoneId, Binding.Primitive.zoneId)
    collection.add(TypeName.zoneOffset, Binding.Primitive.zoneOffset)
    collection.add(TypeName.zonedDateTime, Binding.Primitive.zonedDateTime)
    collection.add(TypeName.currency, Binding.Primitive.currency)
    collection.add(TypeName.uuid, Binding.Primitive.uuid)
    collection.add(TypeName.uuid, Binding.Primitive.uuid)
    collection
  }
}

case class RebindError(message: String)
