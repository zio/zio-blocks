package zio.blocks.schema.binding

trait MapDeconstructor[M[_, _]] {
  type KeyValue[_, _]

  def deconstruct[K, V](m: M[K, V]): Iterator[KeyValue[K, V]]

  def size[K, V](m: M[K, V]): Int

  def get[K, V](m: M[K, V], k: K): Option[V]

  def getKey[K, V](kv: KeyValue[K, V]): K

  def getValue[K, V](kv: KeyValue[K, V]): V

  def getKeyValue[K, V](kv: KeyValue[K, V]): (K, V)
}

object MapDeconstructor {
  implicit val map: MapDeconstructor[Map] = new MapDeconstructor[Map] {
    type KeyValue[K, V] = (K, V)

    def deconstruct[K, V](m: Map[K, V]): Iterator[(K, V)] = m.iterator

    def size[K, V](m: Map[K, V]): Int = m.size

    def get[K, V](m: Map[K, V], k: K): Option[V] = m.get(k)

    def getKey[K, V](kv: (K, V)): K = kv._1

    def getValue[K, V](kv: (K, V)): V = kv._2

    def getKeyValue[K, V](kv: (K, V)): (K, V) = kv
  }

}
