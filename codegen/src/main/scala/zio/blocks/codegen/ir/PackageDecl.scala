package zio.blocks.codegen.ir

/**
 * Represents a Scala package declaration in the IR.
 *
 * @param path
 *   The fully qualified package path (e.g., "com.example")
 *
 * @example
 *   {{{
 * val pkg = PackageDecl("com.example")
 * // Represents: package com.example
 *   }}}
 */
final case class PackageDecl(path: String)
