package zio.blocks.schema.binding

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicValue
import zio.blocks.typeid.TypeId

/**
 * A binding resolver provides bindings for types during schema rebinding.
 *
 * BindingResolver is the read-only interface for looking up bindings by type
 * identity. Multiple resolvers can be combined using `++` to form resolution
 * chains with fallback behavior.
 *
 * The library provides several resolver implementations:
 *   - [[BindingResolver.Registry]]: A map-backed resolver for explicit bindings
 *   - [[BindingResolver.Reflection]]: A resolver that derives record bindings
 *     using runtime reflection
 *   - [[BindingResolver.defaults]]: Pre-configured bindings for primitives,
 *     java.time, and common collections
 *
 * @example
 *   {{{
 * // Use defaults for primitives and collections, with custom bindings
 * val resolver = customRegistry ++ BindingResolver.defaults
 *
 * // Add reflection-based derivation as fallback
 * val withReflection = customRegistry ++ BindingResolver.reflection ++ BindingResolver.defaults
 *   }}}
 */
trait BindingResolver { self =>

  /**
   * Resolves a record binding for a type.
   *
   * @tparam A
   *   The type to resolve
   * @param typeId
   *   The TypeId for type A (usually derived implicitly)
   * @return
   *   The binding if found, None otherwise
   */
  def resolveRecord[A](implicit typeId: TypeId[A]): Option[Binding.Record[A]]

  /**
   * Resolves a variant binding for a type.
   *
   * @tparam A
   *   The type to resolve
   * @param typeId
   *   The TypeId for type A (usually derived implicitly)
   * @return
   *   The binding if found, None otherwise
   */
  def resolveVariant[A](implicit typeId: TypeId[A]): Option[Binding.Variant[A]]

  /**
   * Resolves a primitive binding for a type.
   *
   * @tparam A
   *   The type to resolve
   * @param typeId
   *   The TypeId for type A (usually derived implicitly)
   * @return
   *   The binding if found, None otherwise
   */
  def resolvePrimitive[A](implicit typeId: TypeId[A]): Option[Binding.Primitive[A]]

  /**
   * Resolves a wrapper binding for a type.
   *
   * @tparam A
   *   The outer (wrapped) type
   * @param typeId
   *   The TypeId for type A (usually derived implicitly)
   * @return
   *   The binding if found, None otherwise
   */
  def resolveWrapper[A](implicit typeId: TypeId[A]): Option[Binding.Wrapper[A, _]]

  /**
   * Resolves the dynamic binding.
   *
   * @param typeId
   *   The TypeId for DynamicValue (usually derived implicitly)
   * @return
   *   The binding if found, None otherwise
   */
  def resolveDynamic(implicit typeId: TypeId[DynamicValue]): Option[Binding.Dynamic]

  /**
   * Resolves a sequence binding for an applied sequence type.
   *
   * Uses [[UnapplySeq]] to decompose the applied type and resolve the binding
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
  def resolveSeq[X](implicit typeId: TypeId[X], u: UnapplySeq[X]): Option[Binding.Seq[u.C, u.A]]

  /**
   * Resolves a sequence binding given an explicit TypeId for the applied type.
   *
   * This variant is useful when you already have the type constructor and
   * element type as separate type parameters.
   *
   * @tparam C
   *   The sequence type constructor (e.g., `List`)
   * @tparam A
   *   The element type
   * @param typeId
   *   The TypeId for the applied type `C[A]`
   * @return
   *   The binding if found, None otherwise
   */
  def resolveSeqFor[C[_], A](typeId: TypeId[C[A]]): Option[Binding.Seq[C, A]]

  /**
   * Resolves a map binding for an applied map type.
   *
   * Uses [[UnapplyMap]] to decompose the applied type and resolve the binding
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
  def resolveMap[X](implicit typeId: TypeId[X], u: UnapplyMap[X]): Option[Binding.Map[u.M, u.K, u.V]]

  /**
   * Resolves a map binding given an explicit TypeId for the applied type.
   *
   * This variant is useful when you already have the map type constructor and
   * key/value types as separate type parameters.
   *
   * @tparam M
   *   The map type constructor (e.g., `Map`)
   * @tparam K
   *   The key type
   * @tparam V
   *   The value type
   * @param typeId
   *   The TypeId for the applied type `M[K, V]`
   * @return
   *   The binding if found, None otherwise
   */
  def resolveMapFor[M[_, _], K, V](typeId: TypeId[M[K, V]]): Option[Binding.Map[M, K, V]]

