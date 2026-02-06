package zio.blocks.context

import zio.blocks.chunk.Chunk
import zio.blocks.typeid.TypeId

private[context] final class ContextEntries private (
  private val entries: Array[(TypeId.Erased, Any)]
) {
  def size: Int = entries.length

  def isEmpty: Boolean = entries.length == 0

  def get(key: TypeId.Erased): Any = {
    var i = entries.length - 1
    while (i >= 0) {
      val (k, v) = entries(i)
      if (k == key) return v
      i -= 1
    }
    null
  }

  def getBySubtype(key: TypeId.Erased): Any = {
    var i = entries.length - 1
    while (i >= 0) {
      val (k, v) = entries(i)
      if (k == key) return v
      if (k.isSubtypeOf(key)) return v
      i -= 1
    }
    null
  }

  def updated(key: TypeId.Erased, value: Any): ContextEntries = {
    var found = false
    var i     = 0
    while (i < entries.length && !found) {
      if (entries(i)._1 == key) found = true
      i += 1
    }
    if (found) {
      val newEntries = new Array[(TypeId.Erased, Any)](entries.length)
      var j          = 0
      var k          = 0
      while (j < entries.length) {
        if (entries(j)._1 != key) {
          newEntries(k) = entries(j)
          k += 1
        }
        j += 1
      }
      newEntries(k) = (key, value)
      new ContextEntries(newEntries)
    } else {
      val newEntries = new Array[(TypeId.Erased, Any)](entries.length + 1)
      System.arraycopy(entries, 0, newEntries, 0, entries.length)
      newEntries(entries.length) = (key, value)
      new ContextEntries(newEntries)
    }
  }

  def removed(key: TypeId.Erased): ContextEntries = {
    var found = false
    var i     = 0
    while (i < entries.length && !found) {
      if (entries(i)._1 == key) found = true
      i += 1
    }
    if (found) {
      val newEntries = new Array[(TypeId.Erased, Any)](entries.length - 1)
      var j          = 0
      var k          = 0
      while (j < entries.length) {
        if (entries(j)._1 != key) {
          newEntries(k) = entries(j)
          k += 1
        }
        j += 1
      }
      new ContextEntries(newEntries)
    } else {
      this
    }
  }

  def pruned(keys: Chunk[TypeId.Erased]): ContextEntries = {
    val keySet     = keys.toSet
    val newEntries = entries.filter { case (k, _) => keySet.contains(k) }
    new ContextEntries(newEntries)
  }

  def union(that: ContextEntries): ContextEntries = {
    var result = this
    var i      = 0
    while (i < that.entries.length) {
      val (k, v) = that.entries(i)
      result = result.updated(k, v)
      i += 1
    }
    result
  }

  def reverseIterator: Iterator[(TypeId.Erased, Any)] = entries.reverseIterator
}

private[context] object ContextEntries {
  val empty: ContextEntries = new ContextEntries(Array.empty)

  def apply(entries: (TypeId.Erased, Any)*): ContextEntries = {
    var result = empty
    entries.foreach { case (k, v) => result = result.updated(k, v) }
    result
  }
}
