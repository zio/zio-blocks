package ironrefinedtypes

import zio.blocks.schema._

// Section "Keep IDs Apart with a Typed Id": one opaque type for every entity's ID.
// Inside `object ids`, `Id[A]` is a `Long`; everywhere else it is a distinct type.
object ids {
  opaque type Id[+A] = Long

  object Id {
    def apply[A](value: Long): Id[A] = value

    extension [A](id: Id[A]) def value: Long = id

    // `Schema.long`, not `Schema[Long]`: in this scope `Id[A]` is a `Long`, so
    // `Schema[Long]` would find this very instance and loop forever.
    implicit def schema[A]: Schema[Id[A]] = Schema.long.transform(Id[A](_), _.value)
  }
}
