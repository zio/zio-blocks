package zio.blocks.schema.fixtures

import neotype._

type PlayerId = PlayerId.Type
object PlayerId extends Subtype[String]
