package zio.blocks.context

import java.util.concurrent.ConcurrentHashMap
import zio.blocks.typeid.TypeId

private[context] object PlatformCache {
  def empty: Cache = new JvmCache(new ConcurrentHashMap[TypeId.Erased, Any]())

  private final class JvmCache(underlying: ConcurrentHashMap[TypeId.Erased, Any]) extends Cache {
    def get(key: TypeId.Erased): Any                     = underlying.get(key)
    def put(key: TypeId.Erased, value: Any): Unit        = { underlying.put(key, value); () }
    def putIfAbsent(key: TypeId.Erased, value: Any): Any = underlying.putIfAbsent(key, value)
  }
}
