package zio.blocks.context

import scala.collection.mutable
import zio.blocks.typeid.TypeId

private[context] object PlatformCache {
  def empty: Cache = new JsCache(mutable.HashMap.empty[TypeId.Erased, Any])

  private final class JsCache(underlying: mutable.HashMap[TypeId.Erased, Any]) extends Cache {
    def get(key: TypeId.Erased): Any                     = underlying.getOrElse(key, null)
    def put(key: TypeId.Erased, value: Any): Unit        = { underlying.update(key, value); () }
    def putIfAbsent(key: TypeId.Erased, value: Any): Any =
      underlying.get(key) match {
        case Some(existing) => existing
        case None           =>
          underlying.update(key, value)
          null
      }
  }
}
