package zio.blocks.typeid

/**
 * Represents the ownership chain of a type - where it is defined in the codebase.
 * 
 * An `Owner` captures the full path from the root package to the type's containing scope,
 * distinguishing between package segments, term segments (objects/vals), and type segments
 * (classes/traits containing nested types).
 * 
 * == Example ==
 * For a type `com.example.Outer.Inner`:
 * {{{
 * Owner(List(
 *   Owner.Package("com"),
 *   Owner.Package("example"),
 *   Owner.Type("Outer")
 * ))
 * }}}
 * 
 * @param segments The ordered list of segments from root to the type's container
 */
final case class Owner(segments: List[Owner.Segment]) { self =>

  /**
   * Returns the fully qualified path as a dot-separated string.
   * 
   * @return The owner path as a string, e.g., "com.example.Outer"
   */
  def asString: String = 
    if (segments.isEmpty) ""
    else segments.map(_.name).mkString(".")

  /**
   * Combines this owner with another segment to create a deeper ownership path.
   * 
   * @param segment The segment to append
   * @return A new Owner with the segment appended
   */
  def /(segment: Owner.Segment): Owner = Owner(segments :+ segment)

  /**
   * Appends a package segment to this owner.
   * 
   * @param name The package name
   * @return A new Owner with the package appended
   */
  def pkg(name: String): Owner = self / Owner.Package(name)

  /**
   * Appends a term (object/val) segment to this owner.
   * 
   * @param name The term name
   * @return A new Owner with the term appended
   */
  def term(name: String): Owner = self / Owner.Term(name)

  /**
   * Appends a type (class/trait) segment to this owner.
   * 
   * @param name The type name
   * @return A new Owner with the type appended
   */
  def tpe(name: String): Owner = self / Owner.Type(name)

  /**
   * Returns just the package segments of this owner.
   */
  def packages: List[Owner.Package] = segments.collect { case p: Owner.Package => p }

  /**
   * Returns the non-package segments (terms and types).
   */
  def values: List[Owner.Segment] = segments.filterNot(_.isInstanceOf[Owner.Package])

  /**
   * Returns true if this owner represents the root (empty) scope.
   */
  def isRoot: Boolean = segments.isEmpty

  override def toString: String = s"Owner($asString)"
}

object Owner {
  
  /**
   * A segment in the ownership chain.
   */
  sealed trait Segment extends Product with Serializable {
    /** The name of this segment */
    def name: String
    
    /** The kind of segment for debugging/serialization */
    def kind: String
  }

  /**
   * A package segment in the ownership chain.
   * 
   * @param name The package name (e.g., "scala", "zio")
   */
  final case class Package(name: String) extends Segment {
    def kind: String = "package"
  }

  /**
   * A term (object, val, or def) segment in the ownership chain.
   * Used for types defined inside objects or as path-dependent types.
   * 
   * @param name The term name (e.g., "Companion", "instance")
   */
  final case class Term(name: String) extends Segment {
    def kind: String = "term"
  }

  /**
   * A type (class or trait) segment in the ownership chain.
   * Used for nested types defined inside other types.
   * 
   * @param name The type name (e.g., "Outer", "Container")
   */
  final case class Type(name: String) extends Segment {
    def kind: String = "type"
  }

  /**
   * The root owner, representing the top-level (no containing scope).
   */
  val Root: Owner = Owner(Nil)

  /**
   * Creates an Owner from a sequence of package names.
   * 
   * @param packages The package path
   * @return An Owner with only package segments
   */
  def fromPackages(packages: String*): Owner = 
    Owner(packages.toList.map(Package(_)))

  /**
   * Creates an Owner from a dot-separated package string.
   * 
   * @param path The dot-separated path (e.g., "com.example.app")
   * @return An Owner with package segments
   */
  def fromString(path: String): Owner =
    if (path.isEmpty) Root
    else Owner(path.split('.').toList.map(Package(_)))

  // Common built-in owners
  val scala: Owner                    = fromPackages("scala")
  val scalaCollection: Owner          = fromPackages("scala", "collection")
  val scalaCollectionImmutable: Owner = fromPackages("scala", "collection", "immutable")
  val scalaCollectionMutable: Owner   = fromPackages("scala", "collection", "mutable")
  val javaLang: Owner                 = fromPackages("java", "lang")
  val javaUtil: Owner                 = fromPackages("java", "util")
  val javaTime: Owner                 = fromPackages("java", "time")
  val zioBlocksSchema: Owner          = fromPackages("zio", "blocks", "schema")
  val zioBlocksTypeid: Owner          = fromPackages("zio", "blocks", "typeid")
}
