package zio.blocks.schema.binding

trait MapConstructor[M[_, _]] {
  type ObjectBuilder[_, _]

  def newObjectBuilder[K, V](sizeHint: Int = -1): ObjectBuilder[K, V]

  def addObject[K, V](builder: ObjectBuilder[K, V], k: K, v: V): Unit

  def resultObject[K, V](builder: ObjectBuilder[K, V]): M[K, V]

  def emptyObject[K, V]: M[K, V]

  def updated[K, V](map: M[K, V], key: K, value: V): M[K, V]
}

object MapConstructor {
  def apply[M[_, _]](implicit mc: MapConstructor[M]): MapConstructor[M] = mc

  implicit val map: MapConstructor[Map] = new MapConstructor[Map] {
    type ObjectBuilder[K, V] = scala.collection.mutable.Builder[(K, V), Map[K, V]]

    def newObjectBuilder[K, V](sizeHint: Int): ObjectBuilder[K, V] = Map.newBuilder[K, V]

    def addObject[K, V](builder: ObjectBuilder[K, V], k: K, v: V): Unit = builder.addOne((k, v))

    def resultObject[K, V](builder: ObjectBuilder[K, V]): Map[K, V] = builder.result()

    def emptyObject[K, V]: Map[K, V] = Map.empty

    def updated[K, V](map: Map[K, V], key: K, value: V): Map[K, V] = map.updated(key, value)
  }

}
