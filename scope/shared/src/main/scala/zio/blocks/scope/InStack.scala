package zio.blocks.scope

// InStack is deprecated in the new Scope design.
// The HList-based Scope structure has been replaced with
// Scope[ParentTag, Tag]. Dependencies are now resolved via Factory/Wire,
// not via type-level stack search.
//
// This file is kept for source compatibility but the type is unused.

/**
 * @deprecated
 *   InStack is no longer used in the new Scope design. Dependencies are
 *   resolved via Factory and Wire, not via HList type-level search.
 */
@deprecated("InStack is no longer used in the new Scope design", "0.1.0")
trait InStack[-T, +Stack]

/**
 * @deprecated
 *   InStack is no longer used in the new Scope design.
 */
@deprecated("InStack is no longer used in the new Scope design", "0.1.0")
object InStack extends InStackVersionSpecific
