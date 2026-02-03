package zio.blocks.schema

import zio.test.Assertion._
import zio.test._

object SchemaMetadataSpec extends SchemaBaseSpec {
  case class Record(s: String)

  object Record extends CompanionOptics[Record] {
    implicit val schema: Schema[Record] = Schema.derived
    val s: Lens[Record, String]         = optic(_.s)
  }

  def spec: Spec[TestEnvironment, Any] = suite("SchemaMetadataSpec")(
    test("reports accurate size") {
      val metadata = SchemaMetadata[Record, IndexedSeq](Map(Record.s -> IndexedSeq("foo")))
      assert(metadata.size)(equalTo(1))
    },
    test("metadata can be added") {
      val metadata  = SchemaMetadata[Record, IndexedSeq](Map(Record.s -> IndexedSeq("foo")))
      val metadata2 = metadata.add(Record.s, IndexedSeq("bar"))
      assert(metadata.getAll(Record.s).length)(equalTo(1)) &&
      assert(metadata2.getAll(Record.s).length)(equalTo(2))
    },
    test("metadata can be removed") {
      val metadata  = SchemaMetadata[Record, IndexedSeq](Map(Record.s -> IndexedSeq("foo")))
      val metadata2 = metadata.removeAll(Record.s)
      assert(metadata.getAll(Record.s).length)(equalTo(1)) &&
      assert(metadata2.getAll(Record.s).length)(equalTo(0))
    },
    test("get returns first value") {
      val metadata = SchemaMetadata
        .empty[Record, IndexedSeq]
        .add(Record.s, IndexedSeq("first"))
        .add(Record.s, IndexedSeq("second"))
      assert(metadata.get(Record.s))(isSome(equalTo(IndexedSeq("first"))))
    },
    test("get returns None for missing optic") {
      val metadata = SchemaMetadata.empty[Record, IndexedSeq]
      assert(metadata.get(Record.s))(isNone)
    },
    test("fold iterates over all values") {
      val metadata = SchemaMetadata
        .empty[Record, IndexedSeq]
        .add(Record.s, IndexedSeq("a"))
        .add(Record.s, IndexedSeq("b"))
      val result = metadata.fold(List.empty[String]) {
        new SchemaMetadata.Folder[Record, IndexedSeq, List[String]] {
          def initial: List[String]                                                                 = List.empty
          def fold[A](z: List[String], optic: Optic[Record, A], value: IndexedSeq[A]): List[String] =
            z ++ value.asInstanceOf[IndexedSeq[String]].toList
        }
      }
      assert(result)(equalTo(List("a", "b")))
    },
    test("empty creates empty metadata") {
      val metadata = SchemaMetadata.empty[Record, IndexedSeq]
      assert(metadata.size)(equalTo(0))
    },
    test("simple creates empty Id metadata") {
      val metadata = SchemaMetadata.simple[Record]
      assert(metadata.size)(equalTo(0))
    },
    test("bound creates empty bounded metadata") {
      val metadata = SchemaMetadata.bound[Record, IndexedSeq]
      assert(metadata.size)(equalTo(0))
    },
    test("Folder.simple creates a simple folder") {
      val metadata = SchemaMetadata
        .simple[Record]
        .add(Record.s, "value1")
        .add(Record.s, "value2")
      val folder = SchemaMetadata.Folder.simple[Record, Int](0)((count, _) => count + 1)
      val result = metadata.fold(folder.initial)(folder)
      assert(result)(equalTo(2))
    }
  )
}
