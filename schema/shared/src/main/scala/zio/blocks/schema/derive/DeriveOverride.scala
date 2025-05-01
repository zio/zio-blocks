package zio.blocks.schema.derive

final case class DeriveOverride[TC[_], S, A](optic: Optic.Bound[S, A], tc: TC[A])
