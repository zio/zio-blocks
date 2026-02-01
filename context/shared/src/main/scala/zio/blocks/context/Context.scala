package zio.blocks.context

/**
 * A type-indexed heterogeneous collection that stores values indexed by their
 * types.
 *
 * Context provides compile-time type safety for storing and retrieving values
 * of different types. The phantom type parameter `R` tracks which types are
 * present in the context, enabling the type system to verify that requested
 * values exist.
 *
 * @tparam R
 *   The phantom type representing the types stored in this context. Uses
 *   intersection types (A & B) to represent multiple stored types.
 */
final class Context[+R] private (
  private val entries: ContextEntries,
  @transient @volatile private var cache: Cache
) extends Serializable { self =>

  private def getCache: Cache = {
    var c = cache
    if (c == null) {
      c = PlatformCache.empty
      cache = c
    }
    c
  }

  /** Returns the number of entries in this context. */
  def size: Int = entries.size

  /** Returns true if this context contains no entries. */
  def isEmpty: Boolean = entries.isEmpty

  /** Returns true if this context contains at least one entry. */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Retrieves the value of type `A` from this context.
   *
   * The type bound `A >: R` ensures at compile time that a value of type `A`
   * (or a subtype) is present. Supertype lookups are supported: if you store a
   * `Dog` and request an `Animal`, the `Dog` will be returned.
   *
   * @tparam A
   *   the type to retrieve (must be a supertype of some type in R)
   * @return
   *   the stored value
   * @throws scala.NoSuchElementException
   *   if no matching value is found (indicates a bug)
   */
  def get[A >: R](implicit ev: IsNominalType[A]): A = {
    val key    = ev.typeIdErased
    val cached = getCache.get(key)
    if (cached != null) cached.asInstanceOf[A]
    else {
      val found = entries.getBySubtype(key)
      if (found != null) {
        getCache.putIfAbsent(key, found)
        found.asInstanceOf[A]
      } else {
        throw new NoSuchElementException(s"Bug in Context: type ${ev.typeId.fullName} was expected but not found")
      }
    }
  }

  /**
   * Retrieves the value of type `A` if present, without requiring a type bound.
   *
   * Unlike `get`, this method does not require `A` to be in the context's type
   * parameter. Use this for optional lookups where the type may or may not be
   * present.
   *
   * @tparam A
   *   the type to retrieve
   * @return
   *   Some(value) if found, None otherwise
   */
  def getOption[A](implicit ev: IsNominalType[A]): Option[A] = {
    val key    = ev.typeIdErased
    val cached = getCache.get(key)
    if (cached != null) Some(cached.asInstanceOf[A])
    else {
      val found = entries.getBySubtype(key)
      if (found != null) {
        getCache.putIfAbsent(key, found)
        Some(found.asInstanceOf[A])
      } else None
    }
  }

  /**
   * Adds a value to this context, returning a new context with the expanded
   * type.
   *
   * If a value of the same type already exists, it is replaced.
   *
   * @tparam A
   *   the type of the value to add
   * @param a
   *   the value to add
   * @return
   *   a new context containing the added value
   */
  def add[A](a: A)(implicit ev: IsNominalType[A]): Context[R & A] = {
    val key        = ev.typeIdErased
    val newEntries = entries.updated(key, a)
    new Context(newEntries, PlatformCache.empty)
  }

  /**
   * Transforms an existing value in this context.
   *
   * If the type is not present, the context is returned unchanged.
   *
   * @tparam A
   *   the type to update (must be a supertype of some type in R)
   * @param f
   *   the transformation function
   * @return
   *   a new context with the transformed value
   */
  def update[A >: R](f: A => A)(implicit ev: IsNominalType[A]): Context[R] =
    getOption[A] match {
      case Some(a) =>
        val key        = ev.typeIdErased
        val newEntries = entries.updated(key, f(a))
        new Context(newEntries, PlatformCache.empty)
      case None => self
    }

  /**
   * Combines this context with another, returning a new context containing all
   * entries.
   *
   * When both contexts contain the same type, the value from `that` (right)
   * wins.
   *
   * @tparam R1
   *   the type parameter of the other context
   * @param that
   *   the context to merge with
   * @return
   *   a new context containing entries from both
   */
  def ++[R1](that: Context[R1]): Context[R & R1] =
    if (that.isEmpty) self.asInstanceOf[Context[R & R1]]
    else if (self.isEmpty) that.asInstanceOf[Context[R & R1]]
    else {
      val newEntries = entries.union(that.entries)
      new Context(newEntries, PlatformCache.empty)
    }

  /**
   * Narrows this context to contain only the specified types.
   *
   * The type `A` should be an intersection of nominal types that are present in
   * `R`. Any entries not in `A` are removed.
   *
   * @tparam A
   *   the intersection type to keep (must be a supertype of R)
   * @return
   *   a new context containing only entries for types in A
   */
  def prune[A >: R](implicit ev: IsNominalIntersection[A]): Context[A] = {
    val keys       = ev.typeIdsErased
    val newEntries = entries.pruned(keys)
    new Context(newEntries, PlatformCache.empty)
  }

  override def toString: String = {
    val contents = entries.reverseIterator.map { case (k, v) =>
      s"${k.fullName} -> $v"
    }.mkString(", ")
    s"Context($contents)"
  }
}

