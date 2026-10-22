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

package zio.blocks.schema

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.docs.{Doc, Paragraph, Inline}
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import zio.blocks.typeid.{Owner, TypeId, TypeParam, TypeRepr, Variance}
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.reflect.ClassTag
import scala.util.control.NonFatal

/**
 * A `Reflect[F, A]` is the tree that describes the structure of the type `A`.
 * Each node describes one type: it holds the type's `TypeId`, documentation,
 * modifiers, an optional default value and examples, and the child nodes for
 * its fields, cases, elements, keys or values. A [[Schema]] wraps one, which
 * `Schema#reflect` returns.
 *
 * There are eight kinds of node:
 *   - [[Reflect.Record]]: a product type, such as a case class, with a [[Term]]
 *     per field.
 *   - [[Reflect.Variant]]: a sum type, such as a sealed trait or an enum, with
 *     a [[Term]] per case.
 *   - [[Reflect.Sequence]]: a collection of elements, such as `List[A]` or
 *     `Set[A]`.
 *   - [[Reflect.Map]]: a collection of key-value pairs.
 *   - [[Reflect.Primitive]]: one of the scalar types listed in
 *     [[PrimitiveType]].
 *   - [[Reflect.Wrapper]]: a type that wraps another one, such as an opaque
 *     type or a type built with `Schema#transform`.
 *   - [[Reflect.Dynamic]]: a [[DynamicValue]] of any shape.
 *   - [[Reflect.Deferred]]: a node that is built on first use, which lets a
 *     recursive type refer to itself.
 *
 * `F` is what each node holds besides its structure. In a [[Reflect.Bound]],
 * where `F` is `Binding`, each node holds the functions that build and take
 * apart values of its type, so the tree can convert values to and from
 * [[DynamicValue]]; the methods that need them take an implicit
 * `HasBinding[F]`. In a [[Reflect.Unbound]], where `F` is `NoBinding`, the
 * nodes hold no functions; `noBinding` returns one.
 *
 * A reflect is immutable: `doc`, `modifier`, `defaultValue` and the other
 * setters return a copy. Two reflects are equal when their structure is equal;
 * bindings, default values and examples are not compared. `toString` prints the
 * tree in a schema definition language. A record, variant, sequence, map or
 * wrapper node that appears more than once, as in a recursive type, is printed
 * in full only the first time, and as `deferred =>` and its type name after
 * that; primitive and dynamic nodes are always printed in full.
 *
 * {{{
 * case class Book(title: String, year: Int)
 * object Book {
 *   implicit val schema: Schema[Book] = Schema.derived
 * }
 *
 * val reflect: Reflect.Bound[Book] = Book.schema.reflect
 * val kind       = reflect.nodeType                           // Record
 * val fieldNames = reflect.asRecord.map(_.fields.map(_.name)) // Some(Vector(title, year))
 * println(reflect)
 * // record Book {
 * //   title: String
 * //   year: Int
 * // }
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/reflect/ Reflect]]
 */
sealed trait Reflect[F[_, _], A] extends Reflectable[A] { self =>
  protected def inner: Any

  /** The type that this reflect describes. */
  final type Structure = A

  /**
   * The kind of binding this node holds, such as `BindingType.Record` for a
   * record. It fixes the types of `metadata` and `binding`.
   */
  type NodeBinding <: BindingType

  /**
   * Returns what this node holds besides its structure: a `Binding` in a bound
   * reflect, `NoBinding` in an unbound one.
   */
  def metadata: F[NodeBinding, A]

  /**
   * Returns this node as a [[Reflect.Dynamic]], or `None` for another kind of
   * node.
   */
  def asDynamic: Option[Reflect.Dynamic[F]] = None

  /**
   * Returns this node as a [[Reflect.Map]] typed with the key, value and map
   * types that [[IsMap]] gives for `A`, or `None` for another kind of node.
   */
  def asMap(implicit ev: IsMap[A]): Option[Reflect.Map[F, ev.Key, ev.Value, ev.Map]] = None

  /**
   * Returns this node as a map node whose key, value and map types are not
   * known statically, or `None` for another kind of node. Use it when `A` is
   * not known to be a map type.
   */
  def asMapUnknown: Option[Reflect.Map.Unknown[F]] = None

  /**
   * Returns this node as a [[Reflect.Primitive]], or `None` for another kind of
   * node.
   */
  def asPrimitive: Option[Reflect.Primitive[F, A]] = None

  /**
   * Returns this node as a [[Reflect.Record]], or `None` for another kind of
   * node.
   */
  def asRecord: Option[Reflect.Record[F, A]] = None

  /**
   * Returns this node as a [[Reflect.Sequence]] typed with the element and
   * collection types that [[IsCollection]] gives for `A`, or `None` for another
   * kind of node.
   */
  def asSequence(implicit ev: IsCollection[A]): Option[Reflect.Sequence[F, ev.Elem, ev.Collection]] = None

  /**
   * Returns this node as a sequence node whose element and collection types are
   * not known statically, or `None` for another kind of node. Use it when `A`
   * is not known to be a collection type.
   */
  def asSequenceUnknown: Option[Reflect.Sequence.Unknown[F]] = None

  /**
   * Returns a [[Term]] named `name` whose value is this node, to use as a field
   * or case of a record or variant of type `S`.
   */
  def asTerm[S](name: String): Term[F, S, A] = new Term(name, this)

  /**
   * Returns this node as a [[Reflect.Variant]], or `None` for another kind of
   * node.
   */
  def asVariant: Option[Reflect.Variant[F, A]] = None

  /**
   * Returns this node as a wrapper node whose wrapped type is not known
   * statically, or `None` for another kind of node.
   */
  def asWrapperUnknown: Option[Reflect.Wrapper.Unknown[F]] = None

  /**
   * Returns the `Binding` of this node: the functions that build and take apart
   * values of `A`.
   */
  def binding(implicit F: HasBinding[F]): Binding[NodeBinding, A]

  /**
   * Returns the examples stored on this node. They are stored as
   * [[DynamicValue]]s, and an example that does not decode with this reflect is
   * left out.
   */
  def examples(implicit F: HasBinding[F]): Seq[A]

  /**
   * Returns a copy of this node whose examples are `value` and `values`,
   * replacing any it had.
   */
  def examples(value: A, values: A*)(implicit F: HasBinding[F]): Reflect[F, A]

  /**
   * Returns a copy of this node whose default value is `value`, replacing any
   * it had. Every node except [[Reflect.Deferred]] evaluates `value` and stores
   * it as a [[DynamicValue]] when this method is called.
   */
  def defaultValue(value: => A)(implicit F: HasBinding[F]): Reflect[F, A]

  /**
   * Returns a copy of this node whose documentation is `value`, replacing any
   * it had.
   */
  def doc(value: Doc): Reflect[F, A]

