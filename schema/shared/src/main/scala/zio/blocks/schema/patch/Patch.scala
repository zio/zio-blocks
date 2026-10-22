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

package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding

/**
 * A `Patch[S]` is a typed list of changes to a value of type `S`: set a field,
 * add to a number, edit a string, insert into a sequence, add a map key, and so
 * on. Build one with the constructors in the companion, which point at a part
 * of `S` through an optic, or compute one with
 * [[zio.blocks.schema.Schema#diff]]. Combine patches with `++` and apply them
 * with `apply`.
 *
 * A patch is data: [[dynamicPatch]] holds its operations as a [[DynamicPatch]],
 * which has a schema, so it can be stored or sent elsewhere and applied later.
 * Operations address record fields by name and sequence elements by index, and
 * no [[PatchMode]] checks that the value still holds what it held when the
 * patch was made. The constructor is not public, and on Scala 3 nothing turns a
 * stored `DynamicPatch` back into a `Patch[S]`: apply it to
 * `schema.toDynamicValue(value)` instead (see `docs/dev/issues.md`).
 *
 * {{{
 * import zio.blocks.schema.patch._
 *
 * case class Book(title: String, edition: Int, tags: Vector[String])
 * object Book extends CompanionOptics[Book] {
 *   implicit val schema: Schema[Book] = Schema.derived
 *   val title: Lens[Book, String]        = $(_.title)
 *   val edition: Lens[Book, Int]         = $(_.edition)
 *   val tags: Lens[Book, Vector[String]] = $(_.tags)
 * }
 *
 * val secondEdition = Patch.set(Book.title, "Zionomicon, Second Edition") ++ Patch.increment(Book.edition, 1)
 * secondEdition(Book("Zionomicon", 1, Vector("zio")))
 * // Book("Zionomicon, Second Edition", 2, Vector("zio"))
 *
 * val diff = Book.schema.diff(Book("Zionomicon", 1, Vector("zio")), Book("Zionomicon", 2, Vector("zio", "scala")))
 * // DynamicPatch {
 * //   .edition += 1
 * //   .tags:
 * //     + "scala"
 * // }
 * }}}
 *
 * @param dynamicPatch
 *   the operations, without the type
 * @param schema
 *   the schema that converts values to and from
 *   [[zio.blocks.schema.DynamicValue]] when the patch is applied
 * @see
 *   [[https://zioblocks.com/reference/schema/patch/ Patching]]
 */
