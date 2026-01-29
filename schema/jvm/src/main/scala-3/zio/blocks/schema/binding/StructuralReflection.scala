package zio.blocks.schema.binding

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

private[binding] object StructuralReflection {
  private val methodCache = new ConcurrentHashMap[(Class[?], String), Method]()

  def get(obj: AnyRef, name: String): AnyRef = {
    val cls = obj.getClass
    val key = (cls, name)
    var m   = methodCache.get(key)
    if (m == null) {
      m = cls.getMethod(name)
      methodCache.put(key, m)
    }
    m.invoke(obj)
  }

  def hasAll(obj: AnyRef, names: Array[String]): Boolean = {
    if (obj == null) return false
    val cls = obj.getClass
    var i   = 0
    while (i < names.length) {
      val name = names(i)
      try {
        cls.getMethod(name)
      } catch {
        case _: NoSuchMethodException => return false
      }
      i += 1
    }
    true
  }
}
