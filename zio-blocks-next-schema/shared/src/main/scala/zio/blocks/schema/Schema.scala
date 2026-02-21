package zio.blocks.schema

sealed trait SchemaVersion {
  def value: Int
}

object SchemaVersion {
  case object V1 extends SchemaVersion {
    override val value: Int = 1
  }

  case object V2 extends SchemaVersion {
    override val value: Int = 2
  }

  // Add more schema versions as needed
}