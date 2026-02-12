package zio.blocks.scope

private[scope] object PlatformScope {
  def registerShutdownHook(cleanup: () => Unit): Unit = ()

  def threadYield(): Unit = ()
}
