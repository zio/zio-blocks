package zio.blocks.scope

private[scope] object PlatformScope {
  def registerShutdownHook(cleanup: () => Unit): Unit =
    Runtime.getRuntime.addShutdownHook(new Thread(() => cleanup()))

  def threadYield(): Unit = Thread.`yield`()

  def captureOwner(): AnyRef = Thread.currentThread()

  def isOwner(owner: AnyRef): Boolean = Thread.currentThread() eq owner

  def ownerName(owner: AnyRef): String = owner.asInstanceOf[Thread].getName

  def currentThreadName(): String = Thread.currentThread().getName
}