final case class Patch[S] private[schema] (dynamicPatch: DynamicPatch, schema: Schema[S]) {

  /**
   * Returns a patch that applies the operations of this patch, then those of
   * `that`. Order matters when both touch the same part: a later `set`
   * overwrites an earlier increment. The result keeps this patch's schema.
   *
   * {{{
   * import zio.blocks.schema.patch._
   *
   * case class Book(title: String, edition: Int, tags: Vector[String])
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val title: Lens[Book, String]        = $(_.title)
   *   val edition: Lens[Book, Int]         = $(_.edition)
   *   val tags: Lens[Book, Vector[String]] = $(_.tags)
   * }
   *
   * val book = Book("Zionomicon", 1, Vector("zio"))
   * (Patch.set(Book.edition, 2) ++ Patch.increment(Book.edition, 1))(book) // Book("Zionomicon", 3, Vector("zio"))
   * (Patch.increment(Book.edition, 1) ++ Patch.set(Book.edition, 2))(book) // Book("Zionomicon", 2, Vector("zio"))
   * }}}
   */
  def ++(that: Patch[S]): Patch[S] = Patch(this.dynamicPatch ++ that.dynamicPatch, this.schema)

  /**
   * Applies this patch to `value` in `mode`. It converts `value` to a
   * [[zio.blocks.schema.DynamicValue]] with [[schema]], applies
   * [[DynamicPatch#apply]], and converts the result back. `Strict` returns the
   * error of the first operation whose precondition fails; `Lenient` and
   * `Clobber` skip such operations (see [[PatchMode]]). In every mode the
   * result is a `Left` when the patched value cannot be converted back to an
   * `S`.
   *
   * {{{
   * import zio.blocks.schema.patch._
   *
   * case class Book(title: String, edition: Int, tags: Vector[String])
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val title: Lens[Book, String]        = $(_.title)
   *   val edition: Lens[Book, Int]         = $(_.edition)
   *   val tags: Lens[Book, Vector[String]] = $(_.tags)
   * }
   *
   * val book          = Book("Zionomicon", 1, Vector("zio"))
   * val dropSecondTag = Patch.deleteAt(Book.tags, 1, 1)
   *
   * dropSecondTag(book, PatchMode.Strict)
   * // Left(SchemaError: Delete range [1, 2) out of bounds for sequence of length 1 at: .tags)
   * dropSecondTag(book, PatchMode.Lenient) // Right(Book("Zionomicon", 1, Vector("zio")))
   * }}}
   */
  def apply(value: S, mode: PatchMode): Either[SchemaError, S] =
    dynamicPatch(schema.toDynamicValue(value), mode).flatMap(schema.fromDynamicValue)

  /**
   * Applies this patch to `s` in `Lenient` mode: operations whose precondition
   * fails are skipped and the others applied. Returns `s` unchanged only when
   * the patched value cannot be converted back to an `S`. Use [[applyOption]]
   * or the overload that takes a [[PatchMode]] to learn whether every operation
   * applied.
   *
   * {{{
   * import zio.blocks.schema.patch._
   *
   * case class Book(title: String, edition: Int, tags: Vector[String])
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val title: Lens[Book, String]        = $(_.title)
   *   val edition: Lens[Book, Int]         = $(_.edition)
   *   val tags: Lens[Book, Vector[String]] = $(_.tags)
   * }
   *
   * val book = Book("Zionomicon", 1, Vector("zio"))
   * (Patch.deleteAt(Book.tags, 1, 1) ++ Patch.append(Book.tags, Vector("scala")))(book)
   * // Book("Zionomicon", 1, Vector("zio", "scala")): the out-of-range delete is skipped
   * }}}
   */
  def apply(s: S): S = dynamicPatch(schema.toDynamicValue(s), PatchMode.Lenient) match {
    case Right(patched) =>
      schema.fromDynamicValue(patched) match {
        case Right(result) => result
        case _             => s // Conversion failed, return original
      }
    case _ => s // Patch failed, return original
  }

  /**
   * Applies this patch to `s` in `Strict` mode, returning `None` when any
   * operation fails or the patched value cannot be converted back to an `S`.
   */
  def applyOption(s: S): Option[S] = dynamicPatch(schema.toDynamicValue(s), PatchMode.Strict) match {
    case Right(patched) =>
      schema.fromDynamicValue(patched) match {
        case Right(result) => new Some(result)
        case _             => None
      }
    case _ => None
  }

  /**
   * Returns `true` when this patch has no operations, so applying it changes
   * nothing.
   */
  def isEmpty: Boolean = dynamicPatch.isEmpty

  override def toString: String = dynamicPatch.toString
}

/**
 * Constructors for [[Patch]]. Each takes an optic into `S`, which says where
 * the change applies, and returns a patch with the optic's source schema. How
 * an operation behaves when its precondition fails depends on the [[PatchMode]]
 * it is applied in.
 */
object Patch {

  /**
   * Returns the patch with no operations. Composing it with `++` on either side
   * changes nothing.
   */
  def empty[S](implicit schema: Schema[S]): Patch[S] = new Patch(DynamicPatch.empty, schema)

  /**
   * Sets the part of `S` that `optic` focuses on to `value`. A traversal sets
   * every element; on an empty sequence it fails under `Strict` and changes
   * nothing in the other modes. A prism or an optional sets the value only when
   * the path exists, for example when the value is in the prism's case;
   * otherwise `Strict` fails and the other modes change nothing. A prism never
   * switches a value from one case to another.
   */
  def set[S, A](optic: Optic[S, A], value: A): Patch[S] = {
    val dynamicValue = new Schema(optic.focus).toDynamicValue(value)
    val op           = new Patch.DynamicPatchOp(optic.toDynamic, new Patch.Operation.Set(dynamicValue))
    toDynamicPatch(optic, Chunk.single(op))
  }

  /**
   * Sets the field that `lens` focuses on to `value`.
   *
   * {{{
   * import zio.blocks.schema.patch._
   *
   * case class Book(title: String, edition: Int, tags: Vector[String])
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val title: Lens[Book, String]        = $(_.title)
   *   val edition: Lens[Book, Int]         = $(_.edition)
   *   val tags: Lens[Book, Vector[String]] = $(_.tags)
   * }
   *
   * Patch.set(Book.edition, 2)(Book("Zionomicon", 1, Vector("zio"))) // Book("Zionomicon", 2, Vector("zio"))
   * }}}
   */
  def set[S, A](lens: Lens[S, A], value: A): Patch[S] = set(lens: Optic[S, A], value)

