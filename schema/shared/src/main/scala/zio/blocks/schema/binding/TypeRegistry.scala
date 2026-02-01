package zio.blocks.schema.binding

import zio.blocks.typeid.TypeId

/**
 * A type registry is a type-safe repository of bindings indexed by type
 * identity.
 *
 * TypeRegistry is used for rebinding unbound schemas (`Reflect.Unbound`) to
 * bound schemas (`Reflect.Bound`) by providing the runtime constructors,
 * deconstructors, and other binding information for each type referenced in the
 * schema.
 *
 * The registry stores bindings internally keyed by `TypeId`. For sequence and
 * map types, bindings are stored by their unapplied type constructor (e.g.,
 * `List` rather than `List[Int]`), allowing a single binding to work for all
 * element types.
 *
 * @example
 *   {{{
 * case class Person(name: String, age: Int)
 *
 * val registry = TypeRegistry.default
 *   .bind(Binding.of[Person])
 *
 * val unboundSchema: Reflect.Unbound[Person] = ...
 * val boundSchema: Schema[Person] = unboundSchema.rebind(registry).toSchema
 *   }}}
 */
final class TypeRegistry private (private val entries: Map[TypeId[_], TypeRegistry.Entry]) {
  import TypeRegistry._

  /**
   * Binds a type to its binding, automatically dispatching based on binding
   * kind.
   *
   * This unified entry point accepts any proper binding type (Record, Variant,
   * Primitive, Wrapper, or Dynamic) and stores it appropriately. It works with
   * `Binding.of[A]` which returns `Any` in Scala 2.
   *
   * @tparam A
   *   The type to bind
   * @param binding
   *   Any proper binding (Record, Variant, Primitive, Wrapper, or Dynamic)
   * @param typeId
   *   The TypeId for type A (usually derived implicitly)
   * @return
   *   A new TypeRegistry with the binding added
   * @throws java.lang.IllegalArgumentException
   *   if the binding is a Seq or Map binding (use the appropriate overload
   *   instead)
   *
   * @example
   *   {{{
   * case class Person(name: String, age: Int)
   * val registry = TypeRegistry.default.bind(Binding.of[Person])
   *   }}}
   */
  def bind[A](binding: Binding[_, A])(implicit typeId: TypeId[A]): TypeRegistry = binding match {
    case b: Binding.Record[A] @unchecked     => updated(keyForProper(typeId), Entry.Record(b))
    case b: Binding.Variant[A] @unchecked    => updated(keyForProper(typeId), Entry.Variant(b))
    case b: Binding.Primitive[A] @unchecked  => updated(keyForProper(typeId), Entry.Primitive(b))
    case b: Binding.Wrapper[A, _] @unchecked =>
      updated(keyForProper(typeId), Entry.Wrapper(b.asInstanceOf[Binding.Wrapper[Any, Any]]))
    case b: Binding.Dynamic =>
      updated(keyForProper(typeId), Entry.Dynamic(b))
    case _: Binding.Seq[_, _] =>
      throw new IllegalArgumentException("Use bind[C[_]](Binding.Seq[C, Nothing]) for sequence bindings")
    case _: Binding.Map[_, _, _] =>
      throw new IllegalArgumentException("Use bind[M[_, _]](Binding.Map[M, Nothing, Nothing]) for map bindings")
  }

  /**
   * Binds a sequence type constructor to its binding.
   *
   * The binding is stored by the unapplied type constructor, so binding `List`
   * allows lookup for `List[Int]`, `List[String]`, etc.
   *
   * @tparam C
   *   The sequence type constructor (e.g., `List`, `Vector`)
   * @param binding
   *   The sequence binding providing constructor and deconstructor
   * @param typeId
   *   The TypeId for the applied type `C[Nothing]` (used to extract the
   *   constructor)
   * @return
   *   A new TypeRegistry with the binding added
   */
  def bind[C[_]](binding: Binding.Seq[C, Nothing])(implicit typeId: TypeId[C[Nothing]]): TypeRegistry =
    updated(keyForConstructor(typeId), new Entry.Seq(binding))

