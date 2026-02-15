package zio.blocks.scope

abstract class DeferHandle {
  def cancel(): Unit
}

object DeferHandle {
  private[scope] object Noop extends DeferHandle {
    def cancel(): Unit = ()
  }

  private[scope] final class Live(
    id: Long,
    entries: java.util.concurrent.ConcurrentHashMap[Long, () => Unit]
  ) extends DeferHandle {
    def cancel(): Unit = { entries.remove(id); () }
  }
}