  /**
   * Returns a copy of this node whose documentation is `value`, as a single
   * paragraph of plain text, replacing any it had. `doc` without an argument
   * reads it back.
   *
   * {{{
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val documented = Book.schema.reflect.doc("A book in the catalogue")
   * val text       = documented.doc.toString.trim // A book in the catalogue
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/reflect/ Reflect]]
   */
  def doc(value: String): Reflect[F, A] = doc(Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(value))))))

  /**
   * Returns `true` when `obj` is a reflect with the same structure, such as the
   * same child nodes, type ID, documentation and modifiers. Bindings, default
   * values and examples are not compared.
   */
  override def equals(obj: Any): Boolean = obj match {
    case that: Reflect[?, ?] => (this eq that) || inner == that.inner
    case _                   => false
  }

  /**
   * Converts `value` into an `A`, or returns a [[SchemaError]] that lists where
   * `value` does not fit this reflect. The errors of all the fields, elements
   * and map entries are collected, not only the first one.
   *
   * Each kind of node expects its own kind of [[DynamicValue]]:
   *   - a record expects a `DynamicValue.Record`. It matches the fields by
   *     name, in any order, and ignores fields it does not know.
   *   - a variant expects a `DynamicValue.Variant` whose case name it knows.
   *   - a sequence expects a `DynamicValue.Sequence`, and a map a
   *     `DynamicValue.Map`.
   *   - a primitive expects a `DynamicValue.Primitive`; numeric types and
   *     `Char` also accept other numeric or `Char` values that convert to them,
   *     without loss except for the cases listed at
   *     `PrimitiveType#fromDynamicValue`.
   *   - a wrapper decodes the value of the type it wraps, then wraps it.
   *   - a dynamic node accepts any value as it is.
   *
   * The errors are `ExpectationMismatch` for the wrong kind of value,
   * `MissingField`, `DuplicatedField` and `UnknownCase`, each at the path where
   * it happened, and `ConversionFailed` at a path ending in `.wrapped` when a
   * wrapper's `wrap` function throws. A missing field is an error even when the
   * field has a default value, and `Validation` constraints are not checked
   * (`DynamicSchema#check` checks them).
   *
   * {{{
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val title = DynamicValue.Primitive(PrimitiveValue.String("Zionomicon"))
   * val year  = DynamicValue.Primitive(PrimitiveValue.Int(2021))
   *
   * val book = Book.schema.reflect.fromDynamicValue(
   *   DynamicValue.Record("title" -> title, "year" -> year)
   * ) // Right(Book(Zionomicon,2021))
   * val missing = Book.schema.reflect.fromDynamicValue(
   *   DynamicValue.Record("title" -> title)
   * ) // Left(zio.blocks.schema.SchemaError: Missing field 'year' at: .)
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/dynamic-value/ DynamicValue]]
   */
  def fromDynamicValue(value: DynamicValue)(implicit F: HasBinding[F]): Either[SchemaError, A] =
    fromDynamicValue(value, Nil)

  private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
    F: HasBinding[F]
  ): Either[SchemaError, A]

  /**
   * Returns the node that `optic` focuses on, or `None` when the optic's path
   * does not lead to a node of this reflect.
   *
   * {{{
   * case class Author(name: String)
   * object Author {
   *   implicit val schema: Schema[Author] = Schema.derived
   * }
   *
   * case class Book(title: String, author: Author)
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book]   = Schema.derived
   *   val authorName: Lens[Book, String] = $(_.author.name)
   * }
   *
   * val node = Book.schema.reflect.get(Book.authorName) // Some(String)
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/optics/ Optics]]
   */
  def get[B](optic: Optic[A, B]): Option[Reflect[F, B]] = get(optic.toDynamic).asInstanceOf[Option[Reflect[F, B]]]

  /**
   * Returns the node at the path `dynamic`, or `None` when the path does not
   * lead to a node of this reflect.
   *
   * Each step moves to a child node: a field step to the record field of that
   * name, a case step to the variant case of that name, an index, indices or
   * elements step to the element node of a sequence, a wrapped step to the
   * wrapped node, a map-keys step to the key node of a map, and any other map
   * step to its value node. A type search step moves to the first node, in
   * depth-first order, whose type ID is the searched one, and a schema search
   * step to the first node that matches its pattern; the rest of the path
   * continues from there.
   */
  def get(dynamic: DynamicOptic): Option[Reflect[F, ?]] = {
    @tailrec
    def loop(current: Reflect[F, ?], idx: Int): Option[Reflect[F, ?]] =
      if (idx == dynamic.nodes.length) new Some(current)
      else {
        dynamic.nodes(idx) match {
          case DynamicOptic.Node.TypeSearch(searchTypeId) =>
            // TypeSearch: find first type matching the TypeId, then continue with remaining path
            return Reflect.typeSearch(current, searchTypeId).flatMap { found =>
              if (idx + 1 == dynamic.nodes.length) Some(found)
              else found.get(new DynamicOptic(dynamic.nodes.drop(idx + 1)))
            }
          case DynamicOptic.Node.SchemaSearch(schemaRepr) =>
            // SchemaSearch: find first type matching the SchemaRepr pattern, then continue
            return Reflect.schemaSearch(current, schemaRepr).flatMap { found =>
              if (idx + 1 == dynamic.nodes.length) Some(found)
              else found.get(new DynamicOptic(dynamic.nodes.drop(idx + 1)))
            }
          case _ =>
            loop(
              dynamic.nodes(idx) match {
                case f: DynamicOptic.Node.Field =>
                  val name = f.name
                  current.asRecord match {
                    case Some(record) =>
                      val fieldIdx = record.fieldIndexByName(name)
                      if (fieldIdx >= 0) record.fields(fieldIdx).value
                      else return None
                    case _ => return None
                  }
                case c: DynamicOptic.Node.Case =>
                  val name = c.name
                  current.asVariant match {
                    case Some(variant) =>
                      val caseIdx = variant.caseIndexByName(name)
                      if (caseIdx >= 0) variant.cases(caseIdx).value
                      else return None
                    case _ => return None
                  }
                case _: DynamicOptic.Node.AtIndex | _: DynamicOptic.Node.AtIndices |
                    _: DynamicOptic.Node.Elements.type =>
                  current.asSequenceUnknown match {
                    case Some(unknown) => unknown.sequence.element
                    case _             => return None
                  }
                case _: DynamicOptic.Node.Wrapped.type =>
                  current.asWrapperUnknown match {
                    case Some(unknown) => unknown.wrapper.wrapped
                    case _             => return None
                  }
                case node =>
                  current.asMapUnknown match {
                    case Some(unknown) =>
                      if (node == DynamicOptic.Node.MapKeys) unknown.map.key
                      else unknown.map.value
                    case _ => return None
                  }
              },
              idx + 1
            )
        }
      }

    loop(this, 0)
  }

  /**
   * Returns the default value stored on this node, or `None` when it has none
   * or the stored value does not decode with this reflect.
   */
  def getDefaultValue(implicit F: HasBinding[F]): Option[A]

  override def hashCode: Int = inner.hashCode

  /** Returns `true` when this is a [[Reflect.Dynamic]] node. */
  def isDynamic: Boolean = false

  /** Returns `true` when this is a [[Reflect.Map]] node. */
  def isMap: Boolean = false

  /** Returns `true` when this is a [[Reflect.Primitive]] node. */
  def isPrimitive: Boolean = false

  /** Returns `true` when this is a [[Reflect.Record]] node. */
  def isRecord: Boolean = false

  /** Returns `true` when this is a [[Reflect.Sequence]] node. */
  def isSequence: Boolean = false

  /** Returns `true` when this is a [[Reflect.Variant]] node. */
  def isVariant: Boolean = false

  /** Returns `true` when this is a [[Reflect.Wrapper]] node. */
  def isWrapper: Boolean = false

  /** Returns `true` when this is a sequence or map node. */
  def isCollection: Boolean = isSequence || isMap

  /**
   * Returns `true` when this is the variant of a `scala.Option`: a variant
   * whose type ID is `Option` in package `scala`, with two cases, the second
   * named `Some`.
   */
  def isOption: Boolean = isVariant && {
    val tid   = typeId
    val cases = asVariant.get.cases
    tid.owner == Owner.fromPackagePath("scala") && tid.name == "Option" &&
    cases.length == 2 && cases(1).name == "Some"
  }

  /**
   * Returns `true` when this is the variant of a `zio.blocks.maybe.Maybe`: a
   * variant whose type ID is `Maybe`, with two cases, the second named
   * `Present`.
   */
  def isMaybe: Boolean = isVariant && {
    val tid = typeId
    tid.isMaybe && {
      val cases = asVariant.get.cases
      cases.length == 2 && cases(1).name == "Present"
    }
  }

  /**
   * Returns `true` when this is a variant whose cases carry no data: each case
   * is a record without fields, such as a case object, or is itself such a
   * variant.
   */
  def isEnumeration: Boolean = isVariant && asVariant.get.cases.forall { case_ =>
    val caseReflect = case_.value
    caseReflect.asRecord.exists(_.fields.isEmpty) || caseReflect.isEnumeration
  }

  /**
   * For an `Option` or `Maybe` variant, returns the node of the value it holds:
   * the single field of its `Some` or `Present` case. Returns `None` for any
   * other node.
   */
  def optionInnerType: Option[Reflect[F, ?]] =
    if (isOption || isMaybe) asVariant.get.cases(1).value.asRecord.map(_.fields(0).value)
    else None

  /**
   * Returns the modifiers attached to this node, in the order they were added.
   *
   * {{{
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val tagged = Book.schema.reflect
   *   .modifier(Modifier.config("db.table", "books"))
   *   .modifier(Modifier.config("db.schema", "library"))
   * val modifiers = tagged.modifiers
   * // List(config(db.table,books), config(db.schema,library))
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/modifier/ Modifier]]
   */
  def modifiers: Seq[Modifier.Reflect]

  /**
   * Returns a copy of this node with `modifier` added after its other
   * modifiers.
   */
  def modifier(modifier: Modifier.Reflect): Reflect[F, A]

  /**
   * Returns a copy of this node with `modifiers` added after its other
   * modifiers.
   */
  def modifiers(modifiers: Iterable[Modifier.Reflect]): Reflect[F, A]

  /** Returns the kind of this node, such as `Reflect.Type.Record`. */
  def nodeType: Reflect.Type { type NodeBinding = self.NodeBinding }

  /**
   * This reflect without bindings: the same tree, with the `metadata` of every
   * node replaced by `NoBinding`. It keeps the structure, documentation,
   * modifiers, default values and examples. `Schema#toDynamicSchema` wraps it
   * in a [[DynamicSchema]].
   */
  lazy val noBinding: Reflect[NoBinding, A] =
    Reflect.withTransformCache(transform(DynamicOptic.root, ReflectTransformer.noBinding()).force)

  /**
   * Converts `value` into a [[DynamicValue]] with the same shape as this
   * reflect: a `DynamicValue.Record` with the fields in declaration order, a
   * `DynamicValue.Variant` named after the value's case, a
   * `DynamicValue.Sequence`, a `DynamicValue.Map` or a
   * `DynamicValue.Primitive`. A wrapper gives the dynamic value of the value it
   * wraps, and a dynamic node returns `value` as it is. `fromDynamicValue`
   * converts the result back.
   *
   * {{{
   * case class Book(title: String, year: Int)
   * object Book {
   *   implicit val schema: Schema[Book] = Schema.derived
   * }
   *
   * val value = Book.schema.reflect.toDynamicValue(Book("Zionomicon", 2021))
   * // {
   * //   title: "Zionomicon",
   * //   year: 2021
   * // }
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/dynamic-value/ DynamicValue]]
   */
  def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue

  /**
   * Rebuilds this reflect with `f`, which replaces what each node holds, an
   * `F`, with a `G`. `f` sees the children of a node already transformed, and
   * `path` is the location of this node: `f` receives it, extended by the
   * field, case, elements, map-keys or map-values step of each child (a wrapped
   * node keeps the path of its wrapper). `f` is not called until the result is
   * forced.
   *
   * For a [[Reflect.Deferred]] node, the result is cached per thread under that
   * node and `f`, which is what makes a recursive type terminate. A `Deferred`
   * node reached by several paths in one scope is transformed once, with the
   * path of the first. `noBinding`, `DynamicSchema#rebind` and type class
   * derivation clear that cache when they return. A direct call leaves its
   * entries in the cache, and so does the first use of a `Deferred` node in any
   * result after the call has returned, because that is when the node's value
   * is transformed.
   *
   * @see
   *   [[https://zioblocks.com/reference/schema/reflect-transformer/ ReflectTransformer]]
   */
  def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Reflect[G, A]]

  /** Returns the type ID of `A`. */
  def typeId: TypeId[A]

  /** Returns a copy of this node whose type ID is `value`. */
  def typeId(value: TypeId[A]): Reflect[F, A]

  /**
   * Returns a copy of this reflect in which the node that `optic` focuses on is
   * replaced by `f` of it, or `None` when the optic's path does not lead to a
   * node of this reflect.
   */
  def updated[B](optic: Optic[A, B])(f: Reflect[F, B] => Reflect[F, B]): Option[Reflect[F, A]] =
    updated(optic.toDynamic)(new Reflect.Updater[F] {
      def update[C](reflect: Reflect[F, C]): Reflect[F, C] =
        f(reflect.asInstanceOf[Reflect[F, B]]).asInstanceOf[Reflect[F, C]]
    })

  /**
   * Returns a copy of this reflect in which the node at the path `dynamic` is
   * replaced by `f.update` of it, or `None` when the path does not lead to a
   * node. It follows field, case, sequence, wrapped, map-keys and map-values
   * steps as `get` does, but it does not run type or schema searches: on a map
   * node a type or schema search step is treated as a map-values step, and on
   * any other node it returns `None`.
   */
  def updated[B](dynamic: DynamicOptic)(f: Reflect.Updater[F]): Option[Reflect[F, A]] = {
    def loop(current: Reflect[F, ?], idx: Int): Option[Reflect[F, ?]] =
      if (idx == dynamic.nodes.length) new Some(f.update(current.asInstanceOf[Reflect[F, B]]))
      else {
        dynamic.nodes(idx) match {
          case f: DynamicOptic.Node.Field =>
            current.asRecord match {
              case Some(record) =>
                record.modifyField(f.name)(new Term.Updater[F] {
                  def update[S, C](input: Term[F, S, C]): Option[Term[F, S, C]] =
                    loop(input.value, idx + 1) match {
                      case Some(value) => new Some(input.copy(value = value.asInstanceOf[Reflect[F, C]]))
                      case _           => None
                    }
                })
              case _ => None
            }
          case c: DynamicOptic.Node.Case =>
            current.asVariant match {
              case Some(variant) =>
                variant.modifyCase(c.name)(new Term.Updater[F] {
                  def update[S, C](input: Term[F, S, C]): Option[Term[F, S, C]] =
                    loop(input.value, idx + 1) match {
                      case Some(value) => new Some(input.copy(value = value.asInstanceOf[Reflect[F, C]]))
                      case _           => None
                    }
                })
              case _ => None
            }
          case _: DynamicOptic.Node.AtIndex | _: DynamicOptic.Node.AtIndices | _: DynamicOptic.Node.Elements.type =>
            current.asSequenceUnknown match {
              case Some(unknown) =>
                val sequence = unknown.sequence
                loop(sequence.element, idx + 1) match {
                  case Some(element) =>
                    new Some(sequence.copy(element = element.asInstanceOf[Reflect[F, unknown.ElementType]]))
                  case _ => None
                }
              case _ => None
            }
          case _: DynamicOptic.Node.Wrapped.type =>
            current.asWrapperUnknown match {
              case Some(unknown) =>
                val wrapper = unknown.wrapper
                loop(wrapper.wrapped, idx + 1) match {
                  case Some(element) =>
                    new Some(wrapper.copy(wrapped = element.asInstanceOf[Reflect[F, unknown.Wrapped]]))
                  case _ => None
                }
              case _ => None
            }
          case node =>
            current.asMapUnknown match {
              case Some(unknown) =>
                val map = unknown.map
                if (node == DynamicOptic.Node.MapKeys) {
                  loop(map.key, idx + 1) match {
                    case Some(key) => new Some(map.copy(key = key.asInstanceOf[Reflect[F, unknown.KeyType]]))
                    case _         => None
                  }
                } else {
                  loop(map.value, idx + 1) match {
                    case Some(value) => new Some(map.copy(value = value.asInstanceOf[Reflect[F, unknown.ValueType]]))
                    case _           => None
                  }
                }
              case _ => None
            }
        }
      }

    loop(this, 0).asInstanceOf[Option[Reflect[F, A]]]
  }

  /** Returns this reflect with the [[SchemaAspect]] `aspect` applied to it. */
  def aspect[Min >: A, Max <: A](aspect: SchemaAspect[Min, Max, F]): Reflect[F, A] = aspect(self)

  /**
   * Returns this reflect with `aspect` applied to the node that `optic` focuses
   * on, or this reflect unchanged when the optic's path does not lead to a
   * node.
   */
  def aspect[B, Min >: B, Max <: B](optic: Optic[A, B], aspect: SchemaAspect[Min, Max, F]): Reflect[F, A] =
    self.updated[B](optic)(aspect(_)).getOrElse(self)
}

/**
 * The kinds of [[Reflect]] node, the [[Reflect.Bound]] and [[Reflect.Unbound]]
 * aliases, and the nodes of the built-in primitive, option, either, collection
 * and map types.
 */
object Reflect {

  /**
   * A [[Reflect]] with runtime bindings, capable of constructing and
   * deconstructing values.
   */
  type Bound[A] = Reflect[Binding, A]

  /**
   * A [[Reflect]] without runtime bindings, used for structural inspection and
   * validation.
   *
   * `Unbound` reflects retain all schema metadata (type names, documentation,
   * validations) but lack the runtime machinery to construct or deconstruct
   * actual values. They are used by [[DynamicSchema]] for runtime validation of
   * [[DynamicValue]] instances.
   */
  type Unbound[A] = Reflect[NoBinding, A]

  /**
   * The kind of a [[Reflect]] node, which `Reflect#nodeType` returns. Its
   * `NodeBinding` is the kind of binding that such a node holds.
   */
  sealed trait Type {
    type NodeBinding <: BindingType
  }

  /**
   * The kinds of node. A [[Reflect.Deferred]] has the kind of the node it
   * stands for.
   */
  object Type {

    /** The kind of a [[Reflect.Record]] node. */
    case object Record extends Type {
      type NodeBinding = BindingType.Record
    }

    /** The kind of a [[Reflect.Variant]] node. */
    case object Variant extends Type {
      type NodeBinding = BindingType.Variant
    }

    /** The kind of a [[Reflect.Sequence]] node of collection type `C`. */
    case class Sequence[C[_]]() extends Type {
      type NodeBinding = BindingType.Seq[C]
    }

    /** The kind of a [[Reflect.Map]] node of map type `M`. */
    case class Map[M[_, _]]() extends Type {
      type NodeBinding = BindingType.Map[M]
    }

    /** The kind of a [[Reflect.Dynamic]] node. */
    case object Dynamic extends Type {
      type NodeBinding = BindingType.Dynamic
    }

    /** The kind of a [[Reflect.Primitive]] node. */
    case object Primitive extends Type {
      type NodeBinding = BindingType.Primitive
    }

    /** The kind of a [[Reflect.Wrapper]] node of `A` wrapping `B`. */
    case class Wrapper[A, B]() extends Type {
      type NodeBinding = BindingType.Wrapper[A, B]
    }
  }

  /**
   * A function that replaces a node of any type, which `Reflect#updated`
   * applies to the node at a [[DynamicOptic]] path.
   */
  trait Updater[F[_, _]] {

    /** Returns the node that replaces `reflect`. */
    def update[A](reflect: Reflect[F, A]): Reflect[F, A]
  }

  /**
   * A node for a product type, such as a case class, with a [[Term]] per field
   * in declaration order.
   *
   * @param fields
   *   the fields, each a [[Term]] with the field's name and node
   * @param recordBinding
   *   a `Binding.Record` with the constructor and deconstructor of `A` in a
   *   bound reflect, `NoBinding` in an unbound one
   * @param storedDefaultValue
   *   the default value, stored as a [[DynamicValue]]
   * @param storedExamples
   *   the examples, stored as [[DynamicValue]]s
   */
  case class Record[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    recordBinding: F[BindingType.Record, A],
    doc: Doc = Doc.empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, A] { self =>
    private[this] val fieldValues      = fields.map(_.value).toArray
    private[this] val fieldIndexByName = new StringToIntMap(fields.length) {
      fields.foreach {
        var idx = 0
        term =>
          put(term.name, idx)
          idx += 1
      }
    }

    protected def inner: Any = (fields, typeId, doc, modifiers)

    type NodeBinding = BindingType.Record

    def doc(value: Doc): Record[F, A] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] =
      storedDefaultValue.flatMap(dv => fromDynamicValue(dv).toOption)

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Record[F, A] =
      copy(storedDefaultValue = new Some(toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] =
      storedExamples.flatMap(dv => fromDynamicValue(dv).toOption)

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Record[F, A] =
      copy(storedExamples = values.prepended(value).map(toDynamicValue))

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Record, A] = F.binding(recordBinding)

    /**
     * Returns the constructor from the binding, which builds an `A` from its
     * fields in registers.
     */
    def constructor(implicit F: HasBinding[F]): Constructor[A] = F.constructor(recordBinding)

    /**
     * Returns the deconstructor from the binding, which writes the fields of an
     * `A` to registers.
     */
    def deconstructor(implicit F: HasBinding[F]): Deconstructor[A] = F.deconstructor(recordBinding)

    /**
     * Returns the field named `name`, or `None` when the record has no such
     * field.
     */
    def fieldByName(name: String): Option[Term[F, A, ?]] = {
      val idx = fieldIndexByName.get(name)
      if (idx >= 0) new Some(fields(idx))
      else None
    }

