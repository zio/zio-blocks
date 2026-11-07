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

package zio.blocks.context

/**
 * An immutable map from types to values, such as the services an application
 * wires together (a `Config`, a `Logger`, a `Database`). The type parameter `R`
 * records the stored types as an intersection (`Config & Logger`), so
 * `Context#get` compiles only for a type the context provides.
 *
 * Use it to pass a set of dependencies through plain synchronous code, without
 * an effect system. `add`, `update`, `++` and `prune` return a new context.
 * Keys are nominal types (classes, traits, objects and applied types such as
 * `List[Int]`), which an `IsNominalType` instance proves at compile time, and a
 * stored value cannot be `null`.
 *
 * {{{
 * final case class Config(port: Int)
 * trait Logger { def info(msg: String): Unit }
 * final case class ConsoleLogger(prefix: String) extends Logger {
 *   def info(msg: String): Unit = println(prefix + " " + msg)
 * }
 *
 * val ctx: Context[Config & ConsoleLogger] = Context(Config(8080), ConsoleLogger("[app]"))
 *
 * ctx.get[Config].port // 8080
 * ctx.get[Logger]      // ConsoleLogger([app]), found by its supertype
 * ctx.size             // 2
 * }}}
 *
 * Each context caches the values it has looked up, per requested type. On the
 * JVM the cache is a `ConcurrentHashMap`, so several threads can read one
 * context; on Scala.js it is a `mutable.HashMap`. Java serialization of a
 * context fails with a `NotSerializableException`, although the class extends
 * `Serializable` (a known bug).
 *
 * @tparam R
 *   the stored types, as an intersection such as `Config & Logger`; `Any` for
 *   an empty context
 * @see
 *   [[https://zioblocks.com/reference/context/ Context]]
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

  /** Returns `true` if this context contains no entries. */
  def isEmpty: Boolean = entries.isEmpty

  /** Returns `true` if this context contains at least one entry. */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Returns the value stored for type `A`. The bound `A >: R` makes the call
   * compile only when the context's type provides `A`; otherwise the compiler
   * reports that `A` does not conform to the lower bound `R`.
   *
   * A value stored under exactly `A` wins. Otherwise `get` returns a stored
   * value whose type is a subtype of `A`, the most recently added one when
   * several match. The result is cached in this context.
   *
   * {{{
   * trait Logger
   * final case class ConsoleLogger(prefix: String) extends Logger
   * final case class Config(port: Int)
   *
   * val ctx = Context(Config(8080), ConsoleLogger("[app]"))
   *
   * ctx.get[Config]        // Config(8080)
   * ctx.get[Logger]        // ConsoleLogger([app])
   * // ctx.get[String]     // does not compile: String is not provided
   * }}}
   *
   * @tparam A
   *   the type to look up: a type in `R`, or a supertype of one
   * @throws java.util.NoSuchElementException
   *   if no stored value matches, with the message `Bug in Context: type <A>
   *   was expected but not found`. The type bound rules this out, except after
   *   `prune` with a supertype (a known bug).
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
   * Returns the value stored for type `A`, or `None`. Unlike `get`, it compiles
   * for any nominal type `A`, so use it for a dependency that may be missing.
   * It finds a value the same way as `get`: exact type first, then the most
   * recently added subtype.
   *
   * {{{
   * final case class Config(port: Int)
   * final case class Database(url: String)
   *
   * val ctx = Context(Config(8080))
   *
   * ctx.getOption[Config]   // Some(Config(8080))
   * ctx.getOption[Database] // None
   * }}}
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
   * Returns a new context that also holds `a`, stored under the type `A`. A
   * value already stored under exactly `A` is replaced; a value of another
   * type, even a subtype or supertype of `A`, stays.
   *
   * {{{
   * final case class Config(port: Int)
   * final case class Database(url: String)
   *
   * val ctx = Context(Config(8080)).add(Database("jdbc:h2:mem:app"))
   * ctx.get[Database].url              // jdbc:h2:mem:app
   * ctx.add(Config(9090)).get[Config]  // Config(9090)
   * }}}
   *
   * @throws java.lang.NullPointerException
   *   if `a` is `null`
   */
  def add[A](a: A)(implicit ev: IsNominalType[A]): Context[R & A] = {
    if (a == null)
      throw new NullPointerException(
        s"Context.add: cannot store null value for type ${ev.typeId.fullName}. " +
          "Context uses null internally as a sentinel for missing entries."
      )
    val key        = ev.typeIdErased
    val newEntries = entries.updated(key, a)
    new Context(newEntries, null)
  }

  /**
   * Returns a new context in which the value for type `A` is replaced by `f`
   * applied to it. The value is found as `getOption[A]` finds it; if there is
   * none, the context is returned unchanged.
   *
   * The result is stored under exactly `A`. When `A` is a supertype of the
   * stored value's type (`update[Logger]` on a stored `ConsoleLogger`), the old
   * value stays under its own type and the result is added as a second entry,
   * so `get[ConsoleLogger]` still returns the old value.
   *
   * {{{
   * final case class Config(port: Int)
   *
   * val ctx = Context(Config(8080))
   * ctx.update[Config](c => c.copy(port = c.port + 1)).get[Config] // Config(8081)
   * }}}
   *
   * @throws java.lang.NullPointerException
   *   if `f` returns `null`
   */
  def update[A >: R](f: A => A)(implicit ev: IsNominalType[A]): Context[R] =
    getOption[A] match {
      case Some(a) =>
        val result = f(a)
        if (result == null)
          throw new NullPointerException(
            s"Context.update: transform returned null for type ${ev.typeId.fullName}. " +
              "Context uses null internally as a sentinel for missing entries."
          )
        val key        = ev.typeIdErased
        val newEntries = entries.updated(key, result)
        new Context(newEntries, null)
      case None => self
    }

  /**
   * Returns a context that holds the entries of both contexts. When both hold a
   * value under the same type, the value from `that` wins.
   *
   * {{{
   * final case class Config(port: Int)
   * final case class Database(url: String)
   *
   * val defaults  = Context(Config(8080), Database("jdbc:h2:mem:app"))
   * val overrides = Context(Config(9090))
   *
   * (defaults ++ overrides).get[Config] // Config(9090)
   * (defaults ++ overrides).size        // 2
   * }}}
   */
  def ++[R1](that: Context[R1]): Context[R & R1] =
    if (that.isEmpty) self.asInstanceOf[Context[R & R1]]
    else if (self.isEmpty) that.asInstanceOf[Context[R & R1]]
    else {
      val newEntries = entries.union(that.entries)
      new Context(newEntries, null)
    }

  /**
   * Returns a context that keeps only the entries stored under the types that
   * make up `A`, an intersection of types from `R`.
   *
   * An entry is kept only when its type is exactly one of the components of
   * `A`. Pruning to a supertype (`prune[Logger]` on a stored `ConsoleLogger`)
   * drops that entry, and a later `get[Logger]` throws (a known bug).
   *
   * {{{
   * final case class Config(port: Int)
   * final case class Database(url: String)
   *
   * val ctx    = Context(Config(8080), Database("jdbc:h2:mem:app"))
   * val pruned = ctx.prune[Config]
   * pruned.size // 1
   * }}}
   */
  def prune[A >: R](implicit ev: IsNominalIntersection[A]): Context[A] = {
    val keys       = ev.typeIdsErased
    val newEntries = entries.pruned(keys)
    new Context(newEntries, null)
  }

  /**
   * Lists the entries as `Context(<type name> -> <value>, ...)`, most recently
   * added first, with each type's full name without its type arguments
   * (`List[Int]` prints as `scala.collection.immutable.List`).
   */
  override def toString: String = {
    val contents = entries.reverseIterator.map { case (k, v) =>
      s"${k.fullName} -> $v"
    }.mkString(", ")
    s"Context($contents)"
  }
}

