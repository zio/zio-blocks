package zio.blocks.schema

trait IsMap[MapKV] {
  type Map[_, _]
  type Key
  type Value

  def proof: MapKV =:= Map[Key, Value]
}

object IsMap {
  type Typed[MapKV, M[_, _], K, V] = IsMap[MapKV] {
    type Map[X, Y] = M[X, Y]
    type Key       = K
    type Value     = V
  }

  implicit def isMap[M[_, _], K, V]: Typed[M[K, V], M, K, V] =
    new IsMap[M[K, V]] {
      final type Map[X, Y] = M[X, Y]
      final type Key       = K
      final type Value     = V

      def proof: M[K, V] =:= Map[Key, Value] = implicitly[Map[Key, Value] =:= Map[Key, Value]]
    }
}
