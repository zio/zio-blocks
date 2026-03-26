package golem.config

import zio.blocks.schema.Schema

trait ConfigSchema[T] {
  def describe(path: List[String]): List[AgentConfigDeclaration]
}

object ConfigSchema {
  def apply[T](implicit cs: ConfigSchema[T]): ConfigSchema[T] = cs

  implicit def fromSchema[A](implicit schema: Schema[A]): ConfigSchema[A] =
    new ConfigSchema[A] {
      def describe(path: List[String]): List[AgentConfigDeclaration] =
        ConfigIntrospection.declarations[A](path)
    }
}
