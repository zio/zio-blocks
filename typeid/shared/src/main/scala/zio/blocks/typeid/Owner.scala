package zio.blocks.typeid

/**
 * Represents where a type is defined in the source code.
 * 
 * An Owner consists of path segments that can be packages, terms (objects/vals),
 * or types (classes/traits).
 */
final case class Owner(segments: List[Owner.Segment]) {
  
  /** String representation of the owner path, e.g. "zio.blocks.schema" */
  def asString: String = segments.map(_.name).mkString(".")
  
  /** Append a package segment */
  def /(pkg: String): Owner = Owner(segments :+ Owner.Package(pkg))
  
  /** Append a term segment */
  def term(name: String): Owner = Owner(segments :+ Owner.Term(name))
  
  /** Append a type segment */
  def tpe(name: String): Owner = Owner(segments :+ Owner.Type(name))
  
  /** Check if this owner is empty (root) */
  def isEmpty: Boolean = segments.isEmpty
  
  /** Check if this owner is non-empty */
  def nonEmpty: Boolean = segments.nonEmpty
}

object Owner {
  
  /** A segment of an owner path */
  sealed trait Segment { 
    def name: String 
  }
  
  /** A package in the owner path */
  final case class Package(name: String) extends Segment
  
  /** A term (object, val) in the owner path */
  final case class Term(name: String) extends Segment
  
  /** A type (class, trait) in the owner path */
  final case class Type(name: String) extends Segment
  
  /** The root owner (empty path) */
  val Root: Owner = Owner(Nil)
  
  /** Create an owner from package names */
  def apply(packages: String*): Owner = Owner(packages.toList.map(Package(_)))
  
  // Common namespaces
  val scala: Owner = Owner("scala")
  val scalaCollectionImmutable: Owner = Owner("scala", "collection", "immutable")
  val javaLang: Owner = Owner("java", "lang")
  val javaTime: Owner = Owner("java", "time")
  val javaUtil: Owner = Owner("java", "util")
  val zioBlocksTypeid: Owner = Owner("zio", "blocks", "typeid")
  val zioBlocksSchema: Owner = Owner("zio", "blocks", "schema")
}
