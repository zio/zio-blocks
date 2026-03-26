package golem.config

final class Config[T] private[golem] (private val loadFn: () => T) {
  def value: T = loadFn()
}

object Config {
  private[golem] def apply[T](loadFn: () => T): Config[T] = new Config[T](loadFn)
  private[golem] def eager[T](value: T): Config[T]        = new Config[T](() => value)
}
