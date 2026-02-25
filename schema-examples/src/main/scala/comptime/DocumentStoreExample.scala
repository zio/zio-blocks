package comptime

import zio.blocks.schema._
import zio.blocks.schema.comptime.Allows
import Allows.{Record, Sequence, Variant, `|`}
import Allows.{Map => AMap, Optional => AOptional, Self => ASelf}

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
// and ALL java.time.* types. A JSON library that accepts Schema[A] should
// use SPECIFIC primitive nodes to enforce that user types only contain
// JSON-representable scalars, rather than the catch-all Primitive.
//
// The Allows grammar that captures a valid JSON document type is:
//   JsonPrimitive = Primitive.Boolean | Primitive.Int | Primitive.Long |
//                   Primitive.Float   | Primitive.Double | Primitive.String |
//                   Primitive.BigDecimal | Primitive.BigInt | Primitive.Unit
//
//   JsonDocument  = Record[JsonPrimitive | Self | Optional[JsonPrimitive | Self] |
//                          Sequence[JsonPrimitive | Self] | Map[Primitive.String, JsonPrimitive | Self]]
//
// The `Self` node allows arbitrary recursive nesting. DynamicValue is excluded.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Domain types
// ---------------------------------------------------------------------------

// Compatible: flat document using only JSON-safe primitives
case class Author(name: String, email: String)
object Author { implicit val schema: Schema[Author] = Schema.derived }

// Compatible: document with nested documents (Self allows this)
case class BookChapter(title: String, wordCount: Int)
object BookChapter { implicit val schema: Schema[BookChapter] = Schema.derived }

case class Book(
  title: String,
  author: Author,
  chapters: List[BookChapter],
  tags: List[String],
  metadata: scala.collection.immutable.Map[String, String]
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

// INCOMPATIBLE: contains UUID — not a JSON-native type
case class WithUUID(id: java.util.UUID, name: String)
object WithUUID { implicit val schema: Schema[WithUUID] = Schema.derived }

// INCOMPATIBLE: contains Instant — not JSON-native (would need to be String-encoded)
case class WithTimestamp(name: String, createdAt: java.time.Instant)
object WithTimestamp { implicit val schema: Schema[WithTimestamp] = Schema.derived }

// INCOMPATIBLE: DynamicValue
case class UntypedDoc(id: String, payload: DynamicValue)
object UntypedDoc { implicit val schema: Schema[UntypedDoc] = Schema.derived }

// ---------------------------------------------------------------------------
// Document store library API
// ---------------------------------------------------------------------------

object DocumentStore {

  /**
   * JSON-representable primitive scalar types.
   *
   * Deliberately excludes Char, Byte, Short, UUID, Currency, and all
   * java.time.* types because JSON has no native encoding for them. Library
   * authors wishing to support java.time via ISO-8601 strings would substitute
   * Primitive.String for those fields.
   */
  type JsonPrimitive =
    Allows.Primitive.Boolean | Allows.Primitive.Int | Allows.Primitive.Long | Allows.Primitive.Float |
      Allows.Primitive.Double | Allows.Primitive.String | Allows.Primitive.BigDecimal | Allows.Primitive.BigInt |
      Allows.Primitive.Unit

  /**
   * A valid JSON document field may be:
   *   - A JSON-safe primitive scalar
   *   - A nested document (Self)
   *   - An optional primitive or nested document
   *   - A sequence of primitives or nested documents
   *   - A map from string keys to primitives or nested documents
   *
   * The `Self` node allows arbitrary recursive nesting depth.
   */
  type JsonDocument =
    Record[
      JsonPrimitive | ASelf | AOptional[JsonPrimitive | ASelf] | Sequence[JsonPrimitive | ASelf] |
        AMap[Allows.Primitive.String, JsonPrimitive | ASelf]
    ]

  /**
   * Convert a document to its JSON string representation.
   *
   * Requires that `A` satisfies the `JsonDocument` grammar — a record whose
   * fields contain only JSON-representable primitives, nested documents,
   * sequences, or string-keyed maps. Types containing UUID, Instant, etc. are
   * rejected at compile time with a precise error message.
   */
  def toJson[A: Schema](doc: A)(implicit ev: Allows[A, JsonDocument]): String =
    Schema[A].toDynamicValue(doc).toJson.toString

  /** Serialize a document to a DynamicValue for further processing. */
  def serialize[A: Schema](doc: A)(implicit ev: Allows[A, JsonDocument]): DynamicValue =
    Schema[A].toDynamicValue(doc)

  /**
   * Index a search result. The type must be a variant of flat JSON record
   * cases.
   */
  def index[A: Schema](result: A)(implicit
    ev: Allows[A, Variant[Record[JsonPrimitive | AOptional[JsonPrimitive] | Sequence[JsonPrimitive]]]]
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

  // All of these compile fine — fields use only JSON-native types
  val bookJson: String = DocumentStore.toJson(
    Book(
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

  // The following would NOT compile — uncomment to see the errors:
  //
  // DocumentStore.toJson(WithUUID(new java.util.UUID(0, 0), "Alice"))
  //   [error] Schema shape violation at WithUUID.id: found Primitive(java.util.UUID),
  //           required Boolean | Int | Long | Float | Double | String | ...
  //   Hint: UUID is not a JSON-native type. Consider encoding it as Primitive.String.
  //
  // DocumentStore.toJson(WithTimestamp("Alice", java.time.Instant.EPOCH))
  //   [error] Schema shape violation at WithTimestamp.createdAt: found Primitive(java.time.Instant),
  //           required Boolean | Int | Long | Float | Double | String | ...
  //   Hint: Instant is not a JSON-native type. Consider encoding it as Primitive.String (ISO-8601).
  //
  // DocumentStore.toJson(UntypedDoc("x", DynamicValue.Null))
  //   [error] Schema shape violation at UntypedDoc.payload: found Dynamic,
  //           required Boolean | Int | Long | Float | Double | String | ...
}
