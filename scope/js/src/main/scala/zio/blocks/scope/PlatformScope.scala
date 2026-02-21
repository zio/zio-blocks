package zio.blocks.scope

private[scope] object PlatformScope {
  def registerShutdownHook(cleanup: () => Unit): Unit = ()

  def threadYield(): Unit = ()

  def captureOwner(): AnyRef = null

  def isOwner(owner: AnyRef): Boolean = true

  def ownerName(owner: AnyRef): String = "main"

  def currentThreadName(): String = "main"
}
