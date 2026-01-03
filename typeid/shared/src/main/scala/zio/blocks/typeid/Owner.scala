package zio.blocks.typeid

/**
 * Represents where a type is defined in the Scala/Java ecosystem.
 *
 * An Owner encapsulates the hierarchical ownership chain of a type, which can
 * include packages, terms (objects/values), and types.
 *
 * For example, for a type `MyClass` defined as:
 * {{{
 * package com.example
 * object Outer {
 *   class MyClass
 * }
 * }}}
 *
 * The owner would be:
 * {{{
 * Owner(List(
 *   Owner.Package("com"),
 *   Owner.Package("example"),
 *   Owner.Term("Outer")
 * ))
 * }}}
 */
final case class Owner(segments: List[Owner.Segment]) {

  /**
   * Returns the owner path as a dot-separated string.
   */
  def asString: String = segments.map(_.name).mkString(".")

  /**
   * Appends a package segment to this owner.
   */
  def /(pkg: String): Owner = Owner(segments :+ Owner.Package(pkg))

  /**
   * Appends a term segment to this owner.
   */
  def term(name: String): Owner = Owner(segments :+ Owner.Term(name))

  /**
   * Appends a type segment to this owner.
   */
  def tpe(name: String): Owner = Owner(segments :+ Owner.Type(name))

  /**
   * Returns true if this owner represents the root (empty) owner.
   */
  def isRoot: Boolean = segments.isEmpty

  /**
   * Returns the parent owner, or Root if this is already the root.
   */
  def parent: Owner =
    if (segments.isEmpty) Owner.Root
    else Owner(segments.init)

  /**
   * Returns the last segment's name, or empty string if root.
   */
  def lastName: String =
    segments.lastOption.map(_.name).getOrElse("")
}

object Owner {

  /**
   * A segment in the ownership chain.
   */
  sealed trait Segment {
    def name: String
  }

  /**
   * A package segment (e.g., `com`, `example`).
   */
  final case class Package(name: String) extends Segment

  /**
   * A term segment (e.g., an object or value).
   */
  final case class Term(name: String) extends Segment

  /**
   * A type segment (e.g., an enclosing class or trait).
   */
  final case class Type(name: String) extends Segment

  /**
   * The root owner (no segments).
   */
  val Root: Owner = Owner(Nil)

  /**
   * Creates an Owner from a dot-separated package path. All segments are
   * treated as packages.
   */
  def fromPackagePath(path: String): Owner =
    if (path.isEmpty) Root
    else Owner(path.split("\\.").toList.map(Package.apply))

  // Common namespaces
  private[typeid] val scala: Owner                    = fromPackagePath("scala")
  private[typeid] val scalaCollectionImmutable: Owner = fromPackagePath("scala.collection.immutable")
  private[typeid] val javaLang: Owner                 = fromPackagePath("java.lang")
  private[typeid] val javaTime: Owner                 = fromPackagePath("java.time")
  private[typeid] val javaUtil: Owner                 = fromPackagePath("java.util")
}
