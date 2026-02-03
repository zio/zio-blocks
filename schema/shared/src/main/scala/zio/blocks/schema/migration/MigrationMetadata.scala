package zio.blocks.schema.migration

/**
 * Metadata associated with a migration for identification, auditing, and
 * conflict detection.
 *
 * @param id
 *   Unique migration identifier (e.g. "v1-to-v2")
 * @param description
 *   Human-readable description of what this migration does
 * @param timestamp
 *   Creation timestamp (epoch millis)
 * @param createdBy
 *   Who or what created this migration
 * @param fingerprint
 *   Structural hash for conflict detection, computed by `.build()`
 */
final case class MigrationMetadata(
  id: Option[String] = None,
  description: Option[String] = None,
  timestamp: Option[Long] = None,
  createdBy: Option[String] = None,
  fingerprint: Option[String] = None
)

object MigrationMetadata {
  val empty: MigrationMetadata = MigrationMetadata()

  /**
   * Computes a deterministic structural fingerprint of a migration's actions.
   *
   * The fingerprint is a 32-character hex string (128-bit truncation of SHA-256)
   * computed from the canonical representation of each action: its type name,
   * path, and lossy flag. This enables detecting when composed migrations
   * differ structurally, deduplicating migrations in a registry, and auditing
   * which migration transformed a given event.
   */
  def fingerprint(actions: Vector[MigrationAction]): String = {
    val parts = actions.map { a =>
      s"${a.getClass.getSimpleName}:${a.at}:${a.lossy}"
    }.sorted
    val digest = java.security.MessageDigest
      .getInstance("SHA-256")
      .digest(parts.mkString("|").getBytes("UTF-8"))
    digest.take(16).map("%02x".format(_)).mkString
  }
}
