package golem.config

import golem.data.{DataInterop, ElementSchema}
import zio.blocks.schema.{Reflect, Schema}
import zio.blocks.schema.binding.Binding
import zio.blocks.typeid.TypeId

private[golem] object ConfigIntrospection {

  private val secretFullName: String =
    TypeId.normalize(TypeId.of[Secret[Unit]]).fullName

  def declarations[A](prefix: List[String] = Nil)(implicit schema: Schema[A]): List[AgentConfigDeclaration] =
    walk(prefix, schema.reflect)

  private def isSecret(reflect: Reflect.Bound[_]): Boolean =
    TypeId.normalize(reflect.typeId).fullName == secretFullName

  private def isTupleRecord(rec: Reflect.Record[Binding, _]): Boolean = {
    val names = rec.fields.map(_.name).toSet
    (rec.fields.length == 2 && names == Set("_1", "_2")) ||
    (rec.fields.length == 3 && names == Set("_1", "_2", "_3"))
  }

  private def walk[A](path: List[String], reflect: Reflect.Bound[A]): List[AgentConfigDeclaration] =
    if (isSecret(reflect)) {
      val inner = reflect.asWrapperUnknown match {
        case Some(u) => u.wrapper.wrapped.asInstanceOf[Reflect.Bound[Any]]
        case None    => reflect.asInstanceOf[Reflect.Bound[Any]]
      }
      List(
        AgentConfigDeclaration(
          AgentConfigSource.Secret,
          path,
          ElementSchema.Component(DataInterop.reflectToDataType(inner))
        )
      )
    } else {
      reflect.asWrapperUnknown match {
        case Some(u) =>
          walk(path, u.wrapper.wrapped.asInstanceOf[Reflect.Bound[Any]])
        case None =>
          reflect.asRecord match {
            case Some(rec) if !isTupleRecord(rec) =>
              rec.fields.toList.flatMap { field =>
                walk(path :+ field.name, field.value.asInstanceOf[Reflect.Bound[Any]])
              }
            case _ =>
              List(
                AgentConfigDeclaration(
                  AgentConfigSource.Local,
                  path,
                  ElementSchema.Component(DataInterop.reflectToDataType(reflect))
                )
              )
          }
      }
    }
}
