package golem.config

import golem.data.GolemSchema

trait ConfigSchema[T] {
  def describe(path: List[String]): List[AgentConfigDeclaration]
}

object ConfigSchema {
  def apply[T](implicit cs: ConfigSchema[T]): ConfigSchema[T] = cs

  private def leaf[A](source: AgentConfigSource)(implicit gs: GolemSchema[A]): ConfigSchema[A] =
    new ConfigSchema[A] {
      def describe(path: List[String]): List[AgentConfigDeclaration] =
        List(AgentConfigDeclaration(source, path, gs.elementSchema))
    }

  implicit val stringConfigSchema: ConfigSchema[String]   = leaf[String](AgentConfigSource.Local)
  implicit val intConfigSchema: ConfigSchema[Int]          = leaf[Int](AgentConfigSource.Local)
  implicit val longConfigSchema: ConfigSchema[Long]        = leaf[Long](AgentConfigSource.Local)
  implicit val doubleConfigSchema: ConfigSchema[Double]    = leaf[Double](AgentConfigSource.Local)
  implicit val booleanConfigSchema: ConfigSchema[Boolean]  = leaf[Boolean](AgentConfigSource.Local)

  implicit def secretConfigSchema[A](implicit gs: GolemSchema[A]): ConfigSchema[Secret[A]] =
    new ConfigSchema[Secret[A]] {
      def describe(path: List[String]): List[AgentConfigDeclaration] =
        List(AgentConfigDeclaration(AgentConfigSource.Secret, path, gs.elementSchema))
    }
}
