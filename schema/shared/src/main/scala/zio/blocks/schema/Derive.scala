package zio.blocks.schema

object Derive {
  // We cannot use 'inline' or 'macro' directly here if we want a shared file.
  // Instead, we rely on the implicit search or define version-specific helpers.

  def into[A, B](implicit ev: Into[A, B]): Into[A, B] = ev
  def as[A, B](implicit ev: As[A, B]): As[A, B]       = ev
}
