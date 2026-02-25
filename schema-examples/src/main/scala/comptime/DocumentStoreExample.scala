package comptime

import zio.blocks.schema._
import zio.blocks.schema.comptime.Allows
import Allows.{Primitive, Record, Sequence, Variant, `|`}
import Allows.{Map => AMap, Optional => AOptional, Self => ASelf}

// ---------------------------------------------------------------------------
// Realistic JSON document-store example using Allows[A, S]
//
// A JSON document store can represent any value that maps to the JSON model:
//   - Primitives (string, number, boolean, null)
//   - Records (JSON objects)
//   - Sequences (JSON arrays)
//   - Maps with string keys (JSON objects used as dictionaries)
//   - Optional values (absent fields)
//   - Recursive structures (nested documents)
//
// The Allows grammar that captures this is:
//   Record[Primitive | Optional[Self] | Sequence[Self] | Map[Primitive, Self]]
//
// The `Self` node allows arbitrary nesting depth. DynamicValue is excluded.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Domain types
// ---------------------------------------------------------------------------

// Compatible: flat document
case class Author(name: String, email: String)
object Author { implicit val schema: Schema[Author] = Schema.derived }

// Compatible: document with nested documents (Self allows this)
case class BookChapter(title: String, wordCount: Int)
object BookChapter { implicit val schema: Schema[BookChapter] = Schema.derived }

case class Book(
  id: java.util.UUID,
  title: String,
  author: Author,                                          // nested document — via Self
  chapters: List[BookChapter],                             // sequence of documents — via Self
  tags: List[String],                                      // sequence of primitives — via Self
  metadata: scala.collection.immutable.Map[String, String] // string map — via Self
)
object Book { implicit val schema: Schema[Book] = Schema.derived }

// Compatible: recursive document (tree)
case class Category(name: String, subcategories: List[Category])
object Category { implicit val schema: Schema[Category] = Schema.derived }

// Compatible: variant of search result types
sealed trait SearchResult
case class BookResult(title: String, score: Double)   extends SearchResult
case class AuthorResult(name: String, bookCount: Int) extends SearchResult
object SearchResult { implicit val schema: Schema[SearchResult] = Schema.derived }

// Incompatible: contains DynamicValue (not in the document grammar)
case class UntypedDocument(id: String, payload: DynamicValue)
object UntypedDocument { implicit val schema: Schema[UntypedDocument] = Schema.derived }

// ---------------------------------------------------------------------------
// Document store library API
// ---------------------------------------------------------------------------

object DocumentStore {

  /**
   * The grammar of a valid JSON document: a record whose fields are either
   * primitive values, optional nested documents, sequences of nested documents,
   * or string-keyed maps of nested documents. Recursive via Self.
   */
  // A JSON document field may be:
  //   - A primitive scalar
  //   - A nested document (another record satisfying this grammar)
  //   - An optional primitive or nested document
  //   - A sequence of primitives or nested documents
  //   - A map from primitive keys to primitive values or nested documents
  // The `Self` node allows arbitrary recursive nesting at any position.
  type JsonDocument =
    Record[
      Primitive | ASelf | AOptional[Primitive | ASelf] | Sequence[Primitive | ASelf] |
        AMap[Primitive, Primitive | ASelf]
    ]

  /**
   * Convert a document to its JSON representation.
   *
   * The type must satisfy the JsonDocument grammar — a record (not a bare
   * primitive or sequence) whose fields recursively satisfy the same grammar.
   */
  def toJson[A: Schema](doc: A)(implicit ev: Allows[A, JsonDocument]): String =
    Schema[A].toDynamicValue(doc).toJson.toString

  /** Serialize a document to a DynamicValue for further processing. */
  def serialize[A: Schema](doc: A)(implicit ev: Allows[A, JsonDocument]): DynamicValue =
    Schema[A].toDynamicValue(doc)

  /** Index a search result. The type must be a variant of flat record cases. */
  def index[A: Schema](result: A)(implicit
    ev: Allows[A, Variant[Record[Primitive | AOptional[Primitive] | Sequence[Primitive]]]]
  ): String = {
    val typeName = Schema[A].toDynamicValue(result) match {
      case DynamicValue.Variant(name, _) => name
      case _                             => Schema[A].reflect.typeId.name
    }
    val payload = Schema[A].toDynamicValue(result).toJson.toString
    s"""{"_type":"$typeName","_doc":$payload}"""
  }
}

// ---------------------------------------------------------------------------
// Demonstration
// ---------------------------------------------------------------------------

object DocumentStoreDemo {

  val bookJson: String = DocumentStore.toJson(
    Book(
      new java.util.UUID(0, 0),
      "ZIO Blocks",
      Author("John", "john@example.com"),
      List(BookChapter("Intro", 1000)),
      List("scala", "zio"),
      scala.collection.immutable.Map("version" -> "2.0")
    )
  )

  val categoryJson: String = DocumentStore.toJson(
    Category("Programming", List(Category("Scala", Nil)))
  )

  val indexed: String = DocumentStore.index[SearchResult](BookResult("ZIO Blocks", 0.99))

  // The following would NOT compile — uncomment to see the error:
  //
  // DocumentStore.toJson(UntypedDocument("x", DynamicValue.Null))
  //   [error] Schema shape violation at UntypedDocument.payload: found Dynamic,
  //           required Primitive | Optional[Self] | Sequence[Self] | Map[Primitive, Self]
  //
  // DocumentStore.index[Author](Author("Alice", "alice@example.com"))
  //   [error] Schema shape violation at Author: found Record(Author),
  //           required Variant[...]
}
