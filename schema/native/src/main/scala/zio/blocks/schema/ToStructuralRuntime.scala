package zio.blocks.schema

/**
 * Native platform stub - ToStructural is not supported on Native.
 */
object ToStructuralRuntime {

  def transformProductSchema[A, S](schema: Schema[A]): Schema[S] =
    throw new UnsupportedOperationException(
      "Structural type schema transformation is not supported on Scala Native. " +
        "Structural types require JVM reflection."
    )

  def transformTupleSchema[A, S](schema: Schema[A]): Schema[S] =
    throw new UnsupportedOperationException(
      "Structural type schema transformation is not supported on Scala Native. " +
        "Structural types require JVM reflection."
    )
}