  /**
   * Combines this resolver with another, trying this resolver first.
   *
   * When resolving a binding, the combined resolver tries `this` first. If
   * `this` returns `None`, it falls back to `that`.
   *
   * @param that
   *   The fallback resolver
   * @return
   *   A combined resolver that tries `this` first, then `that`
   *
   * @example
   *   {{{
   * // customRegistry overrides defaults
   * val resolver = customRegistry ++ BindingResolver.defaults
   *   }}}
   */
  final def ++(that: BindingResolver): BindingResolver = new BindingResolver.Combined(self, that)
}

object BindingResolver {

  /**
   * An empty registry with no bindings.
   */
  val empty: Registry = new Registry(Map.empty)

  /**
   * A resolver with bindings for all primitive types and common collections.
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
  val defaults: Registry =
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

  /**
   * A resolver that derives record bindings using runtime reflection.
   *
   * This resolver can automatically create bindings for case classes by
   * inspecting their structure at runtime. It uses the TypeId's `clazz` and
   * `construct` methods to create constructors and deconstructors.
   *
   * Limitations:
   *   - Only works for record types (case classes), not variants
   *   - Requires the type to have a public primary constructor
   *   - Field access must be available (true for case classes)
   *   - Only works on JVM (returns None on JS/Native)
   *
   * @example
   *   {{{
   * // Use reflection for user types, with defaults for primitives
   * val resolver = BindingResolver.reflection ++ BindingResolver.defaults
   *   }}}
   */
  val reflection: BindingResolver = Reflection

