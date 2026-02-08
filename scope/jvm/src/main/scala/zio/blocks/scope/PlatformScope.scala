package zio.blocks.scope

private[scope] object PlatformScope {
  def registerShutdownHook(cleanup: () => Unit): Unit =
    Runtime.getRuntime.addShutdownHook(new Thread(() => cleanup()))

  def threadYield(): Unit = Thread.`yield`()
}
