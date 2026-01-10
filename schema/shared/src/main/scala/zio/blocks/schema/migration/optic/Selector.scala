package zio.blocks.schema.migration.optic

/**
 * A Type Class that proves a selector function (S => A) corresponds to a valid DynamicOptic path.
 * This breaks the dependency cycle in Scala 2 macros.
 */
trait Selector[S, A] {
  def path: DynamicOptic
}