/**
 * Constructors for `Context`: `Context.empty`, and `Context.apply` for one to
 * ten values.
 */
object Context {

  /** An empty context containing no entries. */
  val empty: Context[Any] = new Context(ContextEntries.empty, null)

  private def make[R](entries: ContextEntries): Context[R] =
    new Context[R](entries, null)

  /**
   * Creates a context that holds one value, stored under its type `A1`.
   *
   * {{{
   * final case class Config(port: Int)
   *
   * val ctx: Context[Config] = Context(Config(8080))
   * }}}
   *
   * @throws java.lang.NullPointerException
   *   if the value is `null`
   */
  def apply[A1](a1: A1)(implicit ev1: IsNominalType[A1]): Context[A1] =
    make(ContextEntries.fromPairs(Array((ev1.typeIdErased, a1))))

  /**
   * Creates a context that holds two values, each stored under its own type.
   * When two values have the same type, the later one wins.
   *
   * @throws java.lang.NullPointerException
   *   if a value is `null`
   */
  def apply[A1, A2](a1: A1, a2: A2)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2]
  ): Context[A1 & A2] =
    make(ContextEntries.fromPairs(Array((ev1.typeIdErased, a1), (ev2.typeIdErased, a2))))

  /**
   * Creates a context that holds three values, each stored under its own type.
   * When two values have the same type, the later one wins.
   *
   * @throws java.lang.NullPointerException
   *   if a value is `null`
   */
  def apply[A1, A2, A3](a1: A1, a2: A2, a3: A3)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3]
  ): Context[A1 & A2 & A3] =
    make(
      ContextEntries.fromPairs(
        Array(
          (ev1.typeIdErased, a1),
          (ev2.typeIdErased, a2),
          (ev3.typeIdErased, a3)
        )
      )
    )

  /**
   * Creates a context that holds four values, each stored under its own type.
   * When two values have the same type, the later one wins.
   *
   * @throws java.lang.NullPointerException
   *   if a value is `null`
   */
  def apply[A1, A2, A3, A4](a1: A1, a2: A2, a3: A3, a4: A4)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4]
  ): Context[A1 & A2 & A3 & A4] =
    make(
      ContextEntries.fromPairs(
        Array(
          (ev1.typeIdErased, a1),
          (ev2.typeIdErased, a2),
          (ev3.typeIdErased, a3),
          (ev4.typeIdErased, a4)
        )
      )
    )

  /**
   * Creates a context that holds five values, each stored under its own type.
   * When two values have the same type, the later one wins.
   *
   * @throws java.lang.NullPointerException
   *   if a value is `null`
   */
  def apply[A1, A2, A3, A4, A5](a1: A1, a2: A2, a3: A3, a4: A4, a5: A5)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4],
    ev5: IsNominalType[A5]
  ): Context[A1 & A2 & A3 & A4 & A5] =
    make(
      ContextEntries.fromPairs(
        Array(
          (ev1.typeIdErased, a1),
          (ev2.typeIdErased, a2),
          (ev3.typeIdErased, a3),
          (ev4.typeIdErased, a4),
          (ev5.typeIdErased, a5)
        )
      )
    )

  /**
   * Creates a context that holds six values, each stored under its own type.
   * When two values have the same type, the later one wins.
   *
   * @throws java.lang.NullPointerException
   *   if a value is `null`
   */
  def apply[A1, A2, A3, A4, A5, A6](a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4],
    ev5: IsNominalType[A5],
    ev6: IsNominalType[A6]
  ): Context[A1 & A2 & A3 & A4 & A5 & A6] =
    make(
      ContextEntries.fromPairs(
        Array(
          (ev1.typeIdErased, a1),
          (ev2.typeIdErased, a2),
          (ev3.typeIdErased, a3),
          (ev4.typeIdErased, a4),
          (ev5.typeIdErased, a5),
          (ev6.typeIdErased, a6)
        )
      )
    )

  /**
   * Creates a context that holds seven values, each stored under its own type.
   * When two values have the same type, the later one wins.
   *
   * @throws java.lang.NullPointerException
   *   if a value is `null`
   */
  def apply[A1, A2, A3, A4, A5, A6, A7](a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4],
    ev5: IsNominalType[A5],
    ev6: IsNominalType[A6],
    ev7: IsNominalType[A7]
  ): Context[A1 & A2 & A3 & A4 & A5 & A6 & A7] =
    make(
      ContextEntries.fromPairs(
        Array(
          (ev1.typeIdErased, a1),
          (ev2.typeIdErased, a2),
          (ev3.typeIdErased, a3),
          (ev4.typeIdErased, a4),
          (ev5.typeIdErased, a5),
          (ev6.typeIdErased, a6),
          (ev7.typeIdErased, a7)
        )
      )
    )

  /**
   * Creates a context that holds eight values, each stored under its own type.
   * When two values have the same type, the later one wins.
   *
   * @throws java.lang.NullPointerException
   *   if a value is `null`
   */
  def apply[A1, A2, A3, A4, A5, A6, A7, A8](a1: A1, a2: A2, a3: A3, a4: A4, a5: A5, a6: A6, a7: A7, a8: A8)(implicit
    ev1: IsNominalType[A1],
    ev2: IsNominalType[A2],
    ev3: IsNominalType[A3],
    ev4: IsNominalType[A4],
    ev5: IsNominalType[A5],
    ev6: IsNominalType[A6],
    ev7: IsNominalType[A7],
    ev8: IsNominalType[A8]
  ): Context[A1 & A2 & A3 & A4 & A5 & A6 & A7 & A8] =
    make(
      ContextEntries.fromPairs(
        Array(
          (ev1.typeIdErased, a1),
          (ev2.typeIdErased, a2),
          (ev3.typeIdErased, a3),
          (ev4.typeIdErased, a4),
          (ev5.typeIdErased, a5),
          (ev6.typeIdErased, a6),
          (ev7.typeIdErased, a7),
          (ev8.typeIdErased, a8)
        )
      )
    )

  /**
   * Creates a context that holds nine values, each stored under its own type.
   * When two values have the same type, the later one wins.
   *
   * @throws java.lang.NullPointerException
   *   if a value is `null`
   */
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
  ): Context[A1 & A2 & A3 & A4 & A5 & A6 & A7 & A8 & A9] =
    make(
      ContextEntries.fromPairs(
        Array(
          (ev1.typeIdErased, a1),
          (ev2.typeIdErased, a2),
          (ev3.typeIdErased, a3),
          (ev4.typeIdErased, a4),
          (ev5.typeIdErased, a5),
          (ev6.typeIdErased, a6),
          (ev7.typeIdErased, a7),
          (ev8.typeIdErased, a8),
          (ev9.typeIdErased, a9)
        )
      )
    )

  /**
   * Creates a context that holds ten values, each stored under its own type.
   * When two values have the same type, the later one wins.
   *
   * @throws java.lang.NullPointerException
   *   if a value is `null`
   */
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
  ): Context[A1 & A2 & A3 & A4 & A5 & A6 & A7 & A8 & A9 & A10] =
    make(
      ContextEntries.fromPairs(
        Array(
          (ev1.typeIdErased, a1),
          (ev2.typeIdErased, a2),
          (ev3.typeIdErased, a3),
          (ev4.typeIdErased, a4),
          (ev5.typeIdErased, a5),
          (ev6.typeIdErased, a6),
          (ev7.typeIdErased, a7),
          (ev8.typeIdErased, a8),
          (ev9.typeIdErased, a9),
          (ev10.typeIdErased, a10)
        )
      )
    )
}