  /**
   * A map-backed binding resolver that stores explicit bindings.
   *
   * Registry is an immutable data structure. Each `bind` operation returns a
   * new Registry with the added binding.
   *
   * For sequence and map types, bindings are stored by their unapplied type
   * constructor (e.g., `List` rather than `List[Int]`), allowing a single
   * binding to work for all element types.
   *
   * @example
   *   {{{
   * case class Person(name: String, age: Int)
   *
   * val registry = BindingResolver.empty
   *   .bind(Binding.of[Person])
   *   }}}
   */
  final class Registry private[BindingResolver] (private val entries: Map[TypeId[_], Registry.Entry])
      extends BindingResolver {
    import Registry._

    /**
     * Binds a type to its binding, automatically dispatching based on binding
     * kind.
     *
     * This unified entry point accepts any proper binding type (Record,
     * Variant, Primitive, Wrapper, or Dynamic) and stores it appropriately. It
     * works with `Binding.of[A]` which returns `Any` in Scala 2.
     *
     * @tparam A
     *   The type to bind
     * @param binding
     *   Any proper binding (Record, Variant, Primitive, Wrapper, or Dynamic)
     * @param typeId
     *   The TypeId for type A (usually derived implicitly)
     * @return
     *   A new Registry with the binding added
     * @throws java.lang.IllegalArgumentException
     *   if the binding is a Seq or Map binding (use the appropriate overload
     *   instead)
     *
     * @example
     *   {{{
     * case class Person(name: String, age: Int)
     * val registry = BindingResolver.empty.bind(Binding.of[Person])
     *   }}}
     */
    def bind[A](binding: Binding[_, A])(implicit typeId: TypeId[A]): Registry = binding match {
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
     * The binding is stored by the unapplied type constructor, so binding
     * `List` allows lookup for `List[Int]`, `List[String]`, etc.
     *
     * @tparam C
     *   The sequence type constructor (e.g., `List`, `Vector`)
     * @param binding
     *   The sequence binding providing constructor and deconstructor
     * @param typeId
     *   The TypeId for the applied type `C[Nothing]` (used to extract the
     *   constructor)
     * @return
     *   A new Registry with the binding added
     */
    def bind[C[_]](binding: Binding.Seq[C, Nothing])(implicit typeId: TypeId[C[Nothing]]): Registry =
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
     *   A new Registry with the binding added
     */
    def bind[M[_, _]](binding: Binding.Map[M, Nothing, Nothing])(implicit
      typeId: TypeId[M[Nothing, Nothing]]
    ): Registry =
      updated(keyForConstructor(typeId), new Entry.Map(binding))

    def resolveRecord[A](implicit typeId: TypeId[A]): Option[Binding.Record[A]] =
      entries.get(keyForProper(typeId)).collect { case Entry.Record(b) =>
        b.asInstanceOf[Binding.Record[A]]
      }

    def resolveVariant[A](implicit typeId: TypeId[A]): Option[Binding.Variant[A]] =
      entries.get(keyForProper(typeId)).collect { case Entry.Variant(b) =>
        b.asInstanceOf[Binding.Variant[A]]
      }

    def resolvePrimitive[A](implicit typeId: TypeId[A]): Option[Binding.Primitive[A]] =
      entries.get(keyForProper(typeId)).collect { case Entry.Primitive(b) =>
        b.asInstanceOf[Binding.Primitive[A]]
      }

    def resolveWrapper[A](implicit typeId: TypeId[A]): Option[Binding.Wrapper[A, _]] =
      entries.get(keyForProper(typeId)).collect { case Entry.Wrapper(b) =>
        b.asInstanceOf[Binding.Wrapper[A, _]]
      }

    def resolveDynamic(implicit typeId: TypeId[DynamicValue]): Option[Binding.Dynamic] =
      entries.get(keyForProper(typeId)).collect { case Entry.Dynamic(b) => b }

    def resolveSeq[X](implicit typeId: TypeId[X], u: UnapplySeq[X]): Option[Binding.Seq[u.C, u.A]] =
      entries.get(keyForConstructor(typeId)).collect { case e: Entry.Seq =>
        e.binding.asInstanceOf[Binding.Seq[u.C, u.A]]
      }

    def resolveSeqFor[C[_], A](typeId: TypeId[C[A]]): Option[Binding.Seq[C, A]] =
      entries.get(keyForConstructor(typeId)).collect { case e: Entry.Seq =>
        e.binding.asInstanceOf[Binding.Seq[C, A]]
      }

    def resolveMap[X](implicit typeId: TypeId[X], u: UnapplyMap[X]): Option[Binding.Map[u.M, u.K, u.V]] =
      entries.get(keyForConstructor(typeId)).collect { case e: Entry.Map =>
        e.binding.asInstanceOf[Binding.Map[u.M, u.K, u.V]]
      }

    def resolveMapFor[M[_, _], K, V](typeId: TypeId[M[K, V]]): Option[Binding.Map[M, K, V]] =
      entries.get(keyForConstructor(typeId)).collect { case e: Entry.Map =>
        e.binding.asInstanceOf[Binding.Map[M, K, V]]
      }

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

    private def updated(key: TypeId[_], entry: Entry): Registry =
      new Registry(entries.updated(key, entry))

    override def equals(obj: Any): Boolean = obj match {
      case that: Registry => this.entries == that.entries
      case _              => false
    }

    override def hashCode(): Int = entries.hashCode()

    override def toString: String = s"BindingResolver.Registry(${entries.size} entries)"
  }

  object Registry {
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

    private def keyForProper(id: TypeId[_]): TypeId[_] = TypeId.normalize(id)

    private def keyForConstructor(id: TypeId[_]): TypeId[_] = TypeId.unapplied(TypeId.normalize(id))
  }

  /**
   * A combined resolver that tries the left resolver first, then falls back to
   * the right.
   */
  private[binding] final class Combined(left: BindingResolver, right: BindingResolver) extends BindingResolver {

    def resolveRecord[A](implicit typeId: TypeId[A]): Option[Binding.Record[A]] =
      left.resolveRecord[A].orElse(right.resolveRecord[A])

    def resolveVariant[A](implicit typeId: TypeId[A]): Option[Binding.Variant[A]] =
      left.resolveVariant[A].orElse(right.resolveVariant[A])

    def resolvePrimitive[A](implicit typeId: TypeId[A]): Option[Binding.Primitive[A]] =
      left.resolvePrimitive[A].orElse(right.resolvePrimitive[A])

    def resolveWrapper[A](implicit typeId: TypeId[A]): Option[Binding.Wrapper[A, _]] =
      left.resolveWrapper[A].orElse(right.resolveWrapper[A])

    def resolveDynamic(implicit typeId: TypeId[DynamicValue]): Option[Binding.Dynamic] =
      left.resolveDynamic.orElse(right.resolveDynamic)

    def resolveSeq[X](implicit typeId: TypeId[X], u: UnapplySeq[X]): Option[Binding.Seq[u.C, u.A]] =
      left.resolveSeq[X].orElse(right.resolveSeq[X])

    def resolveSeqFor[C[_], A](typeId: TypeId[C[A]]): Option[Binding.Seq[C, A]] =
      left.resolveSeqFor(typeId).orElse(right.resolveSeqFor(typeId))

    def resolveMap[X](implicit typeId: TypeId[X], u: UnapplyMap[X]): Option[Binding.Map[u.M, u.K, u.V]] =
      left.resolveMap[X].orElse(right.resolveMap[X])

    def resolveMapFor[M[_, _], K, V](typeId: TypeId[M[K, V]]): Option[Binding.Map[M, K, V]] =
      left.resolveMapFor(typeId).orElse(right.resolveMapFor(typeId))

    override def toString: String = s"($left ++ $right)"
  }

  /**
   * A resolver that derives record bindings using runtime reflection.
   *
   * This resolver creates bindings for case classes by using runtime reflection
   * to discover constructor parameters and field accessors. It caches derived
   * bindings to avoid repeated reflection overhead.
   */
  private object Reflection extends BindingResolver {
    import scala.util.Try

    private val recordCache = new java.util.concurrent.ConcurrentHashMap[TypeId[_], Option[Binding.Record[Any]]]()

    def resolveRecord[A](implicit typeId: TypeId[A]): Option[Binding.Record[A]] = {
      val normalizedId = TypeId.normalize(typeId)
      recordCache
        .computeIfAbsent(normalizedId, _ => deriveRecordBinding(normalizedId))
        .asInstanceOf[Option[Binding.Record[A]]]
    }

    def resolveVariant[A](implicit typeId: TypeId[A]): Option[Binding.Variant[A]] = None

    def resolvePrimitive[A](implicit typeId: TypeId[A]): Option[Binding.Primitive[A]] = None

    def resolveWrapper[A](implicit typeId: TypeId[A]): Option[Binding.Wrapper[A, _]] = None

    def resolveDynamic(implicit typeId: TypeId[DynamicValue]): Option[Binding.Dynamic] = None

    def resolveSeq[X](implicit typeId: TypeId[X], u: UnapplySeq[X]): Option[Binding.Seq[u.C, u.A]] = None

    def resolveSeqFor[C[_], A](typeId: TypeId[C[A]]): Option[Binding.Seq[C, A]] = None

    def resolveMap[X](implicit typeId: TypeId[X], u: UnapplyMap[X]): Option[Binding.Map[u.M, u.K, u.V]] = None

    def resolveMapFor[M[_, _], K, V](typeId: TypeId[M[K, V]]): Option[Binding.Map[M, K, V]] = None

    private def deriveRecordBinding(typeId: TypeId[_]): Option[Binding.Record[Any]] =
      if (!typeId.isCaseClass) None
      else
        typeId.clazz.flatMap { clazz =>
          Try {
            val constructors = clazz.getConstructors
            if (constructors.isEmpty) None
            else {
              val primaryCtor = constructors.head
              val paramCount  = primaryCtor.getParameterCount

              val fields = clazz.getDeclaredFields.take(paramCount)
              fields.foreach(_.setAccessible(true))

              val constructor = new Constructor[Any] {
                def usedRegisters: RegisterOffset.RegisterOffset = RegisterOffset(objects = paramCount)

                def construct(in: Registers, baseOffset: RegisterOffset.RegisterOffset): Any = {
                  val args   = new Array[AnyRef](paramCount)
                  var i      = 0
                  var offset = baseOffset
                  while (i < paramCount) {
                    args(i) = in.getObject(offset)
                    offset = RegisterOffset.incrementObjects(offset)
                    i += 1
                  }
                  primaryCtor.newInstance(args: _*)
                }
              }

              val deconstructor = new Deconstructor[Any] {
                def usedRegisters: RegisterOffset.RegisterOffset = RegisterOffset(objects = paramCount)

                def deconstruct(out: Registers, baseOffset: RegisterOffset.RegisterOffset, in: Any): Unit = {
                  var i      = 0
                  var offset = baseOffset
                  while (i < paramCount) {
                    out.setObject(offset, fields(i).get(in).asInstanceOf[AnyRef])
                    offset = RegisterOffset.incrementObjects(offset)
                    i += 1
                  }
                }
              }

              Some(Binding.Record(constructor, deconstructor))
            }
          }.toOption.flatten
        }

    override def toString: String = "BindingResolver.Reflection"
  }
}
