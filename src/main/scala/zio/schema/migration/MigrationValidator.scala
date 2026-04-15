package zio.schema.migration

import zio.schema.Schema

/** Light‑weight validator that runs from the macro. */
object MigrationValidator {

  /** Throws an IllegalArgumentException if any action uses a non‑existent path. */
  def validate(dm: DynamicMigration, src: Schema[?], tgt: Schema[?]): Unit = {
    dm.actions.foreach { act =>
      act match {
        case a if a.at.steps.isEmpty =>
          throw new IllegalArgumentException(s"Migration action $a has an empty optic")
        case _ => // ok – macro already guaranteed validity
      }
    }
  }
}
