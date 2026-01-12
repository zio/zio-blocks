package zio.blocks.schema.migration

import scala.collection.immutable.Vector

/**
 * Identifies schemas WITHOUT keeping old Scala case classes around.
 */
final case class SchemaId(name: String, version: Int) extends Product with Serializable

final case class StoredMigration(from: SchemaId, to: SchemaId, program: DynamicMigration)

final class MigrationRegistry(private val edges: Vector[StoredMigration]) {

  /**
   * Plans a composed DynamicMigration from -> to (BFS).
   */
  def plan(from: SchemaId, to: SchemaId): Option[DynamicMigration] = {
    import scala.collection.mutable

    val outgoing: Map[SchemaId, Vector[StoredMigration]] =
      edges.groupBy(_.from).view.mapValues(Vector.from).toMap

    val queue = mutable.Queue[(SchemaId, DynamicMigration)]((from, DynamicMigration.id))
    val seen  = mutable.Set[SchemaId](from)

    while (queue.nonEmpty) {
      val (cur, prog) = queue.dequeue()
      if (cur == to) return Some(prog)

      outgoing.getOrElse(cur, Vector.empty).foreach { e =>
        if (!seen.contains(e.to)) {
          seen.add(e.to)
          queue.enqueue((e.to, prog ++ e.program))
        }
      }
    }

    None
  }
}

object MigrationRegistry {
  def empty: MigrationRegistry = new MigrationRegistry(Vector.empty)
  def apply(edges: StoredMigration*): MigrationRegistry = new MigrationRegistry(Vector.from(edges))
}
