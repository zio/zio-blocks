package golem.config

object ConfigHolder {
  private var _current: Option[Config[_]] = None

  private[golem] def set[T](config: Config[T]): Unit =
    _current = Some(config)

  private[golem] def clear(): Unit =
    _current = None

  def current[T]: Config[T] =
    _current match {
      case Some(c) => c.asInstanceOf[Config[T]]
      case None    =>
        throw new IllegalStateException(
          "No config is available. Ensure your agent trait extends AgentConfig[T] and an implicit Schema[T] is provided for your config type."
        )
    }
}