object Context {

  /** An empty context containing no entries. */
  val empty: Context[Any] = new Context(ContextEntries.empty, PlatformCache.empty)

  private def make[R](entries: ContextEntries): Context[R] =
    new Context[R](entries, PlatformCache.empty)

  /** Creates a context containing a single value. */
  def apply[A1](a1: A1)(implicit ev1: IsNominalType[A1]): Context[A1] =
    make(ContextEntries.empty.updated(ev1.typeIdErased, a1))

  def apply[A1, A2](a1: A1, a2: A2)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2]
  ): Context[A1 & A2] =
    make(ContextEntries.empty.updated(ev1.typeIdErased, a1).updated(ev2.typeIdErased, a2))

  def apply[A1, A2, A3](a1: A1, a2: A2, a3: A3)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3]
  ): Context[A1 & A2 & A3] = {
    val e =
      ContextEntries.empty.updated(ev1.typeIdErased, a1).updated(ev2.typeIdErased, a2).updated(ev3.typeIdErased, a3)
    make(e)
  }

  def apply[A1, A2, A3, A4](a1: A1, a2: A2, a3: A3, a4: A4)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4]
  ): Context[A1 & A2 & A3 & A4] = {
    val e = ContextEntries.empty
      .updated(ev1.typeIdErased, a1)
      .updated(ev2.typeIdErased, a2)
      .updated(ev3.typeIdErased, a3)
      .updated(ev4.typeIdErased, a4)
    make(e)
  }

  def apply[A1, A2, A3, A4, A5](a1: A1, a2: A2, a3: A3, a4: A4, a5: A5)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4],
    ev5: IsNominalType[A5]
  ): Context[A1 & A2 & A3 & A4 & A5] = {
    val e = ContextEntries.empty
      .updated(ev1.typeIdErased, a1)
      .updated(ev2.typeIdErased, a2)
      .updated(ev3.typeIdErased, a3)
      .updated(ev4.typeIdErased, a4)
      .updated(ev5.typeIdErased, a5)
    make(e)
  }

  def apply[A1, A2, A3, A4, A5, A6](a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4],
    ev5: IsNominalType[A5],
    ev6: IsNominalType[A6]
  ): Context[A1 & A2 & A3 & A4 & A5 & A6] = {
    val e = ContextEntries.empty
      .updated(ev1.typeIdErased, a1)
      .updated(ev2.typeIdErased, a2)
      .updated(ev3.typeIdErased, a3)
      .updated(ev4.typeIdErased, a4)
      .updated(ev5.typeIdErased, a5)
      .updated(ev6.typeIdErased, a6)
    make(e)
  }

  def apply[A1, A2, A3, A4, A5, A6, A7](a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4],
    ev5: IsNominalType[A5],
    ev6: IsNominalType[A6],
    ev7: IsNominalType[A7]
  ): Context[A1 & A2 & A3 & A4 & A5 & A6 & A7] = {
    val e = ContextEntries.empty
      .updated(ev1.typeIdErased, a1)
      .updated(ev2.typeIdErased, a2)
      .updated(ev3.typeIdErased, a3)
      .updated(ev4.typeIdErased, a4)
      .updated(ev5.typeIdErased, a5)
      .updated(ev6.typeIdErased, a6)
      .updated(ev7.typeIdErased, a7)
    make(e)
  }

  def apply[A1, A2, A3, A4, A5, A6, A7, A8](a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4],
    ev5: IsNominalType[A5],
    ev6: IsNominalType[A6],
    ev7: IsNominalType[A7],
    ev8: IsNominalType[A8]
  ): Context[A1 & A2 & A3 & A4 & A5 & A6 & A7 & A8] = {
    val e = ContextEntries.empty
      .updated(ev1.typeIdErased, a1)
      .updated(ev2.typeIdErased, a2)
      .updated(ev3.typeIdErased, a3)
      .updated(ev4.typeIdErased, a4)
      .updated(ev5.typeIdErased, a5)
      .updated(ev6.typeIdErased, a6)
      .updated(ev7.typeIdErased, a7)
      .updated(ev8.typeIdErased, a8)
    make(e)
  }

  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5,
    a6: A6,
    a7: A7,
    a8: A8,
    a9: A9
  )(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4],
    ev5: IsNominalType[A5],
    ev6: IsNominalType[A6],
    ev7: IsNominalType[A7],
    ev8: IsNominalType[A8],
    ev9: IsNominalType[A9]
  ): Context[A1 & A2 & A3 & A4 & A5 & A6 & A7 & A8 & A9] = {
    val e = ContextEntries.empty
      .updated(ev1.typeIdErased, a1)
      .updated(ev2.typeIdErased, a2)
      .updated(ev3.typeIdErased, a3)
      .updated(ev4.typeIdErased, a4)
      .updated(ev5.typeIdErased, a5)
      .updated(ev6.typeIdErased, a6)
      .updated(ev7.typeIdErased, a7)
      .updated(ev8.typeIdErased, a8)
      .updated(ev9.typeIdErased, a9)
    make(e)
  }

  def apply[A1, A2, A3, A4, A5, A6, A7, A8, A9, A10](
    a1: A1,
    a2: A2,
    a3: A3,
    a4: A4,
    a5: A5,
    a6: A6,
    a7: A7,
    a8: A8,
    a9: A9,
    a10: A10
  )(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4],
    ev5: IsNominalType[A5],
    ev6: IsNominalType[A6],
    ev7: IsNominalType[A7],
    ev8: IsNominalType[A8],
    ev9: IsNominalType[A9],
    ev10: IsNominalType[A10]
  ): Context[A1 & A2 & A3 & A4 & A5 & A6 & A7 & A8 & A9 & A10] = {
    val e = ContextEntries.empty
      .updated(ev1.typeIdErased, a1)
      .updated(ev2.typeIdErased, a2)
      .updated(ev3.typeIdErased, a3)
      .updated(ev4.typeIdErased, a4)
      .updated(ev5.typeIdErased, a5)
      .updated(ev6.typeIdErased, a6)
      .updated(ev7.typeIdErased, a7)
      .updated(ev8.typeIdErased, a8)
      .updated(ev9.typeIdErased, a9)
      .updated(ev10.typeIdErased, a10)
    make(e)
  }
}
