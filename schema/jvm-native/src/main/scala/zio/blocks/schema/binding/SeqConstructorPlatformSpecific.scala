package zio.blocks.schema.binding

import java.util.concurrent.ConcurrentHashMap

trait SeqConstructorPlatformSpecific {
  private[this] val classCache = new ConcurrentHashMap[String, Class[_]]
  private[this] val classForNameFunction = new java.util.function.Function[String, Class[_]] {
    override def apply(fqcn: String): Class[_] = Class.forName(fqcn)
  }

  def newArray[A](fqcn: String, length: Int): Array[A] = {
    val clazz = classCache.computeIfAbsent(fqcn, classForNameFunction).asInstanceOf[Class[A]]
    java.lang.reflect.Array.newInstance(clazz, length).asInstanceOf[Array[A]]
  }
}