  /**
   * Binds a map type constructor to its binding.
   *
   * The binding is stored by the unapplied type constructor, so binding `Map`
   * allows lookup for `Map[String, Int]`, `Map[Int, String]`, etc.
   *
   * @tparam M
   *   The map type constructor (e.g., `Map`)
   * @param binding
   *   The map binding providing constructor and deconstructor
   * @param typeId
   *   The TypeId for the applied type `M[Nothing, Nothing]` (used to extract
   *   the constructor)
   * @return
   *   A new TypeRegistry with the binding added
   */
  def bind[M[_, _]](binding: Binding.Map[M, Nothing, Nothing])(implicit
    typeId: TypeId[M[Nothing, Nothing]]
  ): TypeRegistry =
    updated(keyForConstructor(typeId), new Entry.Map(binding))

  /**
   * Looks up the record binding for a type.
   *
   * @tparam A
   *   The type to look up
   * @param typeId
   *   The TypeId for type A (usually derived implicitly)
   * @return
   *   The binding if found, None otherwise
   */
  def lookupRecord[A](implicit typeId: TypeId[A]): Option[Binding.Record[A]] =
    entries.get(keyForProper(typeId)).collect { case Entry.Record(b) =>
      b.asInstanceOf[Binding.Record[A]]
    }

  /**
   * Looks up the variant binding for a type.
   *
   * @tparam A
   *   The type to look up
   * @param typeId
   *   The TypeId for type A (usually derived implicitly)
   * @return
   *   The binding if found, None otherwise
   */
  def lookupVariant[A](implicit typeId: TypeId[A]): Option[Binding.Variant[A]] =
    entries.get(keyForProper(typeId)).collect { case Entry.Variant(b) =>
      b.asInstanceOf[Binding.Variant[A]]
    }

  /**
   * Looks up the primitive binding for a type.
   *
   * @tparam A
   *   The type to look up
   * @param typeId
   *   The TypeId for type A (usually derived implicitly)
   * @return
   *   The binding if found, None otherwise
   */
  def lookupPrimitive[A](implicit typeId: TypeId[A]): Option[Binding.Primitive[A]] =
    entries.get(keyForProper(typeId)).collect { case Entry.Primitive(b) =>
      b.asInstanceOf[Binding.Primitive[A]]
    }

  /**
   * Looks up the wrapper binding for a type.
   *
   * @tparam A
   *   The outer (wrapped) type
   * @param typeId
   *   The TypeId for type A (usually derived implicitly)
   * @return
   *   The binding if found, None otherwise
   */
  def lookupWrapper[A](implicit typeId: TypeId[A]): Option[Binding.Wrapper[A, _]] =
    entries.get(keyForProper(typeId)).collect { case Entry.Wrapper(b) =>
      b.asInstanceOf[Binding.Wrapper[A, _]]
    }

  /**
   * Looks up the dynamic binding.
   *
   * @param typeId
   *   The TypeId for DynamicValue (usually derived implicitly)
   * @return
   *   The binding if found, None otherwise
   */
  def lookupDynamic(implicit typeId: TypeId[zio.blocks.schema.DynamicValue]): Option[Binding.Dynamic] =
    entries.get(keyForProper(typeId)).collect { case Entry.Dynamic(b) => b }

  /**
   * Looks up the sequence binding for an applied sequence type.
   *
   * Uses [[UnapplySeq]] to decompose the applied type and look up the binding
   * by type constructor.
   *
   * @tparam X
   *   The applied sequence type (e.g., `List[Int]`)
   * @param typeId
   *   The TypeId for type X (usually derived implicitly)
   * @param u
   *   Evidence that X is an applied sequence type
   * @return
   *   The binding if found, None otherwise
   */
  def lookupSeq[X](implicit typeId: TypeId[X], u: UnapplySeq[X]): Option[Binding.Seq[u.C, u.A]] =
    entries.get(keyForConstructor(typeId)).collect { case e: Entry.Seq =>
      e.binding.asInstanceOf[Binding.Seq[u.C, u.A]]
    }

  /**
   * Looks up the map binding for an applied map type.
   *
   * Uses [[UnapplyMap]] to decompose the applied type and look up the binding
   * by type constructor.
   *
   * @tparam X
   *   The applied map type (e.g., `Map[String, Int]`)
   * @param typeId
   *   The TypeId for type X (usually derived implicitly)
   * @param u
   *   Evidence that X is an applied map type
   * @return
   *   The binding if found, None otherwise
   */
  def lookupMap[X](implicit typeId: TypeId[X], u: UnapplyMap[X]): Option[Binding.Map[u.M, u.K, u.V]] =
    entries.get(keyForConstructor(typeId)).collect { case e: Entry.Map =>
      e.binding.asInstanceOf[Binding.Map[u.M, u.K, u.V]]
    }

