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

  val empty: TypeRegistry = new Collection()
}

case class RebindError(message: String)
