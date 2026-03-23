package golem.config

final class Config[T](val value: T)

object Config {
  private[golem] def apply[T](value: T): Config[T] = new Config[T](value)
}
