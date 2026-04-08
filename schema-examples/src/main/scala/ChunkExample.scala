import zio.Chunk

/** Minimal runnable example demonstrating basic `Chunk` operations. */
object ChunkExample extends App {
  val numbers = Chunk(1, 2, 3)
  val doubled = numbers.map(_ * 2)

  println(s"Original chunk: $numbers")
  println(s"Doubled chunk: $doubled")
}
