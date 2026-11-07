package tunejsonthroughput

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Tune JSON Throughput: Derive Each Codec Once and Reuse It
 *
 * Shows which expressions return a cached codec and which derive a new one on
 * every call.
 *
 * Run with: cd schema-examples && sbt "runMain
 * tunejsonthroughput.ReuseCodecExample"
 */
object ReuseCodecExample extends App {
  import BookCatalog._

  println(Book.schema.jsonCodec eq Book.schema.jsonCodec)
  println(Schema[List[Book]].jsonCodec eq Schema[List[Book]].jsonCodec)

  // Schema#derive never caches: each call builds a new codec
  val derivedOnce: JsonCodec[Book]  = Book.schema.derive(JsonFormat)
  val derivedTwice: JsonCodec[Book] = Book.schema.derive(JsonFormat)
  println(derivedOnce eq derivedTwice)

  object CatalogCodecs {
    val snakeCaseCatalog: JsonCodec[List[Book]] =
      Schema[List[Book]].derive(JsonCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase))
  }

  println(CatalogCodecs.snakeCaseCatalog eq CatalogCodecs.snakeCaseCatalog)
  println(new String(CatalogCodecs.snakeCaseCatalog.encode(catalog.take(1)), "UTF-8").take(80))
}
