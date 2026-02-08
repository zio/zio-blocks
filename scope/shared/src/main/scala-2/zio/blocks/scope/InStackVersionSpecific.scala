package zio.blocks.scope

/**
 * Scala 2 version-specific InStack support.
 *
 * In the new Scope design, InStack is no longer needed as the HList-based scope
 * is replaced with a two-parameter Scope[ParentTag, Tag].
 *
 * This trait is kept empty for binary compatibility.
 */
private[scope] trait InStackVersionSpecific
