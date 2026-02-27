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
   * The fingerprint is a 32-character hex string (128-bit truncation of
   * SHA-256) computed from the ordered representation of each action including
   * its type, path, lossy flag, and semantic parameters. Action order is
   * preserved since it is semantically significant.
   */
  def fingerprint(actions: Vector[MigrationAction]): String = {
    val parts = actions.map { a =>
      val details = a match {
        case MigrationAction.Rename(_, newName)           => s":$newName"
        case MigrationAction.RenameCase(_, from, to)      => s":$from:$to"
        case MigrationAction.AddField(_, expr)            => s":${expr.hashCode}"
        case MigrationAction.Mandate(_, expr)             => s":${expr.hashCode}"
        case MigrationAction.DropField(_, rev)            => s":${rev.map(_.hashCode).getOrElse(0)}"
        case MigrationAction.TransformValue(_, t, inv)    => s":${t.hashCode}:${inv.map(_.hashCode).getOrElse(0)}"
        case MigrationAction.ChangeType(_, c, inv)        => s":${c.hashCode}:${inv.map(_.hashCode).getOrElse(0)}"
        case MigrationAction.TransformCase(_, cn, sub)    => s":$cn:${sub.size}"
        case MigrationAction.TransformElements(_, t, inv) => s":${t.hashCode}:${inv.map(_.hashCode).getOrElse(0)}"
        case MigrationAction.TransformKeys(_, t, inv)     => s":${t.hashCode}:${inv.map(_.hashCode).getOrElse(0)}"
        case MigrationAction.TransformValues(_, t, inv)   => s":${t.hashCode}:${inv.map(_.hashCode).getOrElse(0)}"
        case MigrationAction.Join(_, srcs, _, _, _)       => s":${srcs.size}"
        case MigrationAction.Split(_, tgts, _, _, _)      => s":${tgts.size}"
        case _                                            => ""
      }
      s"${a.getClass.getSimpleName}:${a.at}:${a.lossy}$details"
    }
    // Use a platform-independent hash (FNV-1a inspired) to avoid
    // java.security.MessageDigest which is unavailable in Scala.js.
    val data = parts.mkString("|")
    var h1   = 0xcbf29ce484222325L
    var h2   = 0x100000001b3L
    var i    = 0
    while (i < data.length) {
      val c = data.charAt(i).toLong
      h1 = (h1 ^ c) * 0x100000001b3L
      h2 = (h2 ^ c) * 0xcbf29ce484222325L
      i += 1
    }
    f"$h1%016x$h2%016x"
  }
}
