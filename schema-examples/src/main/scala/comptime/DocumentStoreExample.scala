package comptime

import zio.blocks.schema._
import zio.blocks.schema.comptime.Allows
import Allows.{Record, Sequence, `|`}
import Allows.{Optional => AOptional, Self => ASelf}

// ---------------------------------------------------------------------------
// Realistic JSON document-store example using Allows[A, S]
//
// JSON has a limited primitive value set:
//   - null    → Unit / Option
//   - boolean → Boolean
//   - number  → Int, Long, Float, Double, BigDecimal, BigInt
//   - string  → String
//
// Notably absent from JSON: Char, Byte, Short, UUID, Currency,
// and ALL java.time.* types. A JSON library should use SPECIFIC primitive
// nodes to reject non-JSON scalars at the call site.
//
// The grammar is simply:
//   type Json = Record[JsonPrimitive | Self] | Sequence[JsonPrimitive | Self]
//
// Self handles all nesting: a field may be a primitive or another Json value.
// Sequences of primitives (List[String]) and sequences of records
// (List[Author]) both satisfy Sequence[JsonPrimitive | Self].
// Optional fields (Option[X]) satisfy Record[...] because Option is recognised
// by the Optional grammar node which falls through to the field constraint.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Domain types
// ---------------------------------------------------------------------------

// Compatible: flat document
case class Author(name: String, email: String)
object Author { implicit val schema: Schema[Author] = Schema.derived }

// Compatible: document with nested documents and sequences
case class BookChapter(title: String, wordCount: Int)
object BookChapter { implicit val schema: Schema[BookChapter] = Schema.derived }

case class Book(
  title: String,
  author: Author,              // nested record — satisfied via Self
  chapters: List[BookChapter], // sequence of records — satisfied via Sequence[Self]
  tags: List[String],          // sequence of primitives — satisfied via Sequence[JsonPrimitive]
  rating: Option[Double]       // optional primitive — satisfied via Optional[JsonPrimitive | Self]
)
object Book { implicit val schema: Schema[Book] = Schema.derived }

// Compatible: recursive document
case class Category(name: String, subcategories: List[Category])
object Category { implicit val schema: Schema[Category] = Schema.derived }

// Compatible: variant of search results (for indexing)
sealed trait SearchResult
case class BookResult(title: String, score: Double)   extends SearchResult
case class AuthorResult(name: String, bookCount: Int) extends SearchResult
object SearchResult { implicit val schema: Schema[SearchResult] = Schema.derived }

// INCOMPATIBLE: UUID is not a JSON-native scalar
case class WithUUID(id: java.util.UUID, name: String)
object WithUUID { implicit val schema: Schema[WithUUID] = Schema.derived }

// INCOMPATIBLE: Instant is not a JSON-native scalar
case class WithTimestamp(name: String, createdAt: java.time.Instant)
object WithTimestamp { implicit val schema: Schema[WithTimestamp] = Schema.derived }

// ---------------------------------------------------------------------------
// Document store library API
// ---------------------------------------------------------------------------

object DocumentStore {

  /**
   * JSON-representable scalar types.
   *
   * Excludes Char, Byte, Short, UUID, Currency, and all java.time.* types —
   * none of these have a native JSON encoding. Authors who need java.time
   * values in JSON should store them as Primitive.String (ISO-8601 etc.).
   */
  type JsonPrimitive =
    Allows.Primitive.Boolean | Allows.Primitive.Int | Allows.Primitive.Long | Allows.Primitive.Float |
      Allows.Primitive.Double | Allows.Primitive.String | Allows.Primitive.BigDecimal | Allows.Primitive.BigInt |
      Allows.Primitive.Unit

  /**
   * A JSON value is either a JSON object (Record) or a JSON array (Sequence).
   * Self recurses back to this same grammar, so nesting works at any depth.
   * Optional covers nullable fields (JSON null / absent key).
   */
  type Json = Record[JsonPrimitive | AOptional[JsonPrimitive | ASelf] | ASelf] | Sequence[JsonPrimitive | ASelf]

  /**
   * Encode a value to its JSON string representation.
   *
   * Accepts both JSON objects (records) and JSON arrays (sequences) at the top
   * level. Fields may be primitives, nested documents, sequences, or optionals
   * — all handled via Self and the JsonPrimitive constraint. Types containing
   * UUID, Instant, Char etc. are rejected at compile time.
   */
  def toJson[A: Schema](doc: A)(implicit ev: Allows[A, Json]): String =
    Schema[A].toDynamicValue(doc).toJson.toString

  /** Serialize to DynamicValue for further processing. */
  def serialize[A: Schema](doc: A)(implicit ev: Allows[A, Json]): DynamicValue =
    Schema[A].toDynamicValue(doc)

  /**
   * Index a search result. The type must be a sealed trait of flat JSON
   * records. No explicit Variant node needed — sealed traits are
   * auto-unwrapped.
   */
  def index[A: Schema](result: A)(implicit
    ev: Allows[A, Record[JsonPrimitive | AOptional[JsonPrimitive | ASelf] | Sequence[JsonPrimitive | ASelf]]]
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

  // JSON objects compile fine
  val bookJson: String = DocumentStore.toJson(
    Book(
      "ZIO Blocks",
      Author("John", "john@example.com"),
      List(BookChapter("Intro", 1000)),
      List("scala", "zio"),
      Some(4.9)
    )
  )

  val categoryJson: String = DocumentStore.toJson(
    Category("Programming", List(Category("Scala", Nil)))
  )

  // JSON arrays also satisfy Json (top-level Sequence)
  val tagListJson: String    = DocumentStore.toJson(List("scala", "zio", "functional"))
  val authorListJson: String = DocumentStore.toJson(List(Author("Alice", "a@b.com"), Author("Bob", "b@b.com")))

  val indexed: String = DocumentStore.index[SearchResult](BookResult("ZIO Blocks", 0.99))

  // The following would NOT compile — uncomment to see the errors:
  //
  // DocumentStore.toJson(WithUUID(new java.util.UUID(0, 0), "Alice"))
  //   [error] Schema shape violation at WithUUID.id: found Primitive(java.util.UUID),
  //           required JsonPrimitive (Boolean | Int | Long | Float | Double | String | ...)
  //   UUID is not a JSON-native type — encode it as Primitive.String.
  //
  // DocumentStore.toJson(WithTimestamp("Alice", java.time.Instant.EPOCH))
  //   [error] Schema shape violation at WithTimestamp.createdAt: found Primitive(java.time.Instant)
  //   Instant is not a JSON-native type — encode it as Primitive.String (ISO-8601).
}
