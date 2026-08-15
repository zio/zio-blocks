package zio.blocks.endpoint

import scala.NamedTuple
import zio.blocks.endpoint.Endpoint
import zio.blocks.endpoint.AuthType
import zio.blocks.endpoint.PathCodec

/** Runtime type-class: nest a constant path prefix into every Endpoint leaf of a group. */
trait NestPrefix[NT] {
  def nest(nt: NT, prefix: String): NT
}

object NestPrefix {
  // Endpoint leaf: copy with route.nest(PathCodec(prefix)) — preserves PathVars (prefix PathVars = EmptyTuple)
  given endpointNest[PathInput, Input, Err, Output, Auth <: AuthType]: NestPrefix[Endpoint[PathInput, Input, Err, Output, Auth]] with {
    def nest(ep: Endpoint[PathInput, Input, Err, Output, Auth], prefix: String): Endpoint[PathInput, Input, Err, Output, Auth] =
      ep.copy(route = ep.route.nest(PathCodec(prefix)))
  }

  given emptyNest: NestPrefix[EmptyTuple] with {
    def nest(t: EmptyTuple, prefix: String): EmptyTuple = t
  }

  // Tuple cons: recurse head + tail (element can be Endpoint or a nested NamedTuple)
  given consNest[H, T <: Tuple](using nh: NestPrefix[H], nt: NestPrefix[T]): NestPrefix[H *: T] with {
    def nest(t: H *: T, prefix: String): H *: T =
      nh.nest(t.head, prefix) *: nt.nest(t.tail, prefix)
  }

  // NamedTuple: the runtime value IS the values-tuple; recurse into it and re-wrap preserving names
  given namedTupleNest[N <: Tuple, V <: Tuple](using nv: NestPrefix[V]): NestPrefix[NamedTuple.NamedTuple[N, V]] with {
    def nest(nt: NamedTuple.NamedTuple[N, V], prefix: String): NamedTuple.NamedTuple[N, V] =
      nv.nest(nt.asInstanceOf[V], prefix).asInstanceOf[NamedTuple.NamedTuple[N, V]]
  }
}
