package zio.blocks.schema.toon

import zio.blocks.schema.codec.BinaryFormat

/**
 * Entry point for TOON (Textual Object-Oriented Notation) format codec
 * derivation.
 *
 * TOON is a human-readable data format that emphasizes clarity and editability.
 * Unlike JSON, TOON uses indentation for structure and colons for key-value
 * pairs, making it more readable for complex nested structures.
 *
 * ==Basic Usage==
 *
 * {{{
 * import zio.blocks.schema.Schema
 * import zio.blocks.schema.toon.ToonFormat
 *
 * case class Person(name: String, age: Int)
 * object Person {
 *   implicit val schema: Schema[Person] = Schema.derived
 * }
 *
 * // Derive a codec
 * val codec = Person.schema.derive(ToonFormat.deriver)
 *
 * // Encode to TOON
 * val toon = codec.encodeToString(Person("Alice", 30))
 * // Result: "name: Alice\nage: 30"
 * }}}
 *
 * ==Configuring the Deriver==
 *
 * For custom configurations, use the `ToonBinaryCodecDeriver` builder methods:
 *
 * {{{
 * val customDeriver = ToonBinaryCodecDeriver
 *   .withFieldNameMapper(NameMapper.SnakeCase)
 *   .withArrayFormat(ArrayFormat.List)
 *   .withTransientNone(false)
 *
 * val codec = Person.schema.derive(customDeriver)
 * }}}
 *
 * ==TOON Format Syntax==
 *
 *   - '''Key-Value''': `fieldName: value`
 *   - '''Strings''': Unquoted if simple, quoted if containing special chars
 *   - '''Arrays (Inline)''': `[N]: val1,val2,val3`
 *   - '''Arrays (List)''': `[N]:\n  - item1\n  - item2`
 *   - '''Variants''': `CaseName:\n  field: value`
 *
 * @see
 *   [[ToonBinaryCodecDeriver]] for configuration options
 * @see
 *   [[ArrayFormat]] for array formatting options
 */
object ToonFormat extends BinaryFormat("application/toon", ToonBinaryCodecDeriver)
