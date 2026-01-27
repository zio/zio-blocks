package zio.blocks.schema

import zio.test.Assertion._
import zio.test._

object SchemaMetadataSpec extends SchemaBaseSpec {
  case class Record(s: String, i: Int)

  object Record extends CompanionOptics[Record] {
    implicit val schema: Schema[Record] = Schema.derived
    val s: Lens[Record, String]         = optic(_.s)
    val i: Lens[Record, Int]            = optic(_.i)
  }

  def spec: Spec[TestEnvironment, Any] = suite("SchemaMetadataSpec")(
    suite("SchemaMetadata operations")(
      test("reports accurate size for empty metadata") {
        val metadata = SchemaMetadata.empty[Record, IndexedSeq]
        assert(metadata.size)(equalTo(0))
      },
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
      test("get returns first value if present") {
        val metadata = SchemaMetadata[Record, IndexedSeq](Map(Record.s -> IndexedSeq("first")))
          .add(Record.s, IndexedSeq("second"))
        assert(metadata.get(Record.s))(isSome(equalTo("first")))
      },
      test("get returns None if not present") {
        val metadata = SchemaMetadata.empty[Record, IndexedSeq]
        assert(metadata.get(Record.s))(isNone)
      },
      test("getAll returns empty IndexedSeq if not present") {
        val metadata = SchemaMetadata.empty[Record, IndexedSeq]
        assert(metadata.getAll(Record.s))(equalTo(IndexedSeq.empty))
      },
      test("add updates existing key") {
        val metadata = SchemaMetadata
          .empty[Record, IndexedSeq]
          .add(Record.s, IndexedSeq("first"))
          .add(Record.s, IndexedSeq("second"))
        assert(metadata.getAll(Record.s))(equalTo(IndexedSeq(IndexedSeq("first"), IndexedSeq("second"))))
      },
      test("add works for multiple different keys") {
        val metadata = SchemaMetadata
          .empty[Record, IndexedSeq]
          .add(Record.s, IndexedSeq("string-value"))
          .add(Record.i, IndexedSeq(42))
        assert(metadata.size)(equalTo(2)) &&
        assert(metadata.get(Record.s))(isSome(equalTo(IndexedSeq("string-value")))) &&
        assert(metadata.get(Record.i))(isSome(equalTo(IndexedSeq(42))))
      },
      test("removeAll only removes specified key") {
        val metadata = SchemaMetadata
          .empty[Record, IndexedSeq]
          .add(Record.s, IndexedSeq("string-value"))
          .add(Record.i, IndexedSeq(42))
          .removeAll(Record.s)
        assert(metadata.size)(equalTo(1)) &&
        assert(metadata.get(Record.s))(isNone) &&
        assert(metadata.get(Record.i))(isSome(equalTo(IndexedSeq(42))))
      }
    ),
    suite("Folder operations")(
      test("fold iterates over all metadata entries") {
        val metadata = SchemaMetadata
          .empty[Record, IndexedSeq]
          .add(Record.s, IndexedSeq("foo"))
          .add(Record.s, IndexedSeq("bar"))
          .add(Record.i, IndexedSeq(42))

        val result = metadata.fold(0)(new SchemaMetadata.Folder[Record, IndexedSeq, Int] {
          def initial: Int                                                        = 0
          def fold[A](z: Int, optic: Optic[Record, A], value: IndexedSeq[A]): Int = z + 1
        })
        assert(result)(equalTo(3))
      },
      test("fold can aggregate values") {
        type Id[A] = A
        val metadata = SchemaMetadata
          .simple[Record]
          .add(Record.s, "foo")
          .add(Record.s, "bar")
          .add(Record.s, "baz")

        val result = metadata.fold("")(new SchemaMetadata.Folder[Record, Id, String] {
          def initial: String                                               = ""
          def fold[A](z: String, optic: Optic[Record, A], value: A): String =
            if (z.isEmpty) value.toString else s"$z,$value"
        })
        assert(result)(equalTo("foo,bar,baz"))
      },
      test("Folder.simple creates a simple folder ignoring values") {
        val metadata = SchemaMetadata
          .simple[Record]
          .add(Record.s, "foo")
          .add(Record.i, 42)

        val folder = SchemaMetadata.Folder.simple[Record, Int](0)((z, _) => z + 1)
        val result = metadata.fold(folder.initial)(folder)
        assert(result)(equalTo(2))
      }
    ),
    suite("Factory methods")(
      test("empty creates empty metadata") {
        val metadata = SchemaMetadata.empty[Record, IndexedSeq]
        assert(metadata.size)(equalTo(0))
      },
      test("bound creates empty metadata") {
        val metadata = SchemaMetadata.bound[Record, IndexedSeq]
        assert(metadata.size)(equalTo(0))
      },
      test("simple creates empty simple metadata") {
        val metadata = SchemaMetadata.simple[Record]
        assert(metadata.size)(equalTo(0))
      }
    )
  )
}
