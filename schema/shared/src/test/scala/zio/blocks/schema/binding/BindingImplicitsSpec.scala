package zio.blocks.schema.binding

import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

import scala.collection.immutable.ArraySeq

object BindingImplicitsSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Binding Implicits")(
    suite("SeqConstructor implicits")(
      test("List") {
        assertTrue(implicitly[SeqConstructor[List]] == SeqConstructor.listConstructor)
      },
      test("Vector") {
        assertTrue(implicitly[SeqConstructor[Vector]] == SeqConstructor.vectorConstructor)
      },
      test("Set") {
        assertTrue(implicitly[SeqConstructor[Set]] == SeqConstructor.setConstructor)
      },
      test("IndexedSeq") {
        assertTrue(implicitly[SeqConstructor[IndexedSeq]] == SeqConstructor.indexedSeqConstructor)
      },
      test("Seq") {
        assertTrue(implicitly[SeqConstructor[collection.immutable.Seq]] == SeqConstructor.seqConstructor)
      },
      test("Chunk") {
        assertTrue(implicitly[SeqConstructor[Chunk]] == SeqConstructor.chunkConstructor)
      },
      test("ArraySeq") {
        assertTrue(implicitly[SeqConstructor[ArraySeq]] == SeqConstructor.arraySeqConstructor)
      }
    ),
    suite("SeqDeconstructor implicits")(
      test("List") {
        assertTrue(implicitly[SeqDeconstructor[List]] == SeqDeconstructor.listDeconstructor)
      },
      test("Vector") {
        assertTrue(implicitly[SeqDeconstructor[Vector]] == SeqDeconstructor.vectorDeconstructor)
      },
      test("Set") {
        assertTrue(implicitly[SeqDeconstructor[Set]] == SeqDeconstructor.setDeconstructor)
      },
      test("IndexedSeq") {
        assertTrue(implicitly[SeqDeconstructor[IndexedSeq]] == SeqDeconstructor.indexedSeqDeconstructor)
      },
      test("Seq") {
        assertTrue(implicitly[SeqDeconstructor[collection.immutable.Seq]] == SeqDeconstructor.seqDeconstructor)
      },
      test("Chunk") {
        assertTrue(implicitly[SeqDeconstructor[Chunk]] == SeqDeconstructor.chunkDeconstructor)
      },
      test("ArraySeq") {
        assertTrue(implicitly[SeqDeconstructor[ArraySeq]] == SeqDeconstructor.arraySeqDeconstructor)
      }
    ),
    suite("MapConstructor implicits")(
      test("Map") {
        assertTrue(implicitly[MapConstructor[Map]] == MapConstructor.map)
      }
    ),
    suite("MapDeconstructor implicits")(
      test("Map") {
        assertTrue(implicitly[MapDeconstructor[Map]] == MapDeconstructor.map)
      }
    )
  )
}
