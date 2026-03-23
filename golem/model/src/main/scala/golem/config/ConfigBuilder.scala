package golem.config

trait ConfigBuilder[T] {
  def build(path: List[String], loader: ConfigFieldLoader): T
}

object ConfigBuilder {
  def apply[T](implicit cb: ConfigBuilder[T]): ConfigBuilder[T] = cb
}