  /**
   * Internal method to look up a sequence binding by TypeId directly.
   *
   * This is used by the rebind implementation where we have the TypeId but not
   * the UnapplySeq evidence.
   */
  private[schema] def lookupSeqByTypeId[C[_], A](typeId: TypeId[C[A]]): Option[Binding.Seq[C, A]] =
    entries.get(keyForConstructor(typeId)).collect { case e: Entry.Seq =>
      e.binding.asInstanceOf[Binding.Seq[C, A]]
    }

  /**
   * Internal method to look up a map binding by TypeId directly.
   *
   * This is used by the rebind implementation where we have the TypeId but not
   * the UnapplyMap evidence.
   */
  private[schema] def lookupMapByTypeId[M[_, _], K, V](typeId: TypeId[M[K, V]]): Option[Binding.Map[M, K, V]] =
    entries.get(keyForConstructor(typeId)).collect { case e: Entry.Map =>
      e.binding.asInstanceOf[Binding.Map[M, K, V]]
    }

  /**
   * Internal method to look up a record binding by TypeId directly.
   */
  private[schema] def lookupRecordByTypeId(typeId: TypeId[_]): Option[Binding.Record[Any]] =
    entries.get(keyForProper(typeId)).collect { case Entry.Record(b) =>
      b.asInstanceOf[Binding.Record[Any]]
    }

  /**
   * Internal method to look up a variant binding by TypeId directly.
   */
  private[schema] def lookupVariantByTypeId(typeId: TypeId[_]): Option[Binding.Variant[Any]] =
    entries.get(keyForProper(typeId)).collect { case Entry.Variant(b) =>
      b.asInstanceOf[Binding.Variant[Any]]
    }

  /**
   * Internal method to look up a primitive binding by TypeId directly.
   */
  private[schema] def lookupPrimitiveByTypeId(typeId: TypeId[_]): Option[Binding.Primitive[Any]] =
    entries.get(keyForProper(typeId)).collect { case Entry.Primitive(b) =>
      b.asInstanceOf[Binding.Primitive[Any]]
    }

  /**
   * Internal method to look up a wrapper binding by TypeId directly.
   */
  private[schema] def lookupWrapperByTypeId(typeId: TypeId[_]): Option[Binding.Wrapper[Any, Any]] =
    entries.get(keyForProper(typeId)).collect { case Entry.Wrapper(b) =>
      b.asInstanceOf[Binding.Wrapper[Any, Any]]
    }

  /**
   * Internal method to look up a dynamic binding by TypeId directly.
   */
  private[schema] def lookupDynamicByTypeId(typeId: TypeId[_]): Option[Binding.Dynamic] =
    entries.get(keyForProper(typeId)).collect { case Entry.Dynamic(b) => b }

  /**
   * Checks if the registry contains a binding for the given type.
   *
   * @tparam A
   *   The type to check
   * @param typeId
   *   The TypeId for type A (usually derived implicitly)
   * @return
   *   true if a binding exists, false otherwise
   */
  def contains[A](implicit typeId: TypeId[A]): Boolean =
    entries.contains(keyForProper(typeId))

  /**
   * Checks if the registry contains a sequence binding for the given type
   * constructor.
   *
   * @tparam X
   *   The applied sequence type (e.g., `List[Int]`)
   * @param typeId
   *   The TypeId for type X (usually derived implicitly)
   * @param u
   *   Evidence that X is an applied sequence type
   * @return
   *   true if a binding exists, false otherwise
   */
  def containsSeq[X](implicit typeId: TypeId[X], @annotation.unused u: UnapplySeq[X]): Boolean =
    entries.get(keyForConstructor(typeId)).exists(_.isInstanceOf[Entry.Seq])

  /**
   * Checks if the registry contains a map binding for the given type
   * constructor.
   *
   * @tparam X
   *   The applied map type (e.g., `Map[String, Int]`)
   * @param typeId
   *   The TypeId for type X (usually derived implicitly)
   * @param u
   *   Evidence that X is an applied map type
   * @return
   *   true if a binding exists, false otherwise
   */
  def containsMap[X](implicit typeId: TypeId[X], @annotation.unused u: UnapplyMap[X]): Boolean =
    entries.get(keyForConstructor(typeId)).exists(_.isInstanceOf[Entry.Map])

  /**
   * Returns the number of bindings in the registry.
   */
  def size: Int = entries.size

