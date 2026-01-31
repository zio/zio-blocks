package zio.blocks.schema.patch

import zio.blocks.schema.Schema

trait PatchModeCompanionVersionSpecific {

  // Schema instances derived automatically in Scala 3
  implicit lazy val strictSchema: Schema[PatchMode.Strict.type]   = Schema.derived
  implicit lazy val lenientSchema: Schema[PatchMode.Lenient.type] = Schema.derived
  implicit lazy val clobberSchema: Schema[PatchMode.Clobber.type] = Schema.derived
  implicit lazy val schema: Schema[PatchMode]                     = Schema.derived
}
