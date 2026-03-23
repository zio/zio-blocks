package golem.config

final class Secret[T](private[golem] val path: List[String], private[golem] val loader: () => T) {
  def get: T = loader()
}