  /**
   * Checks if the registry is empty.
   */
  def isEmpty: Boolean = entries.isEmpty

  /**
   * Checks if the registry is non-empty.
   */
  def nonEmpty: Boolean = entries.nonEmpty

  private def updated(key: TypeId[_], entry: Entry): TypeRegistry =
    new TypeRegistry(entries.updated(key, entry))

  private def keyForProper(id: TypeId[_]): TypeId[_] = TypeId.normalize(id)

  private def keyForConstructor(id: TypeId[_]): TypeId[_] = TypeId.unapplied(TypeId.normalize(id))

  override def equals(obj: Any): Boolean = obj match {
    case that: TypeRegistry => this.entries == that.entries
    case _                  => false
  }

  override def hashCode(): Int = entries.hashCode()

  override def toString: String = s"TypeRegistry(${entries.size} entries)"
}

object TypeRegistry {

  /**
   * Internal entry type to track the kind of binding stored.
   */
  private[binding] sealed trait Entry

  private[binding] object Entry {
    final case class Record(binding: Binding.Record[_])       extends Entry
    final case class Variant(binding: Binding.Variant[_])     extends Entry
    final case class Primitive(binding: Binding.Primitive[_]) extends Entry
    final case class Wrapper(binding: Binding.Wrapper[_, _])  extends Entry
    final case class Dynamic(binding: Binding.Dynamic)        extends Entry
    final class Seq(val binding: Binding[_, _])               extends Entry
    final class Map(val binding: Binding[_, _])               extends Entry
  }

  /**
   * Creates an empty TypeRegistry with no bindings.
   */
  val empty: TypeRegistry = new TypeRegistry(Map.empty)

  /**
   * Creates a TypeRegistry with bindings for all primitive types.
   *
   * This includes:
   *   - Unit, Boolean, Byte, Short, Int, Long, Float, Double, Char, String
   *   - BigInt, BigDecimal
   *   - java.time types (DayOfWeek, Duration, Instant, LocalDate, etc.)
   *   - java.util.Currency, java.util.UUID
   *   - DynamicValue
   *   - Common sequence types (List, Vector, Set, IndexedSeq, Seq, Chunk)
   *   - Map
   */
  val default: TypeRegistry = {
    import zio.blocks.chunk.Chunk

    empty
      // Primitives
      .bind(Binding.Primitive.unit)
      .bind(Binding.Primitive.boolean)
      .bind(Binding.Primitive.byte)
      .bind(Binding.Primitive.short)
      .bind(Binding.Primitive.int)
      .bind(Binding.Primitive.long)
      .bind(Binding.Primitive.float)
      .bind(Binding.Primitive.double)
      .bind(Binding.Primitive.char)
      .bind(Binding.Primitive.string)
      .bind(Binding.Primitive.bigInt)
      .bind(Binding.Primitive.bigDecimal)
      // java.time types
      .bind(Binding.Primitive.dayOfWeek)
      .bind(Binding.Primitive.duration)
      .bind(Binding.Primitive.instant)
      .bind(Binding.Primitive.localDate)
      .bind(Binding.Primitive.localDateTime)
      .bind(Binding.Primitive.localTime)
      .bind(Binding.Primitive.month)
      .bind(Binding.Primitive.monthDay)
      .bind(Binding.Primitive.offsetDateTime)
      .bind(Binding.Primitive.offsetTime)
      .bind(Binding.Primitive.period)
      .bind(Binding.Primitive.year)
      .bind(Binding.Primitive.yearMonth)
      .bind(Binding.Primitive.zoneId)
      .bind(Binding.Primitive.zoneOffset)
      .bind(Binding.Primitive.zonedDateTime)
      .bind(Binding.Primitive.currency)
      .bind(Binding.Primitive.uuid)
      // Dynamic
      .bind(Binding.Dynamic())
      // Sequences
      .bind[Set](Binding.Seq.set[Nothing])
      .bind[List](Binding.Seq.list[Nothing])
      .bind[Vector](Binding.Seq.vector[Nothing])
      .bind[IndexedSeq](Binding.Seq.indexedSeq[Nothing])
      .bind[scala.collection.immutable.Seq](Binding.Seq.seq[Nothing])
      .bind[Chunk](Binding.Seq.chunk[Nothing])
      // Maps
      .bind[Predef.Map](Binding.Map.map[Nothing, Nothing])
  }
}