    private[schema] def fieldIndexByName(name: String): Int = fieldIndexByName.get(name)

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] =
      value match {
        case DynamicValue.Record(fields) =>
          var error: Option[SchemaError] = None

          def addError(e: SchemaError): Unit = error = error.map(_ ++ e).orElse(new Some(e))

          val fieldValues = this.fieldValues.clone
          val constructor = this.constructor
          val registers   = Registers(constructor.usedRegisters)
          val len         = fields.length
          var fieldIdx    = 0
          while (fieldIdx < len) {
            val kv   = fields(fieldIdx)
            val name = kv._1
            val idx  = fieldIndexByName.get(name)
            if (idx >= 0) {
              val fieldValue = fieldValues(idx)
              if (fieldValue ne null) {
                fieldValues(idx) = null
                fieldValue.fromDynamicValue(kv._2, new DynamicOptic.Node.Field(name) :: trace) match {
                  case Right(value) => this.registers(idx).set(registers, 0, value)
                  case Left(error)  => addError(error)
                }
              } else addError(SchemaError.duplicatedField(trace, name))
            }
            fieldIdx += 1
          }
          var idx = 0
          while (idx < fieldValues.length) {
            if (fieldValues(idx) ne null) addError(SchemaError.missingField(trace, this.fields(idx).name))
            idx += 1
          }
          if (error.isDefined) new Left(error.get)
          else new Right(constructor.construct(registers, 0))
        case _ => new Left(SchemaError.expectationMismatch(trace, "Expected a record"))
      }

    /**
     * Returns a [[Lens]] on the field named `name`, or `None` when the record
     * has no such field. `B` is not checked against the type of the field. Use
     * it on a bound record.
     */
    def lensByName[B](name: String): Option[Lens[A, B]] = lensByIndex(fieldIndexByName.get(name))

    /**
     * Returns a [[Lens]] on the field at `index`, counted from 0 in declaration
     * order, or `None` when `index` is out of range. `B` is not checked against
     * the type of the field. Use it on a bound record.
     */
    def lensByIndex[B](index: Int): Option[Lens[A, B]] =
      if (index >= 0 && index < fields.length) {
        new Some(Lens(this.asInstanceOf[Reflect.Record.Bound[A]], fields(index).asInstanceOf[Term.Bound[A, B]]))
      } else None

    def metadata: F[NodeBinding, A] = recordBinding

    def modifier(modifier: Modifier.Reflect): Record[F, A] = copy(modifiers = modifiers.appended(modifier))

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Record[F, A] = copy(modifiers = this.modifiers ++ modifiers)

    /**
     * Returns a copy of this record with the field named `name` replaced by
     * `f.update` of it, or `None` when there is no such field or `f` returns
     * `None`.
     */
    def modifyField(name: String)(f: Term.Updater[F]): Option[Record[F, A]] = {
      val idx = fieldIndexByName.get(name)
      if (idx >= 0) {
        f.update(fields(idx)) match {
          case Some(field) => new Some(copy(fields = fields.updated(idx, field)))
          case _           => None
        }
      } else None
    }

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = {
      val regs = Registers(deconstructor.usedRegisters)
      deconstructor.deconstruct(regs, 0, value)
      val len    = registers.length
      val result = new Array[(String, DynamicValue)](len)
      var idx    = 0
      while (idx < len) {
        val field    = fields(idx)
        val register = registers(idx)
        result(idx) = (
          field.name,
          field.value
            .asInstanceOf[Reflect[F, field.Focus]]
            .toDynamicValue(register.get(regs, 0).asInstanceOf[field.Focus])
        )
        idx += 1
      }
      new DynamicValue.Record(Chunk.fromArray(result))
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Record[G, A]] =
      for {
        fields <- Lazy.foreach(fields)(_.transform(path, Term.Type.Record, f))
        record <-
          f.transformRecord(path, fields, typeId, recordBinding, doc, modifiers, storedDefaultValue, storedExamples)
      } yield record

    /**
     * The register of each field, in field order, which says where the
     * constructor and deconstructor keep the field's value.
     */
    lazy val registers: IndexedSeq[Register[Any]] = ArraySeq.unsafeWrapArray(Record.registers(fieldValues))

    /**
     * The space that the fields take in registers, counted per kind of
     * register.
     */
    lazy val usedRegisters: RegisterOffset = Record.usedRegisters(
      registers.asInstanceOf[ArraySeq[Register[Any]]].unsafeArray.asInstanceOf[Array[Register[Any]]]
    )

    def typeId(value: TypeId[A]): Record[F, A] = copy(typeId = value)

    def nodeType: Reflect.Type.Record.type = Reflect.Type.Record

    override def asRecord: Option[Reflect.Record[F, A]] = new Some(this)

    override def isRecord: Boolean = true

    override def toString: String = ReflectPrinter.printRecord(this)
  }

  /** Register layout helpers for record nodes. */
  object Record {

    /** A record node of a bound reflect. */
    type Bound[A] = Record[Binding, A]

    /**
     * Assigns a register to each node in `reflects`, in order. A node of
     * `Unit`, `Boolean`, `Byte`, `Char`, `Short`, `Float`, `Int`, `Double` or
     * `Long`, or of an opaque type or type alias of one of them, gets a
     * register of that primitive type (a `Unit` takes no space); every other
     * node gets an object register.
     */
    def registers[F[_, _]](reflects: Array[Reflect[F, ?]]): Array[Register[Any]] = {
      var offset    = 0L
      val registers = new Array[Register[?]](reflects.length)
      var idx       = 0
      reflects.foreach { fieldValue =>
        unwrapToPrimitiveTypeOption(fieldValue) match {
          case Some(primitiveType) =>
            primitiveType match {
              case PrimitiveType.Unit =>
                registers(idx) = Register.Unit
              case _: PrimitiveType.Boolean =>
                registers(idx) = new Register.Boolean(offset)
                offset = RegisterOffset.incrementBooleansAndBytes(offset)
              case _: PrimitiveType.Byte =>
                registers(idx) = new Register.Byte(offset)
                offset = RegisterOffset.incrementBooleansAndBytes(offset)
              case _: PrimitiveType.Char =>
                registers(idx) = new Register.Char(offset)
                offset = RegisterOffset.incrementCharsAndShorts(offset)
              case _: PrimitiveType.Short =>
                registers(idx) = new Register.Short(offset)
                offset = RegisterOffset.incrementCharsAndShorts(offset)
              case _: PrimitiveType.Float =>
                registers(idx) = new Register.Float(offset)
                offset = RegisterOffset.incrementFloatsAndInts(offset)
              case _: PrimitiveType.Int =>
                registers(idx) = new Register.Int(offset)
                offset = RegisterOffset.incrementFloatsAndInts(offset)
              case _: PrimitiveType.Double =>
                registers(idx) = new Register.Double(offset)
                offset = RegisterOffset.incrementDoublesAndLongs(offset)
              case _: PrimitiveType.Long =>
                registers(idx) = new Register.Long(offset)
                offset = RegisterOffset.incrementDoublesAndLongs(offset)
              case _ =>
                registers(idx) = new Register.Object(offset)
                offset = RegisterOffset.incrementObjects(offset)
            }
          case _ =>
            registers(idx) = new Register.Object(offset)
            offset = RegisterOffset.incrementObjects(offset)
        }
        idx += 1
      }
      registers.asInstanceOf[Array[Register[Any]]]
    }

    /**
     * Returns the space that `registers` take together, counted per kind of
     * register.
     */
    def usedRegisters(registers: Array[Register[Any]]): RegisterOffset = {
      var offset = 0L
      var idx    = 0
      while (idx < registers.length) {
        offset = RegisterOffset.add(registers(idx).usedRegisters, offset)
        idx += 1
      }
      offset
    }
  }

  /**
   * A node for a sum type, such as a sealed trait or an enum, with a [[Term]]
   * per case. The value of each case term is the node of that case, usually a
   * record.
   *
   * @param cases
   *   the cases, each a [[Term]] with the case's name and node
   * @param variantBinding
   *   a `Binding.Variant` with the discriminator and matchers of `A` in a bound
   *   reflect, `NoBinding` in an unbound one
   * @param storedDefaultValue
   *   the default value, stored as a [[DynamicValue]]
   * @param storedExamples
   *   the examples, stored as [[DynamicValue]]s
   */
  case class Variant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ? <: A]],
    typeId: TypeId[A],
    variantBinding: F[BindingType.Variant, A],
    doc: Doc = Doc.empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, A] {
    private[this] val caseIndexByName = new StringToIntMap(cases.length) {
      cases.foreach {
        var idx = 0
        term =>
          put(term.name, idx)
          idx += 1
      }
    }

    protected def inner: Any = (cases, typeId, doc, modifiers)

    type NodeBinding = BindingType.Variant

    def doc(value: Doc): Variant[F, A] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] =
      storedDefaultValue.flatMap(dv => fromDynamicValue(dv).toOption)

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Variant[F, A] =
      copy(storedDefaultValue = new Some(toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] =
      storedExamples.flatMap(dv => fromDynamicValue(dv).toOption)

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Variant[F, A] =
      copy(storedExamples = values.prepended(value).map(toDynamicValue))

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Variant, A] = F.binding(variantBinding)

    /**
     * Returns the case named `name`, or `None` when the variant has no such
     * case.
     */
    def caseByName(name: String): Option[Term[F, A, ? <: A]] = {
      val idx = caseIndexByName.get(name)
      if (idx >= 0) new Some(cases(idx))
      else None
    }

    private[schema] def caseIndexByName(name: String): Int = caseIndexByName.get(name)

    /**
     * Returns the discriminator from the binding, which gives the index of the
     * case of a value.
     */
    def discriminator(implicit F: HasBinding[F]): Discriminator[A] = F.discriminator(variantBinding)

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] =
      value match {
        case DynamicValue.Variant(discriminator, value) =>
          val idx = caseIndexByName.get(discriminator)
          if (idx >= 0) {
            val case_ = cases(idx)
            case_.value
              .asInstanceOf[Reflect[F, A]]
              .fromDynamicValue(value, new DynamicOptic.Node.Case(case_.name) :: trace)
          } else new Left(SchemaError.unknownCase(trace, discriminator))
        case _ => new Left(SchemaError.expectationMismatch(trace, "Expected a variant"))
      }

    /**
     * Returns the matchers from the binding, one per case, which downcast a
     * value to that case.
     */
    def matchers(implicit F: HasBinding[F]): Matchers[A] = F.matchers(variantBinding)

    def metadata: F[NodeBinding, A] = variantBinding

    def modifier(modifier: Modifier.Reflect): Variant[F, A] = copy(modifiers = modifiers.appended(modifier))

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Variant[F, A] = copy(modifiers = this.modifiers ++ modifiers)

    /**
     * Returns a copy of this variant with the case named `name` replaced by
     * `f.update` of it, or `None` when there is no such case or `f` returns
     * `None`.
     */
    def modifyCase(name: String)(f: Term.Updater[F]): Option[Variant[F, A]] = {
      val idx = caseIndexByName.get(name)
      if (idx >= 0) {
        f.update(cases(idx)) match {
          case Some(case_) => new Some(copy(cases = cases.updated(idx, case_)))
          case _           => None
        }
      } else None
    }

    /**
     * Returns a [[Prism]] on the case named `name`, or `None` when the variant
     * has no such case. `B` is not checked against the type of the case. Use it
     * on a bound variant.
     */
    def prismByName[B <: A](name: String): Option[Prism[A, B]] = prismByIndex(caseIndexByName.get(name))

    /**
     * Returns a [[Prism]] on the case at `index`, counted from 0 in declaration
     * order, or `None` when `index` is out of range. `B` is not checked against
     * the type of the case. Use it on a bound variant.
     */
    def prismByIndex[B <: A](index: Int): Option[Prism[A, B]] =
      if (index >= 0 && index < cases.length) {
        new Some(Prism(this.asInstanceOf[Reflect.Variant.Bound[A]], cases(index).asInstanceOf[Term.Bound[A, B]]))
      } else None

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = {
      val case_ = cases(discriminator.discriminate(value))
      new DynamicValue.Variant(case_.name, case_.value.asInstanceOf[Reflect[F, A]].toDynamicValue(value))
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Variant[G, A]] =
      for {
        cases   <- Lazy.foreach(cases)(_.transform(path, Term.Type.Variant, f))
        variant <-
          f.transformVariant(path, cases, typeId, variantBinding, doc, modifiers, storedDefaultValue, storedExamples)
      } yield variant

    def typeId(value: TypeId[A]): Variant[F, A] = copy(typeId = value)

    def nodeType: Reflect.Type.Variant.type = Reflect.Type.Variant

    override def asVariant: Option[Reflect.Variant[F, A]] = new Some(this)

    override def isVariant: Boolean = true

    override def toString: String = ReflectPrinter.printVariant(this)
  }

  /** Type alias for variant nodes of a bound reflect. */
  object Variant {

    /** A variant node of a bound reflect. */
    type Bound[A] = Variant[Binding, A]
  }

  /**
   * A node for a collection `C[A]` of elements of type `A`, such as `List[A]`,
   * `Vector[A]` or `Set[A]`.
   *
   * @param element
   *   the node of the element type
   * @param seqBinding
   *   a `Binding.Seq` with the constructor and deconstructor of `C` in a bound
   *   reflect, `NoBinding` in an unbound one
   * @param storedDefaultValue
   *   the default value, stored as a [[DynamicValue]]
   * @param storedExamples
   *   the examples, stored as [[DynamicValue]]s
   */
  case class Sequence[F[_, _], A, C[_]](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    seqBinding: F[BindingType.Seq[C], C[A]],
    doc: Doc = Doc.empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, C[A]] { self =>
    require(element ne null)

    protected def inner: Any = (element, typeId, doc, modifiers)

    type NodeBinding = BindingType.Seq[C]

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Seq[C], C[A]] = F.binding(seqBinding)

    def doc(value: Doc): Sequence[F, A, C] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[C[A]] =
      storedDefaultValue.flatMap(dv => fromDynamicValue(dv).toOption)

    def defaultValue(value: => C[A])(implicit F: HasBinding[F]): Sequence[F, A, C] =
      copy(storedDefaultValue = new Some(toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[C[A]] =
      storedExamples.flatMap(dv => fromDynamicValue(dv).toOption)

    def examples(value: C[A], values: C[A]*)(implicit F: HasBinding[F]): Sequence[F, A, C] =
      copy(storedExamples = values.prepended(value).map(toDynamicValue))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, C[A]] = {
      var error: Option[SchemaError] = None

      def addError(e: SchemaError): Unit = error = error.map(_ ++ e).orElse(new Some(e))

      value match {
        case DynamicValue.Sequence(elements) =>
          val seqTrace                       = DynamicOptic.Node.Elements :: trace
          val constructor                    = seqConstructor
          var idx                            = -1
          implicit val classTag: ClassTag[A] = elemClassTag
          val builder                        = constructor.newBuilder[A](elements.size)
          elements.foreach { elem =>
            idx += 1
            element.fromDynamicValue(elem, new DynamicOptic.Node.AtIndex(idx) :: seqTrace) match {
              case Right(value) => constructor.add(builder, value)
              case Left(error)  => addError(error)
            }
          }
          if (error.isDefined) new Left(error.get)
          else new Right(constructor.result(builder))
        case _ => new Left(SchemaError.expectationMismatch(trace, "Expected a sequence"))
      }
    }

    def metadata: F[NodeBinding, C[A]] = seqBinding

    def modifier(modifier: Modifier.Reflect): Sequence[F, A, C] = copy(modifiers = modifiers.appended(modifier))

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Sequence[F, A, C] =
      copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: C[A])(implicit F: HasBinding[F]): DynamicValue = {
      val iterator = seqDeconstructor.deconstruct(value)
      val len      = seqDeconstructor.size(value)
      val result   = new Array[DynamicValue](len)
      var idx      = 0
      while (idx < len) {
        result(idx) = element.toDynamicValue(iterator.next())
        idx += 1
      }
      new DynamicValue.Sequence(Chunk.fromArray(result))
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Sequence[G, A, C]] =
      for {
        element  <- element.transform(path(DynamicOptic.elements), f)
        sequence <-
          f.transformSequence(path, element, typeId, seqBinding, doc, modifiers, storedDefaultValue, storedExamples)
      } yield sequence

    /**
     * Returns the constructor from the binding, which builds a `C` from
     * elements.
     */
    def seqConstructor(implicit F: HasBinding[F]): SeqConstructor[C] = F.seqConstructor(seqBinding)

    /**
     * Returns the deconstructor from the binding, which iterates over the
     * elements of a `C`.
     */
    def seqDeconstructor(implicit F: HasBinding[F]): SeqDeconstructor[C] = F.seqDeconstructor(seqBinding)

    /**
     * Returns the class tag of the element type, taken from the element's type
     * ID.
     */
    def elemClassTag: ClassTag[A] = element.typeId.classTag.asInstanceOf[ClassTag[A]]

    def typeId(value: TypeId[C[A]]): Sequence[F, A, C] = copy(typeId = value)

    def nodeType: Reflect.Type.Sequence[C] = new Reflect.Type.Sequence

    override def asSequence(implicit ev: IsCollection[C[A]]): Option[Reflect.Sequence[F, ev.Elem, ev.Collection]] =
      new Some(this.asInstanceOf[Reflect.Sequence[F, ev.Elem, ev.Collection]])

    override def asSequenceUnknown: Option[Reflect.Sequence.Unknown[F]] = new Some(new Reflect.Sequence.Unknown[F] {
      def sequence: Reflect.Sequence[F, ElementType, CollectionType] =
        self.asInstanceOf[Reflect.Sequence[F, ElementType, CollectionType]]
    })

    override def isSequence: Boolean = true

    override def toString: String = ReflectPrinter.printSequence(this)
  }

  /** Type aliases for sequence nodes. */
  object Sequence {

    /** A sequence node of a bound reflect. */
    type Bound[A, C[_]] = Sequence[Binding, A, C]

    /**
     * A sequence node whose element and collection types are not known
     * statically, which `Reflect#asSequenceUnknown` returns.
     */
    trait Unknown[F[_, _]] {

      /** The collection type constructor of the node. */
      type CollectionType[_]

      /** The element type of the node. */
      type ElementType

      /** The sequence node. */
      def sequence: Reflect.Sequence[F, ElementType, CollectionType]
    }
  }

  /**
   * A node for a map `M[K, V]` with keys of type `K` and values of type `V`.
   *
   * @param key
   *   the node of the key type
   * @param value
   *   the node of the value type
   * @param mapBinding
   *   a `Binding.Map` with the constructor and deconstructor of `M` in a bound
   *   reflect, `NoBinding` in an unbound one
   * @param storedDefaultValue
   *   the default value, stored as a [[DynamicValue]]
   * @param storedExamples
   *   the examples, stored as [[DynamicValue]]s
   */
  case class Map[F[_, _], K, V, M[_, _]](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    mapBinding: F[BindingType.Map[M], M[K, V]],
    doc: Doc = Doc.empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, M[K, V]] { self =>
    require((key ne null) && (value ne null))

    protected def inner: Any = (key, value, typeId, doc, modifiers)

    type NodeBinding = BindingType.Map[M]

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Map[M], M[K, V]] = F.binding(mapBinding)

    def doc(value: Doc): Map[F, K, V, M] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[M[K, V]] =
      storedDefaultValue.flatMap(dv => fromDynamicValue(dv).toOption)

    def defaultValue(value: => M[K, V])(implicit F: HasBinding[F]): Map[F, K, V, M] =
      copy(storedDefaultValue = new Some(toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[M[K, V]] =
      storedExamples.flatMap(dv => fromDynamicValue(dv).toOption)

    def examples(value: M[K, V], values: M[K, V]*)(implicit F: HasBinding[F]): Map[F, K, V, M] =
      copy(storedExamples = values.prepended(value).map(toDynamicValue))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, M[K, V]] = {
      var error: Option[SchemaError] = None

      def addError(e: SchemaError): Unit = error = error.map(_ ++ e).orElse(new Some(e))

      value match {
        case DynamicValue.Map(elements) =>
          val keyTrace    = DynamicOptic.Node.MapKeys :: trace
          val valueTrace  = DynamicOptic.Node.MapValues :: trace
          val constructor = mapConstructor
          val builder     = constructor.newObjectBuilder[K, V](elements.size)
          elements.foreach { case (key, value) =>
            this.key.fromDynamicValue(key, keyTrace) match {
              case Right(keyValue) =>
                this.value.fromDynamicValue(value, new DynamicOptic.Node.AtMapKey(key) :: valueTrace) match {
                  case Right(valueValue) => constructor.addObject(builder, keyValue, valueValue)
                  case Left(error)       => addError(error)
                }
              case Left(error) => addError(error)
            }
          }
          if (error.isDefined) new Left(error.get)
          else new Right(constructor.resultObject(builder))
        case _ => new Left(SchemaError.expectationMismatch(trace, "Expected a map"))
      }
    }

    /**
     * Returns the constructor from the binding, which builds an `M` from
     * key-value pairs.
     */
    def mapConstructor(implicit F: HasBinding[F]): MapConstructor[M] = F.mapConstructor(mapBinding)

    /**
     * Returns the deconstructor from the binding, which iterates over the
     * entries of an `M`.
     */
    def mapDeconstructor(implicit F: HasBinding[F]): MapDeconstructor[M] = F.mapDeconstructor(mapBinding)

    def metadata: F[NodeBinding, M[K, V]] = mapBinding

    def modifier(modifier: Modifier.Reflect): Map[F, K, V, M] = copy(modifiers = modifiers.appended(modifier))

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Map[F, K, V, M] =
      copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: M[K, V])(implicit F: HasBinding[F]): DynamicValue = {
      val deconstructor = mapDeconstructor
      val it            = deconstructor.deconstruct(value)
      val len           = deconstructor.size(value)
      val result        = new Array[(DynamicValue, DynamicValue)](len)
      var idx           = 0
      while (idx < len) {
        val next = it.next()
        result(idx) =
          (this.key.toDynamicValue(deconstructor.getKey(next)), this.value.toDynamicValue(deconstructor.getValue(next)))
        idx += 1
      }
      new DynamicValue.Map(Chunk.fromArray(result))
    }

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Map[G, K, V, M]] =
      for {
        key   <- key.transform(path(DynamicOptic.mapKeys), f)
        value <- value.transform(path(DynamicOptic.mapValues), f)
        map   <-
          f.transformMap(path, key, value, typeId, mapBinding, doc, modifiers, storedDefaultValue, storedExamples)
      } yield map

    def typeId(value: TypeId[M[K, V]]): Map[F, K, V, M] = copy(typeId = value)

    def nodeType: Reflect.Type.Map[M] = new Reflect.Type.Map

    override def asMap(implicit ev: IsMap[M[K, V]]): Option[Reflect.Map[F, ev.Key, ev.Value, ev.Map]] =
      new Some(this.asInstanceOf[Reflect.Map[F, ev.Key, ev.Value, ev.Map]])

    override def asMapUnknown: Option[Reflect.Map.Unknown[F]] = new Some(new Reflect.Map.Unknown[F] {
      def map: Reflect.Map[F, KeyType, ValueType, MapType] =
        self.asInstanceOf[Reflect.Map[F, KeyType, ValueType, MapType]]
    })

    override def isMap: Boolean = true

    override def toString: String = ReflectPrinter.printMap(this)
  }

  /** Type aliases for map nodes. */
  object Map {

    /** A map node of a bound reflect. */
    type Bound[K, V, M[_, _]] = Map[Binding, K, V, M]

    /**
     * A map node whose key, value and map types are not known statically, which
     * `Reflect#asMapUnknown` returns.
     */
    trait Unknown[F[_, _]] {

      /** The map type constructor of the node. */
      type MapType[_, _]

      /** The key type of the node. */
      type KeyType

      /** The value type of the node. */
      type ValueType

      /** The map node. */
      def map: Reflect.Map[F, KeyType, ValueType, MapType]
    }
  }

  /**
   * A node for a [[DynamicValue]] of any shape. `fromDynamicValue` returns any
   * value as it is, and default values and examples are stored as they are
   * given. Two dynamic nodes are equal when their documentation and modifiers
   * are.
   *
   * @param dynamicBinding
   *   a `Binding.Dynamic` in a bound reflect, `NoBinding` in an unbound one
   * @param storedDefaultValue
   *   the default value
   * @param storedExamples
   *   the examples
   */
  case class Dynamic[F[_, _]](
    dynamicBinding: F[BindingType.Dynamic, DynamicValue],
    typeId: TypeId[DynamicValue] = TypeId.of[DynamicValue],
    doc: Doc = Doc.empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, DynamicValue] {
    protected def inner: Any = (modifiers, doc)

    type NodeBinding = BindingType.Dynamic

    def binding(implicit F: HasBinding[F]): Binding[BindingType.Dynamic, DynamicValue] = F.binding(dynamicBinding)

    def doc(value: Doc): Dynamic[F] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[DynamicValue] = storedDefaultValue

    def defaultValue(value: => DynamicValue)(implicit F: HasBinding[F]): Dynamic[F] =
      copy(storedDefaultValue = new Some(value))

    def examples(implicit F: HasBinding[F]): Seq[DynamicValue] = storedExamples

    def examples(value: DynamicValue, values: DynamicValue*)(implicit F: HasBinding[F]): Dynamic[F] =
      copy(storedExamples = values.prepended(value))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, DynamicValue] = new Right(value)

    def metadata: F[NodeBinding, DynamicValue] = dynamicBinding

    def modifier(modifier: Modifier.Reflect): Dynamic[F] = copy(modifiers = modifiers.appended(modifier))

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Dynamic[F] = copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: DynamicValue)(implicit F: HasBinding[F]): DynamicValue = value

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Dynamic[G]] =
      for {
        dynamic <-
          f.transformDynamic(path, typeId, dynamicBinding, doc, modifiers, storedDefaultValue, storedExamples)
      } yield dynamic

    def typeId(value: TypeId[DynamicValue]): Dynamic[F] = copy(typeId = value)

    def nodeType: Reflect.Type.Dynamic.type = Reflect.Type.Dynamic

    override def asDynamic: Option[Reflect.Dynamic[F]] = new Some(this)

    override def isDynamic: Boolean = true

    override def toString: String = ReflectPrinter.printDynamic(this)
  }

  /** Type alias for dynamic nodes of a bound reflect. */
  object Dynamic {

    /** A dynamic node of a bound reflect. */
    type Bound = Dynamic[Binding]
  }

  /**
   * A node for one of the scalar types listed in [[PrimitiveType]], such as
   * `String`, `Int` or `java.time.Instant`. It converts values to and from
   * [[DynamicValue]] with `primitiveType`.
   *
   * @param primitiveType
   *   the primitive type, with the `Validation` that `DynamicSchema#check`
   *   checks
   * @param primitiveBinding
   *   a `Binding.Primitive` in a bound reflect, `NoBinding` in an unbound one
   * @param storedDefaultValue
   *   the default value, stored as a [[DynamicValue]]
   * @param storedExamples
   *   the examples, stored as [[DynamicValue]]s
   */
  case class Primitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    primitiveBinding: F[BindingType.Primitive, A],
    doc: Doc = Doc.empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, A] { self =>
    protected def inner: Any = (primitiveType, typeId, doc, modifiers)

    type NodeBinding = BindingType.Primitive

    def binding(implicit F: HasBinding[F]): Binding.Primitive[A] = F.primitive(primitiveBinding)

    def doc(value: Doc): Primitive[F, A] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] =
      storedDefaultValue.flatMap(dv => primitiveType.fromDynamicValue(dv, Nil).toOption)

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Primitive[F, A] =
      copy(storedDefaultValue = new Some(primitiveType.toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] =
      storedExamples.flatMap(dv => primitiveType.fromDynamicValue(dv, Nil).toOption)

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Primitive[F, A] =
      copy(storedExamples = values.prepended(value).map(primitiveType.toDynamicValue))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] = primitiveType.fromDynamicValue(value, trace)

    def metadata: F[NodeBinding, A] = primitiveBinding

    def modifier(modifier: Modifier.Reflect): Primitive[F, A] = copy(modifiers = modifiers.appended(modifier))

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Primitive[F, A] =
      copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = primitiveType.toDynamicValue(value)

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Primitive[G, A]] =
      for {
        primitive <- f.transformPrimitive(
                       path,
                       primitiveType,
                       typeId,
                       primitiveBinding,
                       doc,
                       modifiers,
                       storedDefaultValue,
                       storedExamples
                     )
      } yield primitive

    def typeId(value: TypeId[A]): Primitive[F, A] = copy(typeId = value)

    def nodeType: Reflect.Type.Primitive.type = Reflect.Type.Primitive

    override def asPrimitive: Option[Reflect.Primitive[F, A]] = new Some(this)

    override def isPrimitive: Boolean = true

    override def toString: String = ReflectPrinter.printPrimitive(this)
  }

  /** Type alias for primitive nodes of a bound reflect. */
  object Primitive {

    /** A primitive node of a bound reflect. */
    type Bound[A] = Primitive[Binding, A]
  }

  /**
   * A node for a type `A` that wraps a value of type `B`, such as an opaque
   * type or a type built with `Schema#transform`. `A` has the [[DynamicValue]]
   * form of `B`: `toDynamicValue` unwraps the value, and `fromDynamicValue`
   * decodes a `B` and wraps it, returning a `ConversionFailed` error at
   * `.wrapped` when the wrap function throws.
   *
   * @param wrapped
   *   the node of the wrapped type `B`
   * @param wrapperBinding
   *   a `Binding.Wrapper` with the wrap and unwrap functions in a bound
   *   reflect, `NoBinding` in an unbound one
   * @param storedDefaultValue
   *   the default value, stored as a [[DynamicValue]]
   * @param storedExamples
   *   the examples, stored as [[DynamicValue]]s
   */
  case class Wrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    wrapperBinding: F[BindingType.Wrapper[A, B], A],
    doc: Doc = Doc.empty,
    modifiers: Seq[Modifier.Reflect] = Nil,
    storedDefaultValue: Option[DynamicValue] = None,
    storedExamples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Reflect[F, A] { self =>
    require((wrapped ne null) && (typeId ne null), "Wrapper requires non-null wrapped and typeId")
    protected def inner: Any = (wrapped, typeId, doc, modifiers)

    /**
     * Returns the primitive type behind `typeId` when it is an opaque type or
     * type alias of a primitive type, or is itself one; otherwise `None`.
     */
    def underlyingPrimitiveType: Option[PrimitiveType[A]] =
      PrimitiveType.fromTypeId(typeId)

    type NodeBinding = BindingType.Wrapper[A, B]

    def binding(implicit F: HasBinding[F]): Binding.Wrapper[A, B] = F.wrapper(wrapperBinding)

    def doc(value: Doc): Wrapper[F, A, B] = copy(doc = value)

    def getDefaultValue(implicit F: HasBinding[F]): Option[A] =
      storedDefaultValue.flatMap(dv => fromDynamicValue(dv).toOption)

    def defaultValue(value: => A)(implicit F: HasBinding[F]): Wrapper[F, A, B] =
      copy(storedDefaultValue = new Some(toDynamicValue(value)))

    def examples(implicit F: HasBinding[F]): Seq[A] =
      storedExamples.flatMap(dv => fromDynamicValue(dv).toOption)

    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Wrapper[F, A, B] =
      copy(storedExamples = values.prepended(value).map(toDynamicValue))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] =
      wrapped.fromDynamicValue(value, trace) match {
        case Right(unwrapped) =>
          try new Right(binding.wrap(unwrapped))
          catch {
            case error if NonFatal(error) =>
              new Left(SchemaError.conversionFailed(DynamicOptic.Node.Wrapped :: trace, error.getMessage))
          }
        case left => left.asInstanceOf[Either[SchemaError, A]]
      }

    def metadata: F[NodeBinding, A] = wrapperBinding

    def modifier(modifier: Modifier.Reflect): Wrapper[F, A, B] = copy(modifiers = modifiers.appended(modifier))

    def modifiers(modifiers: Iterable[Modifier.Reflect]): Wrapper[F, A, B] =
      copy(modifiers = this.modifiers ++ modifiers)

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue =
      wrapped.toDynamicValue(binding.unwrap(value))

    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Wrapper[G, A, B]] =
      for {
        wrapped <- wrapped.transform(path, f)
        wrapper <- f.transformWrapper(
                     path,
                     wrapped,
                     typeId,
                     wrapperBinding,
                     doc,
                     modifiers,
                     storedDefaultValue,
                     storedExamples
                   )
      } yield wrapper

    def typeId(value: TypeId[A]): Wrapper[F, A, B] = copy(typeId = value)

    override def asWrapperUnknown: Option[Reflect.Wrapper.Unknown[F]] = new Some(new Reflect.Wrapper.Unknown[F] {
      def wrapper: Reflect.Wrapper[F, Wrapping, Wrapped] = self.asInstanceOf[Reflect.Wrapper[F, Wrapping, Wrapped]]
    })

    override def isWrapper: Boolean = true

    override def toString: String = ReflectPrinter.printWrapper(this)

    def nodeType: Reflect.Type.Wrapper[A, B] = new Reflect.Type.Wrapper
  }

  /** Type aliases for wrapper nodes. */
  object Wrapper {

    /** A wrapper node of a bound reflect. */
    type Bound[A, B] = Wrapper[Binding, A, B]

    /**
     * A wrapper node whose types are not known statically, which
     * `Reflect#asWrapperUnknown` returns.
     */
    trait Unknown[F[_, _]] {

      /** The wrapper type of the node. */
      type Wrapping

      /** The wrapped type of the node. */
      type Wrapped

      /** The wrapper node. */
      def wrapper: Reflect.Wrapper[F, Wrapping, Wrapped]
    }
  }

  /**
   * A node that stands for another node, built the first time it is needed. A
   * recursive type uses it to refer to itself: in the derived schema of
   * `case class Tree(value: Int, children: List[Tree])`, the node of the
   * `children` field is a `Deferred`. Derivation also uses it for every field
   * with a default value.
   *
   * Its methods forward to `value`, except for the default value and examples
   * set on this node, and the type ID when `_typeId` is set. The methods that
   * walk the tree stop when they come back to this node: `typeId` then returns
   * a placeholder named `<deferred-cycle>`, `toString` prints `deferred =>` and
   * the type ID, `equals` returns `true`, and the `as*` and `is*` methods
   * return `None` and `false`.
   *
   * @param _value
   *   builds the node that this one stands for
   * @param _typeId
   *   a type ID to return instead of that node's
   */
  case class Deferred[F[_, _], A](
    _value: () => Reflect[F, A],
    _typeId: Option[TypeId[A]] = None,
    private val deferredDefaultValue: Option[() => A] = None,
    private val deferredExamples: collection.immutable.Seq[() => A] = Nil
  ) extends Reflect[F, A] { self =>
    protected def inner: Any = value.inner

    /** The node that this one stands for, built on first access. */
    final lazy val value: Reflect[F, A] = _value()

    type NodeBinding = value.NodeBinding

    def binding(implicit F: HasBinding[F]): Binding[NodeBinding, A] = value.binding

    /**
     * Returns a copy that stands for the node with the documentation `value`.
     */
    def doc(value: Doc): Deferred[F, A] = copy(_value = () => _value().doc(value))

    /**
     * Returns the default value set on this node, evaluated on each call, or
     * else the default value of `value`.
     */
    def getDefaultValue(implicit F: HasBinding[F]): Option[A] =
      deferredDefaultValue.map(_()).orElse(value.getDefaultValue)

    /**
     * Returns a copy whose default value is `dv`. Unlike the other nodes, it
     * does not evaluate `dv`: `getDefaultValue` evaluates it on every call.
     */
    def defaultValue(dv: => A)(implicit F: HasBinding[F]): Deferred[F, A] =
      copy(deferredDefaultValue = new Some(() => dv))

    /**
     * Returns the examples set on this node, or else the examples of `value`.
     */
    def examples(implicit F: HasBinding[F]): Seq[A] =
      if (deferredExamples.nonEmpty) deferredExamples.map(_())
      else value.examples

    /**
     * Returns a copy whose examples are `value` and `values`, kept as values of
     * `A` instead of [[DynamicValue]]s.
     */
    def examples(value: A, values: A*)(implicit F: HasBinding[F]): Deferred[F, A] =
      copy(deferredExamples = values.map(v => () => v).prepended(() => value))

    private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node])(implicit
      F: HasBinding[F]
    ): Either[SchemaError, A] = this.value.fromDynamicValue(value, trace)

    def metadata: F[NodeBinding, A] = value.metadata

    /** Returns the modifiers of `value`. */
    def modifiers: Seq[Modifier.Reflect] = value.modifiers

    /** Returns a copy that stands for the node with `modifier` added. */
    def modifier(modifier: Modifier.Reflect): Deferred[F, A] = copy(_value = () => value.modifier(modifier))

    /** Returns a copy that stands for the node with `modifiers` added. */
    def modifiers(modifiers: Iterable[Modifier.Reflect]): Deferred[F, A] =
      copy(_value = () => value.modifiers(modifiers))

    /** Returns the documentation of `value`. */
    def doc: Doc = value.doc

    def toDynamicValue(value: A)(implicit F: HasBinding[F]): DynamicValue = this.value.toDynamicValue(value)

    /**
     * Returns a deferred node that transforms `value` with `f` when it is first
     * needed. The result is cached per thread under this node and `f`, which is
     * what makes the transformation of a recursive type terminate.
     */
    def transform[G[_, _]](path: DynamicOptic, f: ReflectTransformer[F, G]): Lazy[Reflect[G, A]] =
      Lazy {
        val c      = Reflect.transformCache.get
        val key    = new IdentityTuple(this, f)
        val cached = c.get(key)
        if (cached ne null) cached.asInstanceOf[Reflect[G, A]]
        else {
          val result = Deferred(() => value.transform(path, f).force, _typeId, deferredDefaultValue, deferredExamples)
          c.put(key, result)
          result
        }
      }

    /**
     * Returns `_typeId` when it is set, or else the type ID of `value`, or the
     * placeholder `<deferred-cycle>` when looking it up comes back to this
     * node.
     */
    def typeId: TypeId[A] = _typeId.getOrElse {
      val v = visited.get
      if (v.containsKey(this)) {
        // Cycle detected - create a placeholder TypeId to break recursion
        TypeId.nominal[A]("<deferred-cycle>", Owner.Root)
      } else {
        v.put(this, ())
        try value.typeId
        finally v.remove(this)
      }
    }

    /**
     * Returns a copy whose type ID is `newTypeId`, without building `value`.
     */
    def typeId(newTypeId: TypeId[A]): Deferred[F, A] = copy(_typeId = new Some(newTypeId))

    override def hashCode: Int = {
      val v = visited.get
      if (v.containsKey(this)) 0 // exit from recursion
      else {
        v.put(this, ())
        try inner.hashCode
        finally v.remove(this)
      }
    }

    override def equals(obj: Any): Boolean = obj match {
      case that: Reflect[?, ?] =>
        (this eq that) || {
          val v = visited.get
          if (v.containsKey(this)) true // exit from recursion
          else {
            v.put(this, ())
            try inner == that.inner
            finally v.remove(this)
          }
        }
      case _ => false
    }

    override def asDynamic: Option[Reflect.Dynamic[F]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asDynamic
        finally v.remove(this)
      }
    }

    override def asMap(implicit ev: IsMap[A]): Option[Reflect.Map[F, ev.Key, ev.Value, ev.Map]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asMap(ev)
        finally v.remove(this)
      }
    }

    override def asMapUnknown: Option[Reflect.Map.Unknown[F]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asMapUnknown
        finally v.remove(this)
      }
    }

    override def asPrimitive: Option[Reflect.Primitive[F, A]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asPrimitive
        finally v.remove(this)
      }
    }

    override def asRecord: Option[Reflect.Record[F, A]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asRecord
        finally v.remove(this)
      }
    }

    override def asSequence(implicit ev: IsCollection[A]): Option[Reflect.Sequence[F, ev.Elem, ev.Collection]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asSequence(ev)
        finally v.remove(this)
      }
    }

    override def asSequenceUnknown: Option[Reflect.Sequence.Unknown[F]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asSequenceUnknown
        finally v.remove(this)
      }
    }

    override def asVariant: Option[Reflect.Variant[F, A]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asVariant
        finally v.remove(this)
      }
    }

    override def asWrapperUnknown: Option[Reflect.Wrapper.Unknown[F]] = {
      val v = visited.get
      if (v.containsKey(this)) None // exit from recursion
      else {
        v.put(this, ())
        try value.asWrapperUnknown
        finally v.remove(this)
      }
    }

    override def isDynamic: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isDynamic
        finally v.remove(this)
      }
    }

    override def isMap: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isMap
        finally v.remove(this)
      }
    }

    override def isPrimitive: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isPrimitive
        finally v.remove(this)
      }
    }

    override def isRecord: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isRecord
        finally v.remove(this)
      }
    }

    override def isSequence: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isSequence
        finally v.remove(this)
      }
    }

    override def isVariant: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isVariant
        finally v.remove(this)
      }
    }

    override def isWrapper: Boolean = {
      val v = visited.get
      if (v.containsKey(this)) false // exit from recursion
      else {
        v.put(this, ())
        try value.isWrapper
        finally v.remove(this)
      }
    }

    private[this] val visited =
      new ThreadLocal[java.util.IdentityHashMap[AnyRef, Unit]] {
        override def initialValue: java.util.IdentityHashMap[AnyRef, Unit] = new java.util.IdentityHashMap
      }

    /** Returns the kind of `value`. */
    def nodeType = value.nodeType

    override def toString: String = {
      val v = visited.get
      if (v.containsKey(this)) s"deferred => $typeId"
      else {
        v.put(this, ())
        try value.toString
        finally v.remove(this)
      }
    }
  }

  /** Type alias for deferred nodes of a bound reflect. */
  object Deferred {

    /** A deferred node of a bound reflect. */
    type Bound[A] = Deferred[Binding, A]
  }

  private[schema] class IdentityTuple(val v1: AnyRef, val v2: AnyRef) {
    override def equals(obj: Any): Boolean = obj match {
      case that: IdentityTuple => (this.v1 eq that.v1) && (this.v2 eq that.v2)
      case _                   => false
    }

    override def hashCode(): Int = System.identityHashCode(v1) * 31 + System.identityHashCode(v2)
  }

  // Per-thread memoization map shared across all `Reflect.Deferred` instances during a single
  // top-level `transform` walk. Used as a cycle-breaker so that recursive schemas terminate
  // and so that a `Deferred` reached via multiple paths is shared in the result.
  //
  // Keeping it scoped (cleared by `withTransformCache` on outermost exit) prevents transformers
  // and their captured state — e.g. the override maps held by `DerivationBuilder`'s anonymous
  // `ReflectTransformer` — from being pinned for the lifetime of the thread.
  private[schema] val transformCache: ThreadLocal[java.util.HashMap[IdentityTuple, AnyRef]] =
    new ThreadLocal[java.util.HashMap[IdentityTuple, AnyRef]] {
      override def initialValue: java.util.HashMap[IdentityTuple, AnyRef] = new java.util.HashMap
    }

  private[this] val transformDepth: ThreadLocal[Integer] =
    new ThreadLocal[Integer] {
      override def initialValue: Integer = Integer.valueOf(0)
    }

  /**
   * Establishes a scope around a top-level `transform` (or chain of `transform`
   * calls) so that the per-thread cache used to break cycles is cleared once
   * the outermost call returns.
   *
   * The block must encompass the entire `Lazy.force` chain that triggers the
   * transformation, not just the construction of the outer `Lazy`, since the
   * transform recursion happens lazily inside the result `Deferred`s.
   *
   * Re-entrant: only the outermost call clears the cache.
   */
  private[schema] def withTransformCache[A](thunk: => A): A = {
    val depth = transformDepth.get.intValue
    transformDepth.set(Integer.valueOf(depth + 1))
    try thunk
    finally {
      if (depth == 0) {
        transformDepth.remove()
        transformCache.remove()
      } else transformDepth.set(Integer.valueOf(depth))
    }
  }

  /** Returns the primitive node of `Unit`. */
  def unit[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Unit] = primitive(PrimitiveType.Unit)

  /** Returns the primitive node of `Boolean`, without validation. */
  def boolean[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Boolean] =
    primitive(new PrimitiveType.Boolean(Validation.None))

  /** Returns the primitive node of `Byte`, without validation. */
  def byte[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Byte] = primitive(new PrimitiveType.Byte(Validation.None))

  /** Returns the primitive node of `Short`, without validation. */
  def short[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Short] =
    primitive(new PrimitiveType.Short(Validation.None))

  /** Returns the primitive node of `Int`, without validation. */
  def int[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Int] = primitive(new PrimitiveType.Int(Validation.None))

  /** Returns the primitive node of `Long`, without validation. */
  def long[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Long] = primitive(new PrimitiveType.Long(Validation.None))

  /** Returns the primitive node of `Float`, without validation. */
  def float[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Float] =
    primitive(new PrimitiveType.Float(Validation.None))

  /** Returns the primitive node of `Double`, without validation. */
  def double[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Double] =
    primitive(new PrimitiveType.Double(Validation.None))

  /** Returns the primitive node of `Char`, without validation. */
  def char[F[_, _]](implicit F: FromBinding[F]): Reflect[F, Char] = primitive(new PrimitiveType.Char(Validation.None))

  /** Returns the primitive node of `String`, without validation. */
  def string[F[_, _]](implicit F: FromBinding[F]): Reflect[F, String] =
    primitive(new PrimitiveType.String(Validation.None))

  /** Returns the primitive node of `BigInt`, without validation. */
  def bigInt[F[_, _]](implicit F: FromBinding[F]): Reflect[F, BigInt] =
    primitive(new PrimitiveType.BigInt(Validation.None))

  /** Returns the primitive node of `BigDecimal`, without validation. */
  def bigDecimal[F[_, _]](implicit F: FromBinding[F]): Reflect[F, BigDecimal] =
    primitive(new PrimitiveType.BigDecimal(Validation.None))

  /**
   * Returns the primitive node of `java.time.DayOfWeek`, without validation.
   */
  def dayOfWeek[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.DayOfWeek] =
    primitive(new PrimitiveType.DayOfWeek(Validation.None))

  /** Returns the primitive node of `java.time.Duration`, without validation. */
  def duration[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Duration] =
    primitive(new PrimitiveType.Duration(Validation.None))

  /** Returns the primitive node of `java.time.Instant`, without validation. */
  def instant[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Instant] =
    primitive(new PrimitiveType.Instant(Validation.None))

  /**
   * Returns the primitive node of `java.time.LocalDate`, without validation.
   */
  def localDate[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalDate] =
    primitive(new PrimitiveType.LocalDate(Validation.None))

  /**
   * Returns the primitive node of `java.time.LocalDateTime`, without
   * validation.
   */
  def localDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalDateTime] =
    primitive(new PrimitiveType.LocalDateTime(Validation.None))

  /**
   * Returns the primitive node of `java.time.LocalTime`, without validation.
   */
  def localTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.LocalTime] =
    primitive(new PrimitiveType.LocalTime(Validation.None))

  /** Returns the primitive node of `java.time.Month`, without validation. */
  def month[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Month] =
    primitive(new PrimitiveType.Month(Validation.None))

  /** Returns the primitive node of `java.time.MonthDay`, without validation. */
  def monthDay[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.MonthDay] =
    primitive(new PrimitiveType.MonthDay(Validation.None))

  /**
   * Returns the primitive node of `java.time.OffsetDateTime`, without
   * validation.
   */
  def offsetDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.OffsetDateTime] =
    primitive(new PrimitiveType.OffsetDateTime(Validation.None))

  /**
   * Returns the primitive node of `java.time.OffsetTime`, without validation.
   */
  def offsetTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.OffsetTime] =
    primitive(new PrimitiveType.OffsetTime(Validation.None))

  /** Returns the primitive node of `java.time.Period`, without validation. */
  def period[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Period] =
    primitive(new PrimitiveType.Period(Validation.None))

  private[this] def primitive[F[_, _], A](primitiveType: PrimitiveType[A])(implicit F: FromBinding[F]): Reflect[F, A] =
    new Primitive(primitiveType, primitiveType.typeId, F.fromBinding(primitiveType.binding))

  /** Returns the primitive node of `java.time.Year`, without validation. */
  def year[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.Year] =
    primitive(new PrimitiveType.Year(Validation.None))

  /**
   * Returns the primitive node of `java.time.YearMonth`, without validation.
   */
  def yearMonth[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.YearMonth] =
    primitive(new PrimitiveType.YearMonth(Validation.None))

  /** Returns the primitive node of `java.time.ZoneId`, without validation. */
  def zoneId[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZoneId] =
    primitive(new PrimitiveType.ZoneId(Validation.None))

  /**
   * Returns the primitive node of `java.time.ZoneOffset`, without validation.
   */
  def zoneOffset[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZoneOffset] =
    primitive(new PrimitiveType.ZoneOffset(Validation.None))

  /**
   * Returns the primitive node of `java.time.ZonedDateTime`, without
   * validation.
   */
  def zonedDateTime[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.time.ZonedDateTime] =
    primitive(new PrimitiveType.ZonedDateTime(Validation.None))

  /** Returns the primitive node of `java.util.Currency`, without validation. */
  def currency[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.util.Currency] =
    primitive(new PrimitiveType.Currency(Validation.None))

  /** Returns the primitive node of `java.util.UUID`, without validation. */
  def uuid[F[_, _]](implicit F: FromBinding[F]): Reflect[F, java.util.UUID] =
    primitive(new PrimitiveType.UUID(Validation.None))

  /** Returns a [[Reflect.Dynamic]] node for values of any shape. */
  def dynamic[F[_, _]](implicit F: FromBinding[F]): Dynamic[F] = new Dynamic(F.fromBinding(Binding.Dynamic()))

  private[this] def some[F[_, _], A <: AnyRef](
    element: Reflect[F, A]
  )(implicit F: FromBinding[F]): Record[F, Some[A]] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.applied[Some[A]](TypeId.some, TypeRepr.Ref(element.typeId)),
      F.fromBinding(Binding.Record.some)
    )

  private[this] def someDouble[F[_, _]](
    element: Reflect[F, Double]
  )(implicit F: FromBinding[F]): Record[F, Some[Double]] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.of[Some[Double]],
      F.fromBinding(Binding.Record.someDouble)
    )

  private[this] def someLong[F[_, _]](element: Reflect[F, Long])(implicit F: FromBinding[F]): Record[F, Some[Long]] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.of[Some[Long]],
      F.fromBinding(Binding.Record.someLong)
    )

  private[this] def someFloat[F[_, _]](element: Reflect[F, Float])(implicit F: FromBinding[F]): Record[F, Some[Float]] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.of[Some[Float]],
      F.fromBinding(Binding.Record.someFloat)
    )

  private[this] def someInt[F[_, _]](element: Reflect[F, Int])(implicit F: FromBinding[F]): Record[F, Some[Int]] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.of[Some[Int]],
      F.fromBinding(Binding.Record.someInt)
    )

  private[this] def someChar[F[_, _]](element: Reflect[F, Char])(implicit F: FromBinding[F]): Record[F, Some[Char]] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.of[Some[Char]],
      F.fromBinding(Binding.Record.someChar)
    )

  private[this] def someShort[F[_, _]](element: Reflect[F, Short])(implicit F: FromBinding[F]): Record[F, Some[Short]] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.of[Some[Short]],
      F.fromBinding(Binding.Record.someShort)
    )

  private[this] def someBoolean[F[_, _]](
    element: Reflect[F, Boolean]
  )(implicit F: FromBinding[F]): Record[F, Some[Boolean]] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.of[Some[Boolean]],
      F.fromBinding(Binding.Record.someBoolean)
    )

  private[this] def someByte[F[_, _]](element: Reflect[F, Byte])(implicit F: FromBinding[F]): Record[F, Some[Byte]] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.of[Some[Byte]],
      F.fromBinding(Binding.Record.someByte)
    )

  private[this] def someUnit[F[_, _]](element: Reflect[F, Unit])(implicit F: FromBinding[F]): Record[F, Some[Unit]] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.of[Some[Unit]],
      F.fromBinding(Binding.Record.someUnit)
    )

  private[this] def none[F[_, _]](implicit F: FromBinding[F]): Record[F, None.type] =
    new Record(Chunk.empty, TypeId.none, F.fromBinding(Binding.Record.none))

  /**
   * Returns the variant node of `Option[A]` for a reference type `A`, with the
   * cases `None` and `Some`. `Some` is a record with the single field `value`,
   * whose node is `element`.
   */
  def option[F[_, _], A <: AnyRef](element: Reflect[F, A])(implicit F: FromBinding[F]): Variant[F, Option[A]] = {
    val typeId = TypeId.applied[Option[A]](
      TypeId.option,
      TypeRepr.Ref(element.typeId)
    )
    new Variant(
      Chunk(new Term("None", none), new Term("Some", some(element))),
      typeId,
      F.fromBinding(Binding.Variant.option)
    )
  }

  /**
   * Like `option`, for `Option[Double]`, whose `Some` binding keeps the value
   * in a `Double` register.
   */
  def optionDouble[F[_, _]](element: Reflect[F, Double])(implicit F: FromBinding[F]): Variant[F, Option[Double]] =
    new Variant(
      Chunk(new Term("None", none), new Term("Some", someDouble(element))),
      TypeId.of[Option[Double]],
      F.fromBinding(Binding.Variant.option)
    )

  /**
   * Like `option`, for `Option[Long]`, whose `Some` binding keeps the value in
   * a `Long` register.
   */
  def optionLong[F[_, _]](element: Reflect[F, Long])(implicit F: FromBinding[F]): Variant[F, Option[Long]] =
    new Variant(
      Chunk(new Term("None", none), new Term("Some", someLong(element))),
      TypeId.of[Option[Long]],
      F.fromBinding(Binding.Variant.option)
    )

  /**
   * Like `option`, for `Option[Float]`, whose `Some` binding keeps the value in
   * a `Float` register.
   */
  def optionFloat[F[_, _]](element: Reflect[F, Float])(implicit F: FromBinding[F]): Variant[F, Option[Float]] =
    new Variant(
      Chunk(new Term("None", none), new Term("Some", someFloat(element))),
      TypeId.of[Option[Float]],
      F.fromBinding(Binding.Variant.option)
    )

  /**
   * Like `option`, for `Option[Int]`, whose `Some` binding keeps the value in
   * an `Int` register.
   */
  def optionInt[F[_, _]](element: Reflect[F, Int])(implicit F: FromBinding[F]): Variant[F, Option[Int]] =
    new Variant(
      Chunk(new Term("None", none), new Term("Some", someInt(element))),
      TypeId.of[Option[Int]],
      F.fromBinding(Binding.Variant.option)
    )

  /**
   * Like `option`, for `Option[Char]`, whose `Some` binding keeps the value in
   * a `Char` register.
   */
  def optionChar[F[_, _]](element: Reflect[F, Char])(implicit F: FromBinding[F]): Variant[F, Option[Char]] =
    new Variant(
      Chunk(new Term("None", none), new Term("Some", someChar(element))),
      TypeId.of[Option[Char]],
      F.fromBinding(Binding.Variant.option)
    )

  /**
   * Like `option`, for `Option[Short]`, whose `Some` binding keeps the value in
   * a `Short` register.
   */
  def optionShort[F[_, _]](element: Reflect[F, Short])(implicit F: FromBinding[F]): Variant[F, Option[Short]] =
    new Variant(
      Chunk(new Term("None", none), new Term("Some", someShort(element))),
      TypeId.of[Option[Short]],
      F.fromBinding(Binding.Variant.option)
    )

  /**
   * Like `option`, for `Option[Boolean]`, whose `Some` binding keeps the value
   * in a `Boolean` register.
   */
  def optionBoolean[F[_, _]](element: Reflect[F, Boolean])(implicit F: FromBinding[F]): Variant[F, Option[Boolean]] =
    new Variant(
      Chunk(new Term("None", none), new Term("Some", someBoolean(element))),
      TypeId.of[Option[Boolean]],
      F.fromBinding(Binding.Variant.option)
    )

  /**
   * Like `option`, for `Option[Byte]`, whose `Some` binding keeps the value in
   * a `Byte` register.
   */
  def optionByte[F[_, _]](element: Reflect[F, Byte])(implicit F: FromBinding[F]): Variant[F, Option[Byte]] =
    new Variant(
      Chunk(new Term("None", none), new Term("Some", someByte(element))),
      TypeId.of[Option[Byte]],
      F.fromBinding(Binding.Variant.option)
    )

  /** Like `option`, for `Option[Unit]`. */
  def optionUnit[F[_, _]](element: Reflect[F, Unit])(implicit F: FromBinding[F]): Variant[F, Option[Unit]] =
    new Variant(
      Chunk(new Term("None", none), new Term("Some", someUnit(element))),
      TypeId.of[Option[Unit]],
      F.fromBinding(Binding.Variant.option)
    )

  private[this] def left[F[_, _], A, B](
    left: Reflect[F, A],
    right: Reflect[F, B]
  )(implicit F: FromBinding[F]): Record[F, Left[A, B]] = {
    val typeId = TypeId.applied[Left[A, B]](
      TypeId.nominal[Left[?, ?]](
        "Left",
        Owner.fromPackagePath("scala.util"),
        List(TypeParam.covariant("A", 0), TypeParam.covariant("B", 1))
      ),
      TypeRepr.Ref(left.typeId),
      TypeRepr.Ref(right.typeId)
    )
    val register = Record.registers(Array[Reflect[F, ?]](left)).head.asInstanceOf[Register[A]]
    new Record(Chunk.single(new Term("value", left)), typeId, F.fromBinding(Binding.Record.left(register)))
  }

  private[this] def right[F[_, _], A, B](
    left: Reflect[F, A],
    right: Reflect[F, B]
  )(implicit F: FromBinding[F]): Record[F, Right[A, B]] = {
    val typeId = TypeId.applied[Right[A, B]](
      TypeId.nominal[Right[?, ?]](
        "Right",
        Owner.fromPackagePath("scala.util"),
        List(TypeParam.covariant("A", 0), TypeParam.covariant("B", 1))
      ),
      TypeRepr.Ref(left.typeId),
      TypeRepr.Ref(right.typeId)
    )
    val register = Record.registers(Array[Reflect[F, ?]](right)).head.asInstanceOf[Register[B]]
    new Record(Chunk.single(new Term("value", right)), typeId, F.fromBinding(Binding.Record.right(register)))
  }

  /**
   * Creates an `Either[A, B]` variant using the layouts described by the two
   * child reflects.
   *
   * @param left
   *   the reflect for left values
   * @param right
   *   the reflect for right values
   * @param F
   *   the conversion from runtime bindings to metadata
   * @tparam F
   *   the metadata type constructor
   * @tparam A
   *   the left value type
   * @tparam B
   *   the right value type
   * @return
   *   a variant reflect whose cases are ordered as `Left`, then `Right`
   */
  def either[F[_, _], A, B](
    left: Reflect[F, A],
    right: Reflect[F, B]
  )(implicit F: FromBinding[F]): Variant[F, Either[A, B]] =
    new Variant(
      Chunk(new Term("Left", this.left(left, right)), new Term("Right", this.right(left, right))),
      TypeId.applied[Either[A, B]](TypeId.either, TypeRepr.Ref(left.typeId), TypeRepr.Ref(right.typeId)),
      F.fromBinding(Binding.Variant.either)
    )

  private[this] val maybeTypeId: TypeId[Any] =
    TypeId
      .nominal[Any]("Maybe", Owner.fromPackagePath("zio.blocks.maybe"), List(TypeParam("A", 0, Variance.Covariant)))

  private[this] val absentTypeId: TypeId[AnyRef] =
    TypeId.nominal[AnyRef]("Absent", Owner.fromPackagePath("zio.blocks.maybe.Maybe"))

  private[this] def absentRecord[F[_, _]](implicit F: FromBinding[F]): Record[F, AnyRef] =
    new Record(Chunk.empty, absentTypeId, F.fromBinding(Binding.Record.absent))

  private[this] def presentRecord[F[_, _], A <: AnyRef](
    element: Reflect[F, A]
  )(implicit F: FromBinding[F]): Record[F, AnyRef] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.nominal[AnyRef]("Present", Owner.fromPackagePath("zio.blocks.maybe.Maybe")),
      F.fromBinding(Binding.Record.present)
    )

  private[this] def presentDoubleRecord[F[_, _]](
    element: Reflect[F, Double]
  )(implicit F: FromBinding[F]): Record[F, AnyRef] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.nominal[AnyRef]("Present", Owner.fromPackagePath("zio.blocks.maybe.Maybe")),
      F.fromBinding(Binding.Record.presentDouble)
    )

  private[this] def presentLongRecord[F[_, _]](
    element: Reflect[F, Long]
  )(implicit F: FromBinding[F]): Record[F, AnyRef] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.nominal[AnyRef]("Present", Owner.fromPackagePath("zio.blocks.maybe.Maybe")),
      F.fromBinding(Binding.Record.presentLong)
    )

  private[this] def presentFloatRecord[F[_, _]](
    element: Reflect[F, Float]
  )(implicit F: FromBinding[F]): Record[F, AnyRef] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.nominal[AnyRef]("Present", Owner.fromPackagePath("zio.blocks.maybe.Maybe")),
      F.fromBinding(Binding.Record.presentFloat)
    )

  private[this] def presentIntRecord[F[_, _]](
    element: Reflect[F, Int]
  )(implicit F: FromBinding[F]): Record[F, AnyRef] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.nominal[AnyRef]("Present", Owner.fromPackagePath("zio.blocks.maybe.Maybe")),
      F.fromBinding(Binding.Record.presentInt)
    )

  private[this] def presentCharRecord[F[_, _]](
    element: Reflect[F, Char]
  )(implicit F: FromBinding[F]): Record[F, AnyRef] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.nominal[AnyRef]("Present", Owner.fromPackagePath("zio.blocks.maybe.Maybe")),
      F.fromBinding(Binding.Record.presentChar)
    )

  private[this] def presentShortRecord[F[_, _]](
    element: Reflect[F, Short]
  )(implicit F: FromBinding[F]): Record[F, AnyRef] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.nominal[AnyRef]("Present", Owner.fromPackagePath("zio.blocks.maybe.Maybe")),
      F.fromBinding(Binding.Record.presentShort)
    )

  private[this] def presentBooleanRecord[F[_, _]](
    element: Reflect[F, Boolean]
  )(implicit F: FromBinding[F]): Record[F, AnyRef] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.nominal[AnyRef]("Present", Owner.fromPackagePath("zio.blocks.maybe.Maybe")),
      F.fromBinding(Binding.Record.presentBoolean)
    )

  private[this] def presentByteRecord[F[_, _]](
    element: Reflect[F, Byte]
  )(implicit F: FromBinding[F]): Record[F, AnyRef] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.nominal[AnyRef]("Present", Owner.fromPackagePath("zio.blocks.maybe.Maybe")),
      F.fromBinding(Binding.Record.presentByte)
    )

  private[this] def presentUnitRecord[F[_, _]](
    element: Reflect[F, Unit]
  )(implicit F: FromBinding[F]): Record[F, AnyRef] =
    new Record(
      Chunk.single(new Term("value", element)),
      TypeId.nominal[AnyRef]("Present", Owner.fromPackagePath("zio.blocks.maybe.Maybe")),
      F.fromBinding(Binding.Record.presentUnit)
    )

  /**
   * Returns the variant node of `zio.blocks.maybe.Maybe[A]` for a reference
   * type `A`, typed as a `Variant[F, AnyRef]`, with the cases `Absent` and
   * `Present`. `Present` is a record with the single field `value`, whose node
   * is `element`.
   */
  def maybe[F[_, _], A <: AnyRef](element: Reflect[F, A])(implicit F: FromBinding[F]): Variant[F, AnyRef] = {
    val typeId = TypeId.applied[AnyRef](
      maybeTypeId,
      TypeRepr.Ref(element.typeId)
    )
    new Variant(
      Chunk(new Term("Absent", absentRecord), new Term("Present", presentRecord(element))),
      typeId,
      F.fromBinding(Binding.Variant.maybe)
    )
  }

  /**
   * Like `maybe`, for `Maybe[Double]`, whose `Present` binding keeps the value
   * in a `Double` register.
   */
  def maybeDouble[F[_, _]](element: Reflect[F, Double])(implicit F: FromBinding[F]): Variant[F, AnyRef] =
    new Variant(
      Chunk(new Term("Absent", absentRecord), new Term("Present", presentDoubleRecord(element))),
      TypeId.applied[AnyRef](maybeTypeId, TypeRepr.Ref(element.typeId)),
      F.fromBinding(Binding.Variant.maybe)
    )

  /**
   * Like `maybe`, for `Maybe[Long]`, whose `Present` binding keeps the value in
   * a `Long` register.
   */
  def maybeLong[F[_, _]](element: Reflect[F, Long])(implicit F: FromBinding[F]): Variant[F, AnyRef] =
    new Variant(
      Chunk(new Term("Absent", absentRecord), new Term("Present", presentLongRecord(element))),
      TypeId.applied[AnyRef](maybeTypeId, TypeRepr.Ref(element.typeId)),
      F.fromBinding(Binding.Variant.maybe)
    )

  /**
   * Like `maybe`, for `Maybe[Float]`, whose `Present` binding keeps the value
   * in a `Float` register.
   */
  def maybeFloat[F[_, _]](element: Reflect[F, Float])(implicit F: FromBinding[F]): Variant[F, AnyRef] =
    new Variant(
      Chunk(new Term("Absent", absentRecord), new Term("Present", presentFloatRecord(element))),
      TypeId.applied[AnyRef](maybeTypeId, TypeRepr.Ref(element.typeId)),
      F.fromBinding(Binding.Variant.maybe)
    )

  /**
   * Like `maybe`, for `Maybe[Int]`, whose `Present` binding keeps the value in
   * an `Int` register.
   */
  def maybeInt[F[_, _]](element: Reflect[F, Int])(implicit F: FromBinding[F]): Variant[F, AnyRef] =
    new Variant(
      Chunk(new Term("Absent", absentRecord), new Term("Present", presentIntRecord(element))),
      TypeId.applied[AnyRef](maybeTypeId, TypeRepr.Ref(element.typeId)),
      F.fromBinding(Binding.Variant.maybe)
    )

  /**
   * Like `maybe`, for `Maybe[Char]`, whose `Present` binding keeps the value in
   * a `Char` register.
   */
  def maybeChar[F[_, _]](element: Reflect[F, Char])(implicit F: FromBinding[F]): Variant[F, AnyRef] =
    new Variant(
      Chunk(new Term("Absent", absentRecord), new Term("Present", presentCharRecord(element))),
      TypeId.applied[AnyRef](maybeTypeId, TypeRepr.Ref(element.typeId)),
      F.fromBinding(Binding.Variant.maybe)
    )

  /**
   * Like `maybe`, for `Maybe[Short]`, whose `Present` binding keeps the value
   * in a `Short` register.
   */
  def maybeShort[F[_, _]](element: Reflect[F, Short])(implicit F: FromBinding[F]): Variant[F, AnyRef] =
    new Variant(
      Chunk(new Term("Absent", absentRecord), new Term("Present", presentShortRecord(element))),
      TypeId.applied[AnyRef](maybeTypeId, TypeRepr.Ref(element.typeId)),
      F.fromBinding(Binding.Variant.maybe)
    )

  /**
   * Like `maybe`, for `Maybe[Boolean]`, whose `Present` binding keeps the value
   * in a `Boolean` register.
   */
  def maybeBoolean[F[_, _]](element: Reflect[F, Boolean])(implicit F: FromBinding[F]): Variant[F, AnyRef] =
    new Variant(
      Chunk(new Term("Absent", absentRecord), new Term("Present", presentBooleanRecord(element))),
      TypeId.applied[AnyRef](maybeTypeId, TypeRepr.Ref(element.typeId)),
      F.fromBinding(Binding.Variant.maybe)
    )

  /**
   * Like `maybe`, for `Maybe[Byte]`, whose `Present` binding keeps the value in
   * a `Byte` register.
   */
  def maybeByte[F[_, _]](element: Reflect[F, Byte])(implicit F: FromBinding[F]): Variant[F, AnyRef] =
    new Variant(
      Chunk(new Term("Absent", absentRecord), new Term("Present", presentByteRecord(element))),
      TypeId.applied[AnyRef](maybeTypeId, TypeRepr.Ref(element.typeId)),
      F.fromBinding(Binding.Variant.maybe)
    )

  /** Like `maybe`, for `Maybe[Unit]`. */
  def maybeUnit[F[_, _]](element: Reflect[F, Unit])(implicit F: FromBinding[F]): Variant[F, AnyRef] =
    new Variant(
      Chunk(new Term("Absent", absentRecord), new Term("Present", presentUnitRecord(element))),
      TypeId.applied[AnyRef](maybeTypeId, TypeRepr.Ref(element.typeId)),
      F.fromBinding(Binding.Variant.maybe)
    )

  /**
   * Returns the sequence node of `Set[A]`, whose elements are described by
   * `element`.
   */
  def set[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Set] = {
    val typeId = TypeId.applied[Set[A]](TypeId.set, TypeRepr.Ref(element.typeId))
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.set))
  }

  /**
   * Returns the sequence node of `List[A]`, whose elements are described by
   * `element`.
   */
  def list[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, List] = {
    val typeId = TypeId.applied[List[A]](TypeId.list, TypeRepr.Ref(element.typeId))
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.list))
  }

  /**
   * Returns the sequence node of `Vector[A]`, whose elements are described by
   * `element`.
   */
  def vector[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Vector] = {
    val typeId = TypeId.applied[Vector[A]](TypeId.vector, TypeRepr.Ref(element.typeId))
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.vector))
  }

  /**
   * Returns the sequence node of `IndexedSeq[A]`, whose elements are described
   * by `element`.
   */
  def indexedSeq[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, IndexedSeq] = {
    val typeId = TypeId.applied[IndexedSeq[A]](TypeId.indexedSeq, TypeRepr.Ref(element.typeId))
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.indexedSeq))
  }

  /**
   * Returns the sequence node of `Seq[A]`, whose elements are described by
   * `element`.
   */
  def seq[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Seq] = {
    val typeId = TypeId.applied[Seq[A]](TypeId.seq, TypeRepr.Ref(element.typeId))
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.seq))
  }

  /**
   * Returns the sequence node of `Chunk[A]`, whose elements are described by
   * `element`.
   */
  def chunk[F[_, _], A](element: Reflect[F, A])(implicit F: FromBinding[F]): Sequence[F, A, Chunk] = {
    val typeId = TypeId.applied[Chunk[A]](TypeId.chunk, TypeRepr.Ref(element.typeId))
    new Sequence(element, typeId, F.fromBinding(Binding.Seq.chunk))
  }

  /**
   * Returns the map node of `ChunkMap[K, V]`, with the key node `key` and the
   * value node `value`.
   */
  def chunkMap[F[_, _], K, V](key: Reflect[F, K], value: Reflect[F, V])(implicit
    F: FromBinding[F]
  ): Map[F, K, V, ChunkMap] = {
    val typeId = TypeId.applied[ChunkMap[K, V]](TypeId.chunkMap, TypeRepr.Ref(key.typeId), TypeRepr.Ref(value.typeId))
    new Map(key, value, typeId, F.fromBinding(Binding.Map.chunkMap))
  }

  /**
   * Returns the map node of an immutable `Map[K, V]`, with the key node `key`
   * and the value node `value`.
   */
  def map[F[_, _], K, V](key: Reflect[F, K], value: Reflect[F, V])(implicit
    F: FromBinding[F]
  ): Map[F, K, V, collection.immutable.Map] = {
    val typeId = TypeId.applied[collection.immutable.Map[K, V]](
      TypeId.map,
      TypeRepr.Ref(key.typeId),
      TypeRepr.Ref(value.typeId)
    )
    new Map(key, value, typeId, F.fromBinding(Binding.Map.map))
  }

  /**
   * Patterns that match the sequence node of a standard collection by its type
   * ID and return its element node.
   */
  object Extractors {

    /** Matches the sequence node of a `List`. */
    object List {

      /**
       * Returns the element node of `reflect` when it is the sequence node of a
       * `List`, or `None` otherwise.
       */
      def unapply[F[_, _], A](reflect: Reflect[F, List[A]]): Option[Reflect[F, A]] =
        reflect.asSequenceUnknown.collect {
          case x if x.sequence.typeId.name == "List" && x.sequence.typeId.owner == TypeId.list.owner =>
            x.sequence.element.asInstanceOf[Reflect[F, A]]
        }
    }

    /** Matches the sequence node of a `Vector`. */
    object Vector {

      /**
       * Returns the element node of `reflect` when it is the sequence node of a
       * `Vector`, or `None` otherwise.
       */
      def unapply[F[_, _], A](reflect: Reflect[F, Vector[A]]): Option[Reflect[F, A]] =
        reflect.asSequenceUnknown.collect {
          case x if x.sequence.typeId.name == "Vector" && x.sequence.typeId.owner == TypeId.vector.owner =>
            x.sequence.element.asInstanceOf[Reflect[F, A]]
        }
    }

    /** Matches the sequence node of a `Set`. */
    object Set {

      /**
       * Returns the element node of `reflect` when it is the sequence node of a
       * `Set`, or `None` otherwise.
       */
      def unapply[F[_, _], A](reflect: Reflect[F, Set[A]]): Option[Reflect[F, A]] =
        reflect.asSequenceUnknown.collect {
          case x if x.sequence.typeId.name == "Set" && x.sequence.typeId.owner == TypeId.set.owner =>
            x.sequence.element.asInstanceOf[Reflect[F, A]]
        }
    }
  }

  private[schema] def unwrapToPrimitiveTypeOption[F[_, _], A](reflect: Reflect[F, A]): Option[PrimitiveType[A]] =
    reflect.asWrapperUnknown match {
      case Some(unknown) =>
        val wrapper = unknown.wrapper
        wrapper.underlyingPrimitiveType.orElse {
          if (wrapper.typeId.isOpaque)
            unwrapToPrimitiveTypeOption(wrapper.wrapped).asInstanceOf[Option[PrimitiveType[A]]]
          else None
        }.asInstanceOf[Option[PrimitiveType[A]]]
      case None => reflect.asPrimitive.map(_.primitiveType)
    }

  private[blocks] def registerOffset[F[_, _], A](reflect: Reflect[F, A]): RegisterOffset.RegisterOffset =
    unwrapToPrimitiveTypeOption(reflect) match {
      case Some(primitiveType) =>
        primitiveType match {
          case _: PrimitiveType.Unit.type => 0L
          case _: PrimitiveType.Boolean   => RegisterOffset.incrementBooleansAndBytes(0L)
          case _: PrimitiveType.Byte      => RegisterOffset.incrementBooleansAndBytes(0L)
          case _: PrimitiveType.Char      => RegisterOffset.incrementCharsAndShorts(0L)
          case _: PrimitiveType.Short     => RegisterOffset.incrementCharsAndShorts(0L)
          case _: PrimitiveType.Float     => RegisterOffset.incrementFloatsAndInts(0L)
          case _: PrimitiveType.Int       => RegisterOffset.incrementFloatsAndInts(0L)
          case _: PrimitiveType.Double    => RegisterOffset.incrementDoublesAndLongs(0L)
          case _: PrimitiveType.Long      => RegisterOffset.incrementDoublesAndLongs(0L)
          case _                          => RegisterOffset.incrementObjects(0L)
        }
      case _ => RegisterOffset.incrementObjects(0L)
    }

  private[blocks] def typeTag[F[_, _], A](reflect: Reflect[F, A]): Int =
    unwrapToPrimitiveTypeOption(reflect) match {
      case Some(primitiveType) =>
        primitiveType match {
          case _: PrimitiveType.Unit.type => 9
          case _: PrimitiveType.Boolean   => 5
          case _: PrimitiveType.Byte      => 6
          case _: PrimitiveType.Char      => 7
          case _: PrimitiveType.Short     => 8
          case _: PrimitiveType.Float     => 3
          case _: PrimitiveType.Int       => 1
          case _: PrimitiveType.Double    => 4
          case _: PrimitiveType.Long      => 2
          case _                          => 0
        }
      case _ => 0
    }

  private class StringToIntMap(size: Int) {
    private[this] val mask   = (Integer.highestOneBit(size | 1) << 2) - 1
    private[this] val keys   = new Array[String](mask + 1)
    private[this] val values = new Array[Int](mask + 1)

    def put(key: String, value: Int): Unit = {
      var idx             = key.hashCode & mask
      var currKey: String = null
      while ({
        currKey = keys(idx)
        (currKey ne null) && !currKey.equals(key)
      }) idx = (idx + 1) & mask
      keys(idx) = key
      values(idx) = value
    }

    def get(key: String): Int = {
      var idx             = key.hashCode & mask
      var currKey: String = null
      while ({
        currKey = keys(idx)
        (currKey ne null) && !currKey.equals(key)
      }) idx = (idx + 1) & mask
      if (currKey eq null) -1
      else values(idx)
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // TypeSearch and SchemaSearch Helper Functions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Performs depth-first search through the type graph to find the first type
   * matching the given TypeId. Uses iterative traversal with cycle detection
   * for recursive types (Deferred nodes).
   *
   * @param root
   *   The starting Reflect node to search from
   * @param searchTypeId
   *   The TypeId to search for
   * @return
   *   Some(Reflect) if a matching type is found, None otherwise
   */
  private[schema] def typeSearch[F[_, _]](root: Reflect[F, ?], searchTypeId: TypeId[?]): Option[Reflect[F, ?]] = {
    // Thread-local visited set for cycle detection in recursive types
    val visited                                               = new java.util.IdentityHashMap[AnyRef, Unit]
    def search(current: Reflect[F, ?]): Option[Reflect[F, ?]] = {
      // Check for cycle - if we've visited this Deferred node, skip it
      current match {
        case d: Deferred[F, ?] @unchecked =>
          if (visited.containsKey(d)) return None
          visited.put(d, ())
        case _ => ()
      }
      try {
        // Check if current type matches the search TypeId
        if (current.typeId == searchTypeId) return Some(current)
        // Recurse into children based on node type
        current match {
          case d: Deferred[F, ?] @unchecked => search(d.value)
          case r: Record[F, ?] @unchecked   =>
            // Search through all field types
            val fields = r.fields
            val len    = fields.length
            var idx    = 0
            while (idx < len) {
              val result = search(fields(idx).value)
              if (result.isDefined) return result
              idx += 1
            }
            None
          case v: Variant[F, ?] @unchecked =>
            // Search through all case payload types
            val cases = v.cases
            val len   = cases.length
            var idx   = 0
            while (idx < len) {
              val result = search(cases(idx).value)
              if (result.isDefined) return result
              idx += 1
            }
            None
          case s: Sequence[F, ?, ?] @unchecked => search(s.element) // Search element type
          case m: Map[F, ?, ?, ?] @unchecked   =>
            // Search key type first, then value type
            val keyResult = search(m.key)
            if (keyResult.isDefined) keyResult
            else search(m.value)
          case w: Wrapper[F, ?, ?] @unchecked => search(w.wrapped) // Search wrapped type
          case _                              => None              // Primitives and Dynamic don't have child types to search
        }
      } finally {
        current match { // Clean up visited set for Deferred nodes
          case d: Deferred[F, ?] @unchecked => visited.remove(d)
          case _                            => ()
        }
      }
    }

    search(root)
  }

  /**
   * Performs depth-first search through the type graph to find the first type
   * matching the given SchemaRepr pattern. Uses structural matching against the
   * type structure.
   *
   * @param root
   *   The starting Reflect node to search from
   * @param pattern
   *   The SchemaRepr pattern to match against
   * @return
   *   Some(Reflect) if a matching type is found, None otherwise
   */
  private[schema] def schemaSearch[F[_, _]](root: Reflect[F, ?], pattern: SchemaRepr): Option[Reflect[F, ?]] = {
    // Thread-local visited set for cycle detection in recursive types
    val visited                                                              = new java.util.IdentityHashMap[AnyRef, Unit]
    def matchesSchemaRepr(reflect: Reflect[F, ?], repr: SchemaRepr): Boolean = repr match {
      case SchemaRepr.Wildcard       => true
      case srn: SchemaRepr.Nominal   => reflect.typeId.name == srn.name
      case srp: SchemaRepr.Primitive =>
        reflect.asPrimitive match {
          case Some(p) => primitiveTypeNameMatches(srp.name, p.primitiveType)
          case _       => false
        }
      case srr: SchemaRepr.Record =>
        reflect.asRecord match {
          case Some(r) => // Subset matching: all pattern fields must exist with matching types
            srr.fields.forall { case (patternFieldName, patternFieldType) =>
              r.fieldByName(patternFieldName).exists(field => matchesSchemaRepr(field.value, patternFieldType))
            }
          case _ => false
        }
      case srv: SchemaRepr.Variant =>
        reflect.asVariant match {
          case Some(v) => // All pattern cases must exist with matching payload types
            srv.cases.forall { case (patternCaseName, patternCaseType) =>
              v.caseByName(patternCaseName).exists(case_ => matchesSchemaRepr(case_.value, patternCaseType))
            }
          case _ => false
        }
      case srs: SchemaRepr.Sequence =>
        reflect.asSequenceUnknown match {
          case Some(s) => matchesSchemaRepr(s.sequence.element, srs.element)
          case _       => false
        }
      case srm: SchemaRepr.Map =>
        reflect.asMapUnknown match {
          case Some(m) => matchesSchemaRepr(m.map.key, srm.key) && matchesSchemaRepr(m.map.value, srm.value)
          case _       => false
        }
      case sro: SchemaRepr.Optional =>
        // Check if this is an Option type (variant with None/Some cases)
        reflect.isOption && reflect.optionInnerType.exists(inner => matchesSchemaRepr(inner, sro.inner))
    }

    def search(current: Reflect[F, ?]): Option[Reflect[F, ?]] = {
      // Check for cycle - if we've visited this Deferred node, skip it
      current match {
        case d: Deferred[F, ?] @unchecked =>
          if (visited.containsKey(d)) return None
          visited.put(d, ())
        case _ => ()
      }
      try {
        // Check if current type matches the pattern
        if (matchesSchemaRepr(current, pattern)) return Some(current)
        // Recurse into children based on node type
        current match {
          case d: Deferred[F, ?] @unchecked => search(d.value)
          case r: Record[F, ?] @unchecked   =>
            val fields = r.fields
            val len    = fields.length
            var idx    = 0
            while (idx < len) {
              val result = search(fields(idx).value)
              if (result.isDefined) return result
              idx += 1
            }
            None
          case v: Variant[F, ?] @unchecked =>
            val cases = v.cases
            val len   = cases.length
            var idx   = 0
            while (idx < len) {
              val result = search(cases(idx).value)
              if (result.isDefined) return result
              idx += 1
            }
            None
          case s: Sequence[F, ?, ?] @unchecked => search(s.element)
          case m: Map[F, ?, ?, ?] @unchecked   =>
            val keyResult = search(m.key)
            if (keyResult.isDefined) keyResult
            else search(m.value)
          case w: Wrapper[F, ?, ?] @unchecked => search(w.wrapped)
          case _                              => None
        }
      } finally {
        current match {
          case d: Deferred[F, ?] @unchecked => visited.remove(d)
          case _                            => ()
        }
      }
    }

    search(root)
  }

  /**
   * Checks if a primitive type name matches a PrimitiveType. Case-insensitive
   * comparison.
   */
  private def primitiveTypeNameMatches(name: String, pt: PrimitiveType[?]): Boolean = {
    val lowerName = name.toLowerCase
    pt match {
      case _: PrimitiveType.Unit.type      => lowerName == "unit"
      case _: PrimitiveType.Boolean        => lowerName == "boolean"
      case _: PrimitiveType.Byte           => lowerName == "byte"
      case _: PrimitiveType.Short          => lowerName == "short"
      case _: PrimitiveType.Int            => lowerName == "int"
      case _: PrimitiveType.Long           => lowerName == "long"
      case _: PrimitiveType.Float          => lowerName == "float"
      case _: PrimitiveType.Double         => lowerName == "double"
      case _: PrimitiveType.Char           => lowerName == "char"
      case _: PrimitiveType.String         => lowerName == "string"
      case _: PrimitiveType.BigInt         => lowerName == "bigint"
      case _: PrimitiveType.BigDecimal     => lowerName == "bigdecimal"
      case _: PrimitiveType.DayOfWeek      => lowerName == "dayofweek"
      case _: PrimitiveType.Duration       => lowerName == "duration"
      case _: PrimitiveType.Instant        => lowerName == "instant"
      case _: PrimitiveType.LocalDate      => lowerName == "localdate"
      case _: PrimitiveType.LocalDateTime  => lowerName == "localdatetime"
      case _: PrimitiveType.LocalTime      => lowerName == "localtime"
      case _: PrimitiveType.Month          => lowerName == "month"
      case _: PrimitiveType.MonthDay       => lowerName == "monthday"
      case _: PrimitiveType.OffsetDateTime => lowerName == "offsetdatetime"
      case _: PrimitiveType.OffsetTime     => lowerName == "offsettime"
      case _: PrimitiveType.Period         => lowerName == "period"
      case _: PrimitiveType.Year           => lowerName == "year"
      case _: PrimitiveType.YearMonth      => lowerName == "yearmonth"
      case _: PrimitiveType.ZoneId         => lowerName == "zoneid"
      case _: PrimitiveType.ZoneOffset     => lowerName == "zoneoffset"
      case _: PrimitiveType.ZonedDateTime  => lowerName == "zoneddatetime"
      case _: PrimitiveType.Currency       => lowerName == "currency"
      case _: PrimitiveType.UUID           => lowerName == "uuid"
    }
  }
}
