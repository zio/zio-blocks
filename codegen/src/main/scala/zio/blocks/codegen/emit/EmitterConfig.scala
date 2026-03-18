package zio.blocks.codegen.emit

/**
 * Configuration for the Scala code emitter.
 *
 * @param indentWidth
 *   Number of spaces per indentation level (default: 2)
 * @param sortImports
 *   Whether to sort imports alphabetically (default: true)
 * @param scala3Syntax
 *   Whether to use Scala 3 syntax features (default: true)
 * @param trailingCommas
 *   Whether to use trailing commas in multi-line constructs (default: true)
 */
final case class EmitterConfig(
  indentWidth: Int = 2,
  sortImports: Boolean = true,
  scala3Syntax: Boolean = true,
  trailingCommas: Boolean = true
)

object EmitterConfig {
  val default: EmitterConfig = EmitterConfig()
  val scala2: EmitterConfig  = EmitterConfig(scala3Syntax = false, trailingCommas = false)
}