  /**
   * Sets the value that `optional` focuses on to `value`, when the path exists.
   * Otherwise `Strict` fails and the other modes change nothing.
   */
  def set[S, A](optional: Optional[S, A], value: A): Patch[S] = set(optional: Optic[S, A], value)

  /**
   * Sets every element that `traversal` focuses on to `value`. On an empty
   * sequence, `Strict` fails and the other modes change nothing.
   */
  def set[S, A](traversal: Traversal[S, A], value: A): Patch[S] = set(traversal: Optic[S, A], value)

  /**
   * Replaces the value with `value` when it is already in the case that `prism`
   * focuses on. When it is in another case, `Strict` fails and the other modes
   * change nothing: the patch never switches cases.
   */
  def set[S, A <: S](prism: Prism[S, A], value: A): Patch[S] = set(prism: Optic[S, A], value)

  /**
   * Appends `elements` to the end of a `Vector` field. It never fails on a
   * sequence.
   */
  def append[S, A](optic: Optic[S, Vector[A]], elements: Vector[A])(implicit
    d: CollectionDummy.ForVector.type
  ): Patch[S] = {
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, Vector]]
    val dynamicElements = Chunk.from(elements.map(new Schema(seqReflect.element).toDynamicValue))
    toSequenceEditPatch(optic, new Patch.SeqOp.Append(dynamicElements))
  }

  /**
   * Appends `elements` to the end of a `List` field. It never fails on a
   * sequence.
   */
  def append[S, A](optic: Optic[S, List[A]], elements: List[A])(implicit d: CollectionDummy.ForList.type): Patch[S] = {
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, List]]
    val dynamicElements = Chunk.from(elements.map(new Schema(seqReflect.element).toDynamicValue))
    toSequenceEditPatch(optic, new Patch.SeqOp.Append(dynamicElements))
  }

  /**
   * Appends `elements` to the end of a `Seq` field. It never fails on a
   * sequence.
   */
  def append[S, A](optic: Optic[S, Seq[A]], elements: Seq[A])(implicit d: CollectionDummy.ForSeq.type): Patch[S] = {
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, Seq]]
    val dynamicElements = Chunk.from(elements.map(new Schema(seqReflect.element).toDynamicValue))
    toSequenceEditPatch(optic, new Patch.SeqOp.Append(dynamicElements))
  }

  /**
   * Appends `elements` to the end of an `IndexedSeq` field. It never fails on a
   * sequence.
   */
  def append[S, A](optic: Optic[S, IndexedSeq[A]], elements: IndexedSeq[A])(implicit
    d: CollectionDummy.ForIndexedSeq.type
  ): Patch[S] = {
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, IndexedSeq]]
    val dynamicElements = Chunk.from(elements.map(new Schema(seqReflect.element).toDynamicValue))
    toSequenceEditPatch(optic, new Patch.SeqOp.Append(dynamicElements))
  }

  /**
   * Appends `elements` to the end of a `LazyList` field. It never fails on a
   * sequence. `Schema.derived` cannot derive a schema for a case class with a
   * `LazyList` field.
   */
  def append[S, A](optic: Optic[S, LazyList[A]], elements: LazyList[A])(implicit
    d: CollectionDummy.ForLazyList.type
  ): Patch[S] = {
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, LazyList]]
    val dynamicElements = Chunk.from(elements.map(new Schema(seqReflect.element).toDynamicValue))
    toSequenceEditPatch(optic, new Patch.SeqOp.Append(dynamicElements))
  }

  /**
   * Inserts `elements` into a `Vector` field before the element at `index`; an
   * `index` equal to the length appends. An index outside `[0, length]` fails
   * under `Strict`, is skipped under `Lenient`, and is clamped under `Clobber`,
   * where a negative index inserts at the start and one past the end appends.
   */
  def insertAt[S, A](optic: Optic[S, Vector[A]], index: Int, elements: Vector[A])(implicit
    d: CollectionDummy.ForVector.type
  ): Patch[S] = {
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, Vector]]
    val dynamicElements = Chunk.from(elements.map(new Schema(seqReflect.element).toDynamicValue))
    toSequenceEditPatch(optic, new Patch.SeqOp.Insert(index, dynamicElements))
  }

  /**
   * Inserts `elements` into a `List` field before the element at `index`; an
   * `index` equal to the length appends. An index outside `[0, length]` fails
   * under `Strict`, is skipped under `Lenient`, and is clamped under `Clobber`,
   * where a negative index inserts at the start and one past the end appends.
   */
  def insertAt[S, A](optic: Optic[S, List[A]], index: Int, elements: List[A])(implicit
    d: CollectionDummy.ForList.type
  ): Patch[S] = {
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, List]]
    val dynamicElements = Chunk.from(elements.map(new Schema(seqReflect.element).toDynamicValue))
    toSequenceEditPatch(optic, new Patch.SeqOp.Insert(index, dynamicElements))
  }

  /**
   * Inserts `elements` into a `Seq` field before the element at `index`; an
   * `index` equal to the length appends. An index outside `[0, length]` fails
   * under `Strict`, is skipped under `Lenient`, and is clamped under `Clobber`,
   * where a negative index inserts at the start and one past the end appends.
   */
  def insertAt[S, A](optic: Optic[S, Seq[A]], index: Int, elements: Seq[A])(implicit
    d: CollectionDummy.ForSeq.type
  ): Patch[S] = {
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, Seq]]
    val dynamicElements = Chunk.from(elements.map(new Schema(seqReflect.element).toDynamicValue))
    toSequenceEditPatch(optic, new Patch.SeqOp.Insert(index, dynamicElements))
  }

  /**
   * Inserts `elements` into an `IndexedSeq` field before the element at
   * `index`; an `index` equal to the length appends. An index outside
   * `[0, length]` fails under `Strict`, is skipped under `Lenient`, and is
   * clamped under `Clobber`, where a negative index inserts at the start and
   * one past the end appends.
   */
  def insertAt[S, A](optic: Optic[S, IndexedSeq[A]], index: Int, elements: IndexedSeq[A])(implicit
    d: CollectionDummy.ForIndexedSeq.type
  ): Patch[S] = {
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, IndexedSeq]]
    val dynamicElements = Chunk.from(elements.map(new Schema(seqReflect.element).toDynamicValue))
    toSequenceEditPatch(optic, new Patch.SeqOp.Insert(index, dynamicElements))
  }

  /**
   * Inserts `elements` into a `LazyList` field before the element at `index`;
   * an `index` equal to the length appends. An index outside `[0, length]`
   * fails under `Strict`, is skipped under `Lenient`, and is clamped under
   * `Clobber`, where a negative index inserts at the start and one past the end
   * appends. `Schema.derived` cannot derive a schema for a case class with a
   * `LazyList` field.
   */
  def insertAt[S, A](optic: Optic[S, LazyList[A]], index: Int, elements: LazyList[A])(implicit
    d: CollectionDummy.ForLazyList.type
  ): Patch[S] = {
    val seqReflect      = optic.focus.asInstanceOf[Reflect.Sequence[Binding, A, LazyList]]
    val dynamicElements = Chunk.from(elements.map(new Schema(seqReflect.element).toDynamicValue))
    toSequenceEditPatch(optic, new Patch.SeqOp.Insert(index, dynamicElements))
  }

  /**
   * Deletes `count` elements of a `Vector` field, starting at `index`. A range
   * that is not inside the sequence fails under `Strict`, is skipped under
   * `Lenient`, and is clamped under `Clobber`, which deletes only the elements
   * inside the range.
   */
  def deleteAt[S, A](optic: Optic[S, Vector[A]], index: Int, count: Int)(implicit
    d: CollectionDummy.ForVector.type
  ): Patch[S] = toSequenceEditPatch(optic, new Patch.SeqOp.Delete(index, count))

  /**
   * Deletes `count` elements of a `List` field, starting at `index`. A range
   * that is not inside the sequence fails under `Strict`, is skipped under
   * `Lenient`, and is clamped under `Clobber`, which deletes only the elements
   * inside the range.
   */
  def deleteAt[S, A](optic: Optic[S, List[A]], index: Int, count: Int)(implicit
    d: CollectionDummy.ForList.type
  ): Patch[S] = toSequenceEditPatch(optic, new Patch.SeqOp.Delete(index, count))

  /**
   * Deletes `count` elements of a `Seq` field, starting at `index`. A range
   * that is not inside the sequence fails under `Strict`, is skipped under
   * `Lenient`, and is clamped under `Clobber`, which deletes only the elements
   * inside the range.
   */
  def deleteAt[S, A](optic: Optic[S, Seq[A]], index: Int, count: Int)(implicit
    d: CollectionDummy.ForSeq.type
  ): Patch[S] = toSequenceEditPatch(optic, new Patch.SeqOp.Delete(index, count))

  /**
   * Deletes `count` elements of an `IndexedSeq` field, starting at `index`. A
   * range that is not inside the sequence fails under `Strict`, is skipped
   * under `Lenient`, and is clamped under `Clobber`, which deletes only the
   * elements inside the range.
   */
  def deleteAt[S, A](optic: Optic[S, IndexedSeq[A]], index: Int, count: Int)(implicit
    d: CollectionDummy.ForIndexedSeq.type
  ): Patch[S] = toSequenceEditPatch(optic, new Patch.SeqOp.Delete(index, count))

  /**
   * Deletes `count` elements of a `LazyList` field, starting at `index`. A
   * range that is not inside the sequence fails under `Strict`, is skipped
   * under `Lenient`, and is clamped under `Clobber`, which deletes only the
   * elements inside the range. `Schema.derived` cannot derive a schema for a
   * case class with a `LazyList` field.
   */
  def deleteAt[S, A](optic: Optic[S, LazyList[A]], index: Int, count: Int)(implicit
    d: CollectionDummy.ForLazyList.type
  ): Patch[S] = toSequenceEditPatch(optic, new Patch.SeqOp.Delete(index, count))

  private[this] def toSequenceEditPatch[S, F](optic: Optic[S, F], seqOp: DynamicPatch.SeqOp): Patch[S] = {
    val op = new Patch.DynamicPatchOp(optic.toDynamic, new Patch.Operation.SequenceEdit(Chunk.single(seqOp)))
    toDynamicPatch(optic, Chunk.single(op))
  }

  /**
   * Applies `elementPatch` to the element of a `Vector` field at `index`. An
   * index out of range fails under `Strict` and is skipped in the other modes,
   * `Clobber` included. An empty `elementPatch` gives an empty patch.
   */
  def modifyAt[S, A](optic: Optic[S, Vector[A]], index: Int, elementPatch: Patch[A])(implicit
    d: CollectionDummy.ForVector.type
  ): Patch[S] = {
    val nestedOps = elementPatch.dynamicPatch.ops
    if (nestedOps.isEmpty) toEmptyPatch(optic)
    else {
      // Combine paths: basePath + AtIndex(index) + each nested operation's path
      val combinedOps = nestedOps.map { nestedOp =>
        // Build the full path: sequence field -> index -> nested path
        val fullPath =
          new DynamicOptic(optic.toDynamic.nodes.appended(new DynamicOptic.Node.AtIndex(index)) ++ nestedOp.path.nodes)
        new Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      toDynamicPatch(optic, combinedOps)
    }
  }

  /**
   * Applies `elementPatch` to the element of a `List` field at `index`. An
   * index out of range fails under `Strict` and is skipped in the other modes,
   * `Clobber` included. An empty `elementPatch` gives an empty patch.
   */
  def modifyAt[S, A](optic: Optic[S, List[A]], index: Int, elementPatch: Patch[A])(implicit
    d: CollectionDummy.ForList.type
  ): Patch[S] = {
    val nestedOps = elementPatch.dynamicPatch.ops
    if (nestedOps.isEmpty) toEmptyPatch(optic)
    else {
      val combinedOps = nestedOps.map { nestedOp =>
        val fullPath =
          new DynamicOptic(optic.toDynamic.nodes.appended(new DynamicOptic.Node.AtIndex(index)) ++ nestedOp.path.nodes)
        new Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      toDynamicPatch(optic, combinedOps)
    }
  }

  /**
   * Applies `elementPatch` to the element of a `Seq` field at `index`. An index
   * out of range fails under `Strict` and is skipped in the other modes,
   * `Clobber` included. An empty `elementPatch` gives an empty patch.
   */
  def modifyAt[S, A](optic: Optic[S, Seq[A]], index: Int, elementPatch: Patch[A])(implicit
    d: CollectionDummy.ForSeq.type
  ): Patch[S] = {
    val nestedOps = elementPatch.dynamicPatch.ops
    if (nestedOps.isEmpty) toEmptyPatch(optic)
    else {
      val combinedOps = nestedOps.map { nestedOp =>
        val fullPath =
          new DynamicOptic(optic.toDynamic.nodes.appended(new DynamicOptic.Node.AtIndex(index)) ++ nestedOp.path.nodes)
        new Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      toDynamicPatch(optic, combinedOps)
    }
  }

  /**
   * Applies `elementPatch` to the element of an `IndexedSeq` field at `index`.
   * An index out of range fails under `Strict` and is skipped in the other
   * modes, `Clobber` included. An empty `elementPatch` gives an empty patch.
   */
  def modifyAt[S, A](optic: Optic[S, IndexedSeq[A]], index: Int, elementPatch: Patch[A])(implicit
    d: CollectionDummy.ForIndexedSeq.type
  ): Patch[S] = {
    val nestedOps = elementPatch.dynamicPatch.ops
    if (nestedOps.isEmpty) toEmptyPatch(optic)
    else {
      val combinedOps = nestedOps.map { nestedOp =>
        val fullPath =
          new DynamicOptic(optic.toDynamic.nodes.appended(new DynamicOptic.Node.AtIndex(index)) ++ nestedOp.path.nodes)
        new Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      toDynamicPatch(optic, combinedOps)
    }
  }

  /**
   * Applies `elementPatch` to the element of a `LazyList` field at `index`. An
   * index out of range fails under `Strict` and is skipped in the other modes,
   * `Clobber` included. An empty `elementPatch` gives an empty patch.
   * `Schema.derived` cannot derive a schema for a case class with a `LazyList`
   * field.
   */
  def modifyAt[S, A](optic: Optic[S, LazyList[A]], index: Int, elementPatch: Patch[A])(implicit
    d: CollectionDummy.ForLazyList.type
  ): Patch[S] = {
    val nestedOps = elementPatch.dynamicPatch.ops
    if (nestedOps.isEmpty) toEmptyPatch(optic)
    else {
      val combinedOps = nestedOps.map { nestedOp =>
        val fullPath =
          new DynamicOptic(optic.toDynamic.nodes.appended(new DynamicOptic.Node.AtIndex(index)) ++ nestedOp.path.nodes)
        new Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      toDynamicPatch(optic, combinedOps)
    }
  }

  /**
   * Adds `key` with `value` to the map. When the key is already present,
   * `Strict` fails, `Lenient` skips the operation and `Clobber` replaces the
   * value.
   */
  def addKey[S, K, V](optic: Optic[S, Map[K, V]], key: K, value: V): Patch[S] = {
    val mapReflect   = optic.focus.asInstanceOf[Reflect.Map[Binding, K, V, Map]]
    val dynamicKey   = new Schema(mapReflect.key).toDynamicValue(key)
    val dynamicValue = new Schema(mapReflect.value).toDynamicValue(value)
    val mapOp        = new Patch.MapOp.Add(dynamicKey, dynamicValue)
    val op           = new Patch.DynamicPatchOp(optic.toDynamic, new Patch.Operation.MapEdit(Chunk.single(mapOp)))
    toDynamicPatch(optic, Chunk.single(op))
  }

  /**
   * Removes `key` from the map. When the key is missing, `Strict` fails and the
   * other modes leave the map unchanged. The error does not name the key.
   */
  def removeKey[S, K, V](optic: Optic[S, Map[K, V]], key: K): Patch[S] = {
    val mapReflect = optic.focus.asInstanceOf[Reflect.Map[Binding, K, V, Map]]
    val dynamicKey = new Schema(mapReflect.key).toDynamicValue(key)
    val mapOp      = new Patch.MapOp.Remove(dynamicKey)
    val op         = new Patch.DynamicPatchOp(optic.toDynamic, new Patch.Operation.MapEdit(Chunk.single(mapOp)))
    toDynamicPatch(optic, Chunk.single(op))
  }

  /**
   * Applies `valuePatch` to the value at `key`. A missing key fails under
   * `Strict` and is skipped in the other modes, `Clobber` included. An empty
   * `valuePatch` gives an empty patch.
   */
  def modifyKey[S, K, V](optic: Optic[S, Map[K, V]], key: K, valuePatch: Patch[V]): Patch[S] = {
    val mapReflect = optic.focus.asInstanceOf[Reflect.Map[Binding, K, V, Map]]
    val dynamicKey = new Schema(mapReflect.key).toDynamicValue(key)
    val nestedOps  = valuePatch.dynamicPatch.ops
    if (nestedOps.isEmpty) toEmptyPatch(optic) // Empty patch - return empty
    else {
      // Combine paths: basePath + AtMapKey(key) + each nested operation's path
      val combinedOps = nestedOps.map { nestedOp =>
        // Build the full path: map field -> key -> nested path
        val fullPath =
          new DynamicOptic(
            optic.toDynamic.nodes.appended(new DynamicOptic.Node.AtMapKey(dynamicKey)) ++ nestedOp.path.nodes
          )
        new Patch.DynamicPatchOp(fullPath, nestedOp.operation)
      }
      toDynamicPatch(optic, combinedOps)
    }
  }

  private[this] def toEmptyPatch[S, F](optic: Optic[S, F]): Patch[S] = Patch.empty[S](new Schema(optic.source))

  /**
   * Adds `delta` to an `Int`. The sum wraps around on overflow, as `Int`
   * addition does. Unlike a `set`, increments from two sources can be applied
   * in either order with the same result.
   *
   * {{{
   * import zio.blocks.schema.patch._
   *
   * case class Book(title: String, edition: Int, tags: Vector[String])
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val title: Lens[Book, String]        = $(_.title)
   *   val edition: Lens[Book, Int]         = $(_.edition)
   *   val tags: Lens[Book, Vector[String]] = $(_.tags)
   * }
   *
   * Patch.increment(Book.edition, 1)(Book("Zionomicon", 1, Vector("zio"))) // Book("Zionomicon", 2, Vector("zio"))
   * }}}
   */
  def increment[S](optic: Optic[S, Int], delta: Int): Patch[S] =
    toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.IntDelta(delta))

  /** Adds `delta` to a `Long`. The sum wraps around on overflow. */
  def increment[S](optic: Optic[S, Long], delta: Long): Patch[S] =
    toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.LongDelta(delta))

  /** Adds `delta` to a `Double`. */
  def increment[S](optic: Optic[S, Double], delta: Double): Patch[S] =
    toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.DoubleDelta(delta))

  /** Adds `delta` to a `Float`. */
  def increment[S](optic: Optic[S, Float], delta: Float): Patch[S] =
    toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.FloatDelta(delta))

  /** Adds `delta` to a `Short`. The sum wraps around on overflow. */
  def increment[S](optic: Optic[S, Short], delta: Short): Patch[S] =
    toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.ShortDelta(delta))

  /** Adds `delta` to a `Byte`. The sum wraps around on overflow. */
  def increment[S](optic: Optic[S, Byte], delta: Byte): Patch[S] =
    toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.ByteDelta(delta))

  /** Adds `delta` to a `BigInt`. */
  def increment[S](optic: Optic[S, BigInt], delta: BigInt): Patch[S] =
    toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.BigIntDelta(delta))

  /** Adds `delta` to a `BigDecimal`. */
  def increment[S](optic: Optic[S, BigDecimal], delta: BigDecimal): Patch[S] =
    toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.BigDecimalDelta(delta))

  /** Adds `duration` to an `Instant`. */
  def addDuration[S](optic: Optic[S, java.time.Instant], duration: java.time.Duration): Patch[S] =
    toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.InstantDelta(duration))

  /** Adds `duration` to a `Duration`. */
  def addDuration[S](optic: Optic[S, java.time.Duration], duration: java.time.Duration)(implicit
    d: DurationDummy.ForDuration.type
  ): Patch[S] = toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.DurationDelta(duration))

  /** Adds `period` to a `LocalDate`. */
  def addPeriod[S](optic: Optic[S, java.time.LocalDate], period: java.time.Period): Patch[S] =
    toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.LocalDateDelta(period))

  /** Adds `period`, then `duration`, to a `LocalDateTime`. */
  def addPeriodAndDuration[S](
    optic: Optic[S, java.time.LocalDateTime],
    period: java.time.Period,
    duration: java.time.Duration
  ): Patch[S] = toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.LocalDateTimeDelta(period, duration))

  /** Adds `period` to a `Period`, field by field (years, months and days). */
  def addPeriod[S](optic: Optic[S, java.time.Period], period: java.time.Period)(implicit
    d: PeriodDummy.ForPeriod.type
  ): Patch[S] = toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.PeriodDelta(period))

  /**
   * Applies `edits` to a string in order; each index refers to the string as
   * the previous edit left it. The edits form one operation: when any of them
   * is out of range, `Strict` fails and the other modes leave the string
   * unchanged, the in-range edits included.
   */
  def editString[S](optic: Optic[S, String], edits: Chunk[Patch.StringOp]): Patch[S] =
    toPrimitiveDeltaPatch(optic, new Patch.PrimitiveOp.StringEdit(edits))

  private[this] def toPrimitiveDeltaPatch[S, F](optic: Optic[S, F], pop: DynamicPatch.PrimitiveOp): Patch[S] = {
    val op = new Patch.DynamicPatchOp(optic.toDynamic, new Patch.Operation.PrimitiveDelta(pop))
    new Patch(new DynamicPatch(Chunk.single(op)), new Schema(optic.source))
  }

  private[this] def toDynamicPatch[S, F](optic: Optic[S, F], ops: Chunk[DynamicPatchOp]): Patch[S] =
    new Patch(new DynamicPatch(ops), new Schema(optic.source))

  /** Same as `set` with `lens`. */
  def replace[S, A](lens: Lens[S, A], a: A): Patch[S] = set(lens, a)

  /** Same as `set` with `optional`. */
  def replace[S, A](optional: Optional[S, A], a: A): Patch[S] = set(optional, a)

  /** Same as `set` with `traversal`. */
  def replace[S, A](traversal: Traversal[S, A], a: A): Patch[S] = set(traversal, a)

  /** Same as `set` with `prism`: it never switches cases. */
  def replace[S, A <: S](prism: Prism[S, A], a: A): Patch[S] = set(prism, a)

  // Type aliases for convenience
  /** Alias of [[DynamicPatch.DurationDummy]]. */
  type DurationDummy = DynamicPatch.DurationDummy

  /** The companion of [[DynamicPatch.DurationDummy]]. */
  val DurationDummy = DynamicPatch.DurationDummy

  /** Alias of [[DynamicPatch.PeriodDummy]]. */
  type PeriodDummy = DynamicPatch.PeriodDummy

  /** The companion of [[DynamicPatch.PeriodDummy]]. */
  val PeriodDummy = DynamicPatch.PeriodDummy

  /** Alias of [[DynamicPatch.CollectionDummy]]. */
  type CollectionDummy = DynamicPatch.CollectionDummy

  /** The companion of [[DynamicPatch.CollectionDummy]]. */
  val CollectionDummy = DynamicPatch.CollectionDummy

  /** Alias of [[DynamicPatch.DynamicPatchOp]]. */
  type DynamicPatchOp = DynamicPatch.DynamicPatchOp

  /** The companion of [[DynamicPatch.DynamicPatchOp]]. */
  val DynamicPatchOp = DynamicPatch.DynamicPatchOp

  /** Alias of [[DynamicPatch.Operation]]. */
  type Operation = DynamicPatch.Operation

  /** The companion of [[DynamicPatch.Operation]]. */
  val Operation = DynamicPatch.Operation

  /** Alias of [[DynamicPatch.PrimitiveOp]]. */
  type PrimitiveOp = DynamicPatch.PrimitiveOp

  /** The companion of [[DynamicPatch.PrimitiveOp]]. */
  val PrimitiveOp = DynamicPatch.PrimitiveOp

  /** Alias of [[DynamicPatch.SeqOp]]. */
  type SeqOp = DynamicPatch.SeqOp

  /** The companion of [[DynamicPatch.SeqOp]]. */
  val SeqOp = DynamicPatch.SeqOp

  /** Alias of [[DynamicPatch.StringOp]]. */
  type StringOp = DynamicPatch.StringOp

  /** The companion of [[DynamicPatch.StringOp]]. */
  val StringOp = DynamicPatch.StringOp

  /** Alias of [[DynamicPatch.MapOp]]. */
  type MapOp = DynamicPatch.MapOp

  /** The companion of [[DynamicPatch.MapOp]]. */
  val MapOp = DynamicPatch.MapOp
}
