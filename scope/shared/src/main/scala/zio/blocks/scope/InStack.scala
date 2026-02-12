package zio.blocks.scope

// InStack is deprecated in the new Scope design.
// The HList-based Scope structure has been replaced with
// Scope[ParentTag, Tag]. Dependencies are now resolved via Resource/Wire,
// not via type-level stack search.
//
// This file is kept for source compatibility but the type is unused.

/**
 * @tparam T
 *   the type to search for in the stack
 * @tparam Stack
 *   the HList-style stack to search within
 *
 * @deprecated
 *   InStack is no longer used in the new Scope design. Dependencies are
 *   resolved via Resource and Wire, not via HList type-level search.
 */
@deprecated("InStack is no longer used in the new Scope design", "0.1.0")
trait InStack[-T, +Stack]

/**
 * @deprecated
 *   InStack is no longer used in the new Scope design.
 */
@deprecated("InStack is no longer used in the new Scope design", "0.1.0")
object InStack extends InStackVersionSpecific
