package zio.blocks.schema

/**
 * TOON (Token-Oriented Object Notation) codec support for ZIO Schema.
 *
 * TOON is a compact, human-readable encoding of the JSON data model that
 * minimizes tokens while preserving structure. It achieves 30-60% token
 * reduction compared to JSON, making it ideal for LLM prompts.
 *
 * ==Quick Start==
 *
 * {{{
 * import zio.blocks.schema._
 * import zio.blocks.schema.toon._
 *
 * case class User(id: Int, name: String)
 * object User {
 *   implicit val schema: Schema[User] = DeriveSchema.gen[User]
 * }
 *
 * // Derive codec from schema
 * val codec = ToonFormat.codec[User]
 *
 * // Encode to TOON
 * val toon: String = codec.encodeToString(User(1, "Alice"))
 * // Result: id: 1\nname: Alice
 *
 * // Decode from TOON
 * val user: User = codec.decode("id: 1\nname: Alice")
 * }}}
 *
 * ==Array Formats==
 *
 * TOON supports three array formats, selected automatically or configured:
 *
 *   - '''Inline''': For primitive arrays: `tags[3]: a,b,c`
 *   - '''Tabular''': For uniform object arrays: `users[2]{id,name}: 1,Alice /
 *     2,Bob`
 *   - '''List''': For mixed/nested arrays: `items[3]: / - item1 / - item2`
 *
 * ==Configuration==
 *
 * Use [[WriterConfig]] to customize output format and [[ReaderConfig]] for
 * parsing behavior and security limits.
 *
 * @see
 *   [[https://github.com/toon-format/spec TOON Specification v1.4]]
 * @see
 *   [[https://toonformat.dev/ TOON Official Website]]
 */
package object toon
