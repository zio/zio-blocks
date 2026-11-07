/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.typeid

/**
 * The place where a type is defined: the chain of packages, objects and
 * enclosing classes or traits around it, outermost first. [[TypeId#owner]]
 * returns it, and [[TypeId#fullName]] is the owner's [[asString]] followed by
 * the type's name.
 *
 * A package is a [[Owner.Package]] segment, an object is a [[Owner.Term]]
 * segment, and a class or trait is a [[Owner.Type]] segment. For a class
 * `MyClass` declared inside `object Outer` in package `com.example`, the owner
 * is `Owner(List(Package("com"), Package("example"), Term("Outer")))`. A class
 * declared inside a method body gets the owner of the method: the method itself
 * is not a segment.
 *
 * {{{
 * val outer = Owner.fromPackagePath("com.example").term("Outer")
 * outer.asString // "com.example.Outer"
 * outer.segments // List(Package(com), Package(example), Term(Outer))
 *
 * TypeId.of[List[Int]].owner.asString // "scala.collection.immutable"
 * }}}
 *
 * @param segments
 *   the segments of the path, outermost first; empty for [[Owner.Root]]
 * @see
 *   [[https://zioblocks.com/reference/typeid/#namespaces-and-owners Namespaces and Owners]]
 */
final case class Owner(segments: List[Owner.Segment]) {

  /**
   * Returns the segment names joined with dots, such as
   * `"scala.collection.immutable"`, or `""` for [[Owner.Root]]. Package, term
   * and type segments all print as their plain name.
   */
  def asString: String = segments
    .foldLeft(new java.lang.StringBuilder) { (sb, s) =>
      if (sb.length > 0) sb.append('.')
      sb.append(s.name)
    }
    .toString

  /**
   * Appends a package segment to this owner.
   */
  def /(pkg: String): Owner = new Owner(segments :+ new Owner.Package(pkg))

  /**
   * Appends a term segment to this owner.
   */
  def term(name: String): Owner = new Owner(segments :+ new Owner.Term(name))

  /**
   * Appends a type segment to this owner.
   */
  def tpe(name: String): Owner = new Owner(segments :+ new Owner.Type(name))

  /**
   * Returns `true` if this owner has no segments, as [[Owner.Root]] does.
   */
  def isRoot: Boolean = segments.isEmpty

  /**
   * Returns this owner without its last segment, or [[Owner.Root]] if this is
   * already the root.
   */
  def parent: Owner =
    if (segments.isEmpty) Owner.Root
    else new Owner(segments.init)

  /**
   * Returns the name of the last segment, or `""` for [[Owner.Root]].
   */
  def lastName: String = segments.lastOption match {
    case Some(s) => s.name
    case _       => ""
  }
}

/**
 * The segment cases of an [[Owner]] (`Package`, `Term` and `Type`), the empty
 * owner `Root`, and `fromPackagePath`, which builds an owner from a dotted
 * package path.
 */
object Owner {

  /**
   * A segment in the ownership chain.
   */
  sealed trait Segment {

    /**
     * The name of the package, object or type this segment stands for.
     */
    def name: String
  }

  /**
   * A package segment (e.g., `com`, `example`).
   */
  final case class Package(name: String) extends Segment

  /**
   * An object segment, such as `Outer` in `com.example.Outer.MyClass`.
   */
  final case class Term(name: String) extends Segment

  /**
   * A type segment (e.g., an enclosing class or trait).
   */
  final case class Type(name: String) extends Segment

  /**
   * The owner with no segments. Types declared in the empty package have it,
   * and so do the ids the Scala 3 macro derives for union and intersection
   * types.
   */
  val Root: Owner = new Owner(Nil)

  /**
   * Creates an owner from a dot-separated path, with every part as a
   * [[Owner.Package]] segment. An empty string gives [[Owner.Root]].
   */
  def fromPackagePath(path: String): Owner =
    if (path.isEmpty) Root
    else new Owner(path.split('.').map(Package.apply).toList)

  // Common namespaces
  private[typeid] val scala: Owner                    = fromPackagePath("scala")
  private[typeid] val scalaUtil: Owner                = fromPackagePath("scala.util")
  private[typeid] val scalaCollectionImmutable: Owner = fromPackagePath("scala.collection.immutable")
  private[typeid] val javaLang: Owner                 = fromPackagePath("java.lang")
  private[typeid] val javaTime: Owner                 = fromPackagePath("java.time")
  private[typeid] val javaUtil: Owner                 = fromPackagePath("java.util")
}
