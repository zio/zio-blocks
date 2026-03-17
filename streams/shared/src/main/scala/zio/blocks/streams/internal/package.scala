package zio.blocks.streams.internal

/** Interpreter storage lane index (0=Int, 1=Long, 2=Float, 3=Double, 4=Ref). */
type Lane = Int

/** Packed interpreter state in a single Long (56 bits used). */
type StreamState = Long

/** Operation tag for the Interpreter (8-bit value). */
type OpTag = Int

/**
 * Unsafe evidence for internal use where runtime dispatch (jvmType) guarantees
 * correctness.
 */
private[streams] inline def unsafeEvidence[A, B]: (A <:< B) = <:<.refl.asInstanceOf[A <:< B]

/**
 * Internal sentinel object used instead of `null` to detect end-of-stream in
 * the AnyRef lane. Using a dedicated object instead of `null` allows streams to
 * contain `null` elements without them being confused with end-of-stream.
 */
private[streams] val EndOfStream: AnyRef = new AnyRef {
  override def toString: String = "EndOfStream"
}
