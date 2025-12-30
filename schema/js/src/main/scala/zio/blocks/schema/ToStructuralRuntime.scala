package zio.blocks.schema

/**
 * JS platform stub - ToStructural is not supported on JS.
 */
object ToStructuralRuntime {

  def transformProductSchema[A, S](schema: Schema[A]): Schema[S] =
    throw new UnsupportedOperationException(
      "Structural type schema transformation is not supported on JavaScript. " +
        "Structural types require JVM reflection."
    )

  def transformTupleSchema[A, S](schema: Schema[A]): Schema[S] =
    throw new UnsupportedOperationException(
      "Structural type schema transformation is not supported on JavaScript. " +
        "Structural types require JVM reflection."
    )
}

