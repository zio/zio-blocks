package zio.blocks.schema.derive

import zio.blocks.schema._

final case class DeriveOverride[TC[_], S, A](optic: Optic.Bound[S, A], tc: TC[A])
