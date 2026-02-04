package zio.blocks.schema.binding

/**
 * Type class that witnesses that a type `X` is an applied sequence type `C[A]`.
 *
 * This is used for Scala 2 compatibility when resolving sequence bindings in a
 * BindingResolver, since Scala 2 cannot express higher-kinded type parameters
 * directly in implicit parameters.
 *
 * @tparam X
 *   The applied type (e.g., `List[Int]`)
 */
trait UnapplySeq[X] {

  /**
   * The sequence type constructor (e.g., `List`).
   */
  type C[_]

  /**
   * The element type (e.g., `Int` for `List[Int]`).
   */
  type A

  /**
   * Evidence that `X` equals `C[A]`.
   */
  def ev: X =:= C[A]
}

object UnapplySeq extends UnapplySeqLowPriority {
  type Aux[X, C0[_], A0] = UnapplySeq[X] { type C[x] = C0[x]; type A = A0 }

  implicit def setInstance[A0]: UnapplySeq.Aux[Set[A0], Set, A0] =
    new UnapplySeq[Set[A0]] {
      type C[x] = Set[x]
      type A    = A0
      def ev: Set[A0] =:= Set[A0] = implicitly
    }

  implicit def listInstance[A0]: UnapplySeq.Aux[List[A0], List, A0] =
    new UnapplySeq[List[A0]] {
      type C[x] = List[x]
      type A    = A0
      def ev: List[A0] =:= List[A0] = implicitly
    }

  implicit def vectorInstance[A0]: UnapplySeq.Aux[Vector[A0], Vector, A0] =
    new UnapplySeq[Vector[A0]] {
      type C[x] = Vector[x]
      type A    = A0
      def ev: Vector[A0] =:= Vector[A0] = implicitly
    }

  implicit def indexedSeqInstance[A0]: UnapplySeq.Aux[IndexedSeq[A0], IndexedSeq, A0] =
    new UnapplySeq[IndexedSeq[A0]] {
      type C[x] = IndexedSeq[x]
      type A    = A0
      def ev: IndexedSeq[A0] =:= IndexedSeq[A0] = implicitly
    }

  implicit def chunkInstance[A0]: UnapplySeq.Aux[zio.blocks.chunk.Chunk[A0], zio.blocks.chunk.Chunk, A0] =
    new UnapplySeq[zio.blocks.chunk.Chunk[A0]] {
      type C[x] = zio.blocks.chunk.Chunk[x]
      type A    = A0
      def ev: zio.blocks.chunk.Chunk[A0] =:= zio.blocks.chunk.Chunk[A0] = implicitly
    }
}

trait UnapplySeqLowPriority {
  implicit def seqInstance[A0]: UnapplySeq.Aux[scala.collection.immutable.Seq[A0], scala.collection.immutable.Seq, A0] =
    new UnapplySeq[scala.collection.immutable.Seq[A0]] {
      type C[x] = scala.collection.immutable.Seq[x]
      type A    = A0
      def ev: scala.collection.immutable.Seq[A0] =:= scala.collection.immutable.Seq[A0] = implicitly
    }
}

/**
 * Type class that witnesses that a type `X` is an applied map type `M[K, V]`.
 *
 * This is used for Scala 2 compatibility when resolving map bindings in a
 * BindingResolver, since Scala 2 cannot express higher-kinded type parameters
 * directly in implicit parameters.
 *
 * @tparam X
 *   The applied type (e.g., `Map[String, Int]`)
 */
trait UnapplyMap[X] {

  /**
   * The map type constructor (e.g., `Map`).
   */
  type M[_, _]

  /**
   * The key type (e.g., `String` for `Map[String, Int]`).
   */
  type K

  /**
   * The value type (e.g., `Int` for `Map[String, Int]`).
   */
  type V

  /**
   * Evidence that `X` equals `M[K, V]`.
   */
  def ev: X =:= M[K, V]
}

object UnapplyMap {
  type Aux[X, M0[_, _], K0, V0] = UnapplyMap[X] { type M[k, v] = M0[k, v]; type K = K0; type V = V0 }

  implicit def mapInstance[K0, V0]: UnapplyMap.Aux[Map[K0, V0], Map, K0, V0] =
    new UnapplyMap[Map[K0, V0]] {
      type M[k, v] = Map[k, v]
      type K       = K0
      type V       = V0
      def ev: Map[K0, V0] =:= Map[K0, V0] = implicitly
    }
}
