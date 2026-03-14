package zio.blocks.codegen.ir

/**
 * Represents a Scala annotation in the IR.
 *
 * @param name
 *   The name of the annotation (e.g., "deprecated", "required")
 * @param args
 *   List of annotation arguments as (name, value) pairs (defaults to empty
 *   list)
 *
 * @example
 *   {{{
 * // Annotation without arguments
 * val required = Annotation("required")
 *
 * // Annotation with arguments
 * val deprecated = Annotation("deprecated", List(("message", "\"use v2\"")))
 *   }}}
 */
final case class Annotation(
  name: String,
  args: List[(String, String)] = Nil
)
