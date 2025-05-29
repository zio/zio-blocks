package zio.blocks.schema

import zio.Scope
import zio.test.Assertion._
import zio.test._

object SchemaMetadataSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("SchemaMetadataSpec")(
    suiteAll("SchemaMetadata") {
      case class Record(s: String)

      object Record extends CompanionOptics[Record] {
        implicit val schema: Schema[Record] = Schema.derived
        val s: Lens[Record, String]         = optic(_.s)
      }

      test("reports accurate size") {
        val metadata = SchemaMetadata[Record, IndexedSeq](Map(Record.s -> IndexedSeq("foo")))
        assert(metadata.size)(equalTo(1))
      }

      test("metadata can be added") {
        val metadata  = SchemaMetadata[Record, IndexedSeq](Map(Record.s -> IndexedSeq("foo")))
        val metadata2 = metadata.add(Record.s, IndexedSeq("bar"))
        assert(metadata.getAll(Record.s).length)(equalTo(1)) &&
        assert(metadata2.getAll(Record.s).length)(equalTo(2))
      }

      test("metadata can be removed") {
        val metadata  = SchemaMetadata[Record, IndexedSeq](Map(Record.s -> IndexedSeq("foo")))
        val metadata2 = metadata.removeAll(Record.s)
        assert(metadata.getAll(Record.s).length)(equalTo(1)) &&
        assert(metadata2.getAll(Record.s).length)(equalTo(0))
      }
    }
  )
}
