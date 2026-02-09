package zio.blocks.schema

import zio.blocks.chunk.Chunk

/**
 * A schema metadata structure can store arbitrary metadata attached to the
 * structure of a type.
 */
final case class SchemaMetadata[S, G[_]](private val map: Map[Optic[S, ?], IndexedSeq[?]]) {
  def add[A](optic: Optic[S, A], value: G[A]): SchemaMetadata[S, G] =
    new SchemaMetadata[S, G](map.updated(optic, map.getOrElse(optic, Chunk.empty) :+ value))

  def fold[Z](z: Z)(fold: SchemaMetadata.Folder[S, G, Z]): Z =
    map.foldLeft(z) { case (z, (optic, values)) =>
      values.foldLeft(z) { case (z, value) =>
        fold.fold(z, optic.asInstanceOf[Optic[S, Any]], value.asInstanceOf[G[Any]])
      }
    }

  def get[A](optic: Optic[S, A]): Option[G[A]] = getAll(optic).headOption

  def getAll[A](optic: Optic[S, A]): IndexedSeq[G[A]] = map.getOrElse(optic, Chunk.empty).asInstanceOf[IndexedSeq[G[A]]]

  def removeAll[A](optic: Optic[S, A]): SchemaMetadata[S, G] = SchemaMetadata[S, G](map - optic)

  def size: Int = map.size
}

object SchemaMetadata {
  trait Folder[S, G[_], Z] {
    def initial: Z

    def fold[A](z: Z, optic: Optic[S, A], value: G[A]): Z
  }

  object Folder {
    type Bound[S, G[_], Z] = Folder[S, G, Z]

    type Simple[S, Z] = Bound[S, Id, Z]

    def simple[S, Z](initial0: Z)(f: (Z, Optic[S, ?]) => Z): Simple[S, Z] = new Simple[S, Z] {
      val initial: Z = initial0

      def fold[A](z: Z, optic: Optic[S, A], value: Id[A]): Z = f(z, optic)
    }
  }

  type Id[A] = A

  type Bound[S, G[_]] = SchemaMetadata[S, G]

  type Simple[S] = Bound[S, Id]

  def bound[S, G[_]]: SchemaMetadata[S, G] = empty[S, G]

  def empty[S, G[_]]: SchemaMetadata[S, G] = SchemaMetadata[S, G](Map())

  def simple[S]: SchemaMetadata.Simple[S] = empty[S, Id]
}
