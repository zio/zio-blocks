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

package zio.blocks.maybe

import scala.Conversion
import scala.language.implicitConversions

/**
 * The wrapper for a present value. On Scala 2, `MaybeValue.Present` holds every
 * present value. On Scala 3, `Present` is a top-level class used only for a
 * value that would otherwise look absent: `Maybe.present(null)` is
 * `Present(null)`, and `Maybe.present(Maybe.absent)` is `Present(Absent)`; a
 * plain present value is stored without it. A `Present` is not itself a
 * `Maybe`: build one with `Maybe.present`.
 *
 * @param value
 *   the present value, possibly `null`
 */
final class Present[+A](val value: A) {
  override def equals(other: Any): Boolean = other match {
    case p: Present[?] => value == p.value
    case _             => false
  }
  override def hashCode: Int    = if (value == null) 0 else value.hashCode // value CAN be null (Present(null))
  override def toString: String = s"Present($value)"
}

/** Builds and matches `Present` wrappers. */
object Present {

  /**
   * Wraps `value` in a `Present`. A `Present` is not a `Maybe`; use
   * `Maybe.present` to build one.
   */
  def apply[A](value: A): Present[A] = new Present(value)

  /**
   * Matches any present `Maybe`, stored as the plain value or in a `Present`
   * wrapper, and extracts its value; it allocates a `Some` per match. Scala 3
   * only; on Scala 2, match `MaybeValue.Present`.
   *
   * {{{
   * val port: Maybe[Int] = Maybe.present(8080)
   * port match {
   *   case Present(p) => p
   *   case Absent     => 80
   * } // 8080
   * }}}
   */
  def unapply[A](maybe: Maybe[A]): Option[A] =
    if (maybe.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) None
    else Some(Maybe.unsafeGet(maybe).asInstanceOf[A])
}

/**
 * The absent value, a single shared object. On Scala 3 it is the top-level
 * object `Absent`, and its `toString` is the default one
 * (`zio.blocks.maybe.Absent\$@<hash>`). On Scala 2 it is `MaybeValue.Absent`,
 * which prints as `Absent`. `Maybe.absent` returns it typed as a `Maybe[A]`.
 */
object Absent

/**
 * An optional value, like `Option`, that is cheaper to hold: on Scala 3 a
 * present value is stored as the value itself, and absence is one shared
 * object. A `Maybe[A]` is either present, holding an `A` (which may be `null`),
 * or absent.
 *
 * Use it for optional fields and results on hot paths, such as an optional port
 * or timeout in a config, where `Option` allocates a `Some` per value. It has
 * the `Option` operations (`map`, `flatMap`, `filter`, `fold`, `getOrElse`,
 * ...), converts to and from `Option`, and works in for-comprehensions.
 *
 * {{{
 * final case class ServerConfig(port: Int, timeoutSeconds: Maybe[Int])
 *
 * val defaults = ServerConfig(8080, Maybe.absent)
 * val custom   = defaults.copy(timeoutSeconds = Maybe.present(5))
 *
 * defaults.timeoutSeconds.getOrElse(30)        // 30
 * custom.timeoutSeconds.map(_ * 1000).toOption // Some(5000)
 * }}}
 *
 * The encoding depends on the Scala version. On Scala 3, `Maybe[A]` is an
 * opaque type for `A | Absent.type | Present[A]`: a present value is stored as
 * it is (on the JVM a primitive as its box), absence is the object `Absent`,
 * and only a value that would otherwise look absent (`null`, an absent `Maybe`,
 * or a `Present`) is wrapped in a `Present`. On Scala 2, `Maybe[A]` is an alias
 * of the sealed trait `MaybeValue[A]`, and every present value is a
 * `MaybeValue.Present`. So `toString` and `==` differ: on Scala 3
 * `Maybe.present(8080)` prints as `8080` and equals `8080`, on Scala 2 it
 * prints as `Present(8080)`.
 *
 * To match on a `Maybe`, use `fold`, which works on both versions, or match
 * `case Present(v)` and `case Absent` on Scala 3 and
 * `case MaybeValue.Present(v)` and `case MaybeValue.Absent` on Scala 2.
 *
 * @tparam A
 *   the type of the value
 * @see
 *   [[https://zioblocks.com/reference/maybe/ Maybe]]
 */
opaque type Maybe[+A] = A | Absent.type | Present[A]

/**
 * Constructors for `Maybe`: `Maybe.apply` for a value that may be `null`,
 * `present`, `absent` and `fromOption`.
 *
 * {{{
 * val port: Maybe[Int]     = Maybe.present(8080)
 * val user: Maybe[String]  = Maybe(null: String)
 * val timeout: Maybe[Long] = Maybe.fromOption(Some(30L))
 *
 * (port.isPresent, user.isAbsent, timeout.get) // (true,true,30)
 * }}}
 */
object Maybe {

  /**
   * Wraps `a`, treating `null` as absent: `Maybe(null: String)` is absent.
   *
   * On Scala 3, an absent `Maybe` passed as `a` is absent too, and a value that
   * is a `Present` wrapper (a present `Maybe` that holds `null` or an absent
   * `Maybe`, or a `Present` you built) is stored without a new wrapper, so
   * `get` removes one level too many (a known bug). On Scala 2 both are
   * present. Use `Maybe.present` for such values.
   *
   * {{{
   * Maybe("admin").isPresent       // true
   * Maybe(null: String).isAbsent   // true
   * }}}
   */
  inline def apply[A](a: A): Maybe[A] =
    if (a == null) zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]] else a.asInstanceOf[Maybe[A]]

  /**
   * Wraps `a` as a present value, even when `a` is `null` or an absent `Maybe`.
   * This keeps nested `Maybe`s sound: `Maybe.present(Maybe.absent)` is present,
   * and flattening it gives absent.
   *
   * On Scala 3, a plain value is returned as it is, without allocating (on the
   * JVM a primitive is stored as its box, which may allocate), and only `null`,
   * `Absent` or a `Present` gets a `Present` wrapper. On Scala 2, the value is
   * wrapped in a `MaybeValue.Present`.
   *
   * {{{
   * val port: Maybe[Int] = Maybe.present(8080)
   * port.get // 8080
   *
   * val nested: Maybe[Maybe[Int]] = Maybe.present(Maybe.absent[Int])
   * nested.isPresent        // true
   * nested.flatten.isAbsent // true
   * }}}
   */
  inline def present[A](a: A): Maybe[A] =
    if (a == null) zio.blocks.maybe.Present(null).asInstanceOf[Maybe[A]]
    else if (a.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)
      zio.blocks.maybe.Present(zio.blocks.maybe.Absent).asInstanceOf[Maybe[A]]
    else
      a.asInstanceOf[AnyRef] match {
        case p: zio.blocks.maybe.Present[?] => zio.blocks.maybe.Present(p).asInstanceOf[Maybe[A]]
        case _                              => a.asInstanceOf[Maybe[A]]
      }

  /**
   * Returns the absent value, typed as a `Maybe[A]`.
   *
   * {{{
   * val timeout: Maybe[Int] = Maybe.absent
   * timeout.getOrElse(30) // 30
   * }}}
   */
  inline def absent[A]: Maybe[A] = zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]

  /** Returns the absent value; the same as `Maybe.absent`. */
  inline def empty[A]: Maybe[A] = zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]

  /**
   * Converts an `Option`: `Some(a)` gives `Maybe.present(a)`, so `Some(null)`
   * is present, and `None` gives absent.
   *
   * {{{
   * Maybe.fromOption(Some(8080)).get        // 8080
   * Maybe.fromOption(Option.empty[Int]).isAbsent // true
   * }}}
   */
  def fromOption[A](opt: Option[A]): Maybe[A] = opt match {
    case Some(a) => present(a)
    case None    => zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]
  }

  /**
   * Converts an `Option` to a `Maybe` implicitly, as `fromOption` does. It
   * applies without an import; the call site needs
   * `scala.language.implicitConversions` to avoid a feature warning.
   */
  given [A]: Conversion[Option[A], Maybe[A]] with {
    def apply(opt: Option[A]): Maybe[A] = fromOption(opt)
  }

  /**
   * The view that `withFilter` returns: a `Maybe` together with a predicate, so
   * that a for-comprehension with an `if` guard compiles. Each method gives the
   * same result as calling it on the `Maybe` filtered by the predicate.
   */
  final class WithFilter[A](self: Maybe[A], predicate: A => Boolean) {

    /**
     * Returns a present `Maybe` of `f` applied to the value, or an absent one
     * if this `Maybe` is absent; on the view that `withFilter` returns, a value
     * that fails the view's predicate counts as absent. The result is present
     * even when `f` returns `null`.
     *
     * {{{
     * val timeoutSeconds: Maybe[Int] = Maybe.present(5)
     * timeoutSeconds.map(_ * 1000).toOption // Some(5000)
     * }}}
     */
    def map[B](f: A => B): Maybe[B] = self.filter(predicate).map(f)

    /**
     * Returns `f` applied to the value, or an absent `Maybe` if this `Maybe` is
     * absent; on the view that `withFilter` returns, a value that fails the
     * view's predicate counts as absent. Use it to chain steps that can each
     * produce no value.
     *
     * {{{
     * def parsePort(s: String): Maybe[Int] = Maybe.fromOption(s.toIntOption)
     *
     * Maybe.present("8080").flatMap(parsePort).toOption // Some(8080)
     * Maybe.present("http").flatMap(parsePort).isAbsent // true
     * }}}
     */
    def flatMap[B](f: A => Maybe[B]): Maybe[B] = self.filter(predicate).flatMap(f)

    /**
     * Runs `f` on the value if this `Maybe` is present, and does nothing
     * otherwise; on the view that `withFilter` returns, a value that fails the
     * view's predicate counts as absent.
     */
    def foreach[U](f: A => U): Unit = self.filter(predicate).foreach(f)

    /**
     * Returns a view filtered by the given predicate, so that a
     * for-comprehension can use an `if` guard; on a view, the result keeps the
     * view's predicate too. `map`, `flatMap` and `foreach` on the view give the
     * same result as on the `Maybe` filtered by the predicates.
     *
     * {{{
     * val port: Maybe[Int] = Maybe.present(8080)
     * val unprivileged = for {
     *   p <- port
     *   if p >= 1024
     * } yield p
     * unprivileged.toOption // Some(8080)
     * }}}
     */
    def withFilter(q: A => Boolean): WithFilter[A] = new WithFilter(self, x => predicate(x) && q(x))
  }

  /**
   * Returns `true` if `x` is the absent value. Used by other ZIO Blocks
   * modules, such as `schema`; not for public use.
   */
  private[blocks] def unsafeIsAbsent(x: Maybe[Any]): Boolean =
    x.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent

  /**
   * Returns the value, unwrapping a `Present`, or `null` if `x` is absent. Used
   * by other ZIO Blocks modules, such as `schema`; not for public use.
   *
   * Inline, so a hot path makes no call: an absent `x` costs one reference
   * comparison, and a present one a comparison and a type test.
   */
  private[blocks] inline def unsafeGet(x: Maybe[Any]): Any = {
    val ref = x.asInstanceOf[AnyRef]
    if (ref eq zio.blocks.maybe.Absent) null
    else if (ref.isInstanceOf[zio.blocks.maybe.Present[?]])
      ref.asInstanceOf[zio.blocks.maybe.Present[?]].value
    else x
  }

  /**
   * Returns `x` as a `Maybe`, `null` giving the absent value. Unlike
   * `Maybe.present`, it adds no `Present` wrapper. Used by other ZIO Blocks
   * modules, such as `schema`; not for public use.
   */
  private[blocks] def unsafeWrap[A](x: Any): Maybe[A] =
    if (x == null) zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]] else x.asInstanceOf[Maybe[A]]

  extension [A](self: Maybe[A]) {

    /**
     * Returns `true` if this `Maybe` is absent. A present `null`, as in
     * `Maybe.present(null)`, is not absent.
     */
    inline def isAbsent: Boolean = self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent

    /**
     * Returns `true` if this `Maybe` holds a value, even a `null` one.
     *
     * {{{
     * Maybe.present(8080).isPresent // true
     * Maybe.absent[Int].isPresent   // false
     * }}}
     */
    inline def isPresent: Boolean = !(self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)

    /**
     * Returns `true` if this `Maybe` is absent; the same as `isAbsent`.
     */
    inline def isEmpty: Boolean = self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent

    /**
     * Returns `true` if this `Maybe` holds a value; the same as `isPresent`.
     */
    inline def isDefined: Boolean = !(self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)

    /**
     * Returns `true` if this `Maybe` holds a value; the same as `isPresent`.
     */
    inline def nonEmpty: Boolean = !(self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)

    /**
     * Returns the value. Prefer `getOrElse` or `fold` unless the value is known
     * to be present.
     *
     * {{{
     * Maybe.present("admin").get // admin
     * // Maybe.absent[String].get throws NoSuchElementException("Maybe.absent.get")
     * }}}
     *
     * @throws java.util.NoSuchElementException
     *   if this `Maybe` is absent, with the message `Maybe.absent.get`
     */
    inline def get: A =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) throw new NoSuchElementException("Maybe.absent.get")
      else unsafeGet(self).asInstanceOf[A]

    /**
     * Returns the value, or `null` if this `Maybe` is absent. The result type
     * is `A | Null` on Scala 3 and `Any` on Scala 2; `orNull` keeps the type
     * `A` for a reference type.
     *
     * {{{
     * Maybe.present("admin").getOrNull // admin
     * Maybe.absent[String].getOrNull   // null
     * }}}
     */
    inline def getOrNull: A | Null = unsafeGet(self).asInstanceOf[A | Null]

    /**
     * Returns the value, or `default` if this `Maybe` is absent. `default` is
     * evaluated only in that case.
     *
     * {{{
     * val timeoutSeconds: Maybe[Int] = Maybe.absent
     * timeoutSeconds.getOrElse(30) // 30
     * }}}
     */
    inline def getOrElse[B >: A](default: => B): B =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) default else unsafeGet(self).asInstanceOf[A]

    /**
     * Returns this `Maybe` if it is present, and `alternative` otherwise.
     * `alternative` is evaluated only when this `Maybe` is absent.
     */
    inline def orElse[B >: A](alternative: => Maybe[B]): Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) alternative else self

    /**
     * Returns the value, or `null` if this `Maybe` is absent. It compiles only
     * when `null` is a valid `B`, so for a reference type.
     */
    inline def orNull[B >: A](using ev: Null <:< B): B =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) ev(null) else unsafeGet(self).asInstanceOf[A]

    /**
     * Returns `ifPresent` applied to the value, or `ifAbsent` if this `Maybe`
     * is absent. `ifAbsent` is evaluated only in that case. It works the same
     * on both Scala versions, so prefer it to a pattern match in cross-built
     * code.
     *
     * {{{
     * val timeoutSeconds: Maybe[Int] = Maybe.present(5)
     * timeoutSeconds.fold("no timeout")(s => s.toString + " s") // 5 s
     * }}}
     */
    inline def fold[B](ifAbsent: => B)(ifPresent: A => B): B =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) ifAbsent else ifPresent(unsafeGet(self).asInstanceOf[A])

    /**
     * Returns `Some` of the value, or `None` if this `Maybe` is absent. A
     * present `null` gives `Some(null)`.
     *
     * {{{
     * Maybe.present(8080).toOption // Some(8080)
     * Maybe.absent[Int].toOption   // None
     * }}}
     */
    inline def toOption: Option[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) None else Some(unsafeGet(self).asInstanceOf[A])

    /**
     * Returns a one-element `List` of the value, or `Nil` if this `Maybe` is
     * absent.
     */
    inline def toList: List[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) Nil else unsafeGet(self).asInstanceOf[A] :: Nil

    /**
     * Returns a one-element `Seq` of the value, or an empty `Seq` if this
     * `Maybe` is absent.
     */
    inline def toSeq: Seq[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) Seq.empty else Seq(unsafeGet(self).asInstanceOf[A])

    /**
     * Returns an iterator over the value, which is empty if this `Maybe` is
     * absent.
     */
    inline def iterator: Iterator[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) Iterator.empty
      else Iterator.single(unsafeGet(self).asInstanceOf[A])

    /**
     * Returns a present `Maybe` of `f` applied to the value, or an absent one
     * if this `Maybe` is absent; on the view that `withFilter` returns, a value
     * that fails the view's predicate counts as absent. The result is present
     * even when `f` returns `null`.
     *
     * {{{
     * val timeoutSeconds: Maybe[Int] = Maybe.present(5)
     * timeoutSeconds.map(_ * 1000).toOption // Some(5000)
     * }}}
     */
    inline def map[B](f: A => B): Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[B]]
      else present(f(unsafeGet(self).asInstanceOf[A]))

    /**
     * Returns `f` applied to the value, or an absent `Maybe` if this `Maybe` is
     * absent; on the view that `withFilter` returns, a value that fails the
     * view's predicate counts as absent. Use it to chain steps that can each
     * produce no value.
     *
     * {{{
     * def parsePort(s: String): Maybe[Int] = Maybe.fromOption(s.toIntOption)
     *
     * Maybe.present("8080").flatMap(parsePort).toOption // Some(8080)
     * Maybe.present("http").flatMap(parsePort).isAbsent // true
     * }}}
     */
    inline def flatMap[B](f: A => Maybe[B]): Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[B]]
      else f(unsafeGet(self).asInstanceOf[A])

    /**
     * Removes one level of nesting: a present `Maybe` that holds a present
     * value gives that value, and one that holds an absent `Maybe` gives
     * absent.
     */
    inline def flatten[B](using ev: A <:< Maybe[B]): Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[B]]
      else ev(unsafeGet(self).asInstanceOf[A])

    /**
     * Runs `f` on the value if this `Maybe` is present, and does nothing
     * otherwise; on the view that `withFilter` returns, a value that fails the
     * view's predicate counts as absent.
     */
    inline def foreach[U](f: A => U): Unit =
      if (!(self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)) {
        f(unsafeGet(self).asInstanceOf[A])
        ()
      }

    /**
     * Returns `true` if this `Maybe` is present and its value equals `elem`.
     */
    inline def contains[A1 >: A](elem: A1): Boolean =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) false else unsafeGet(self).asInstanceOf[A] == elem

    /**
     * Returns `true` if this `Maybe` is present and its value satisfies `p`.
     */
    inline def exists(p: A => Boolean): Boolean =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) false else p(unsafeGet(self).asInstanceOf[A])

    /**
     * Returns `true` if this `Maybe` is absent or its value satisfies `p`.
     */
    inline def forall(p: A => Boolean): Boolean =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) true else p(unsafeGet(self).asInstanceOf[A])

    /**
     * Returns this `Maybe` if it is present and its value satisfies `p`, and an
     * absent `Maybe` otherwise.
     *
     * {{{
     * val port: Maybe[Int] = Maybe.present(80)
     * port.filter(_ >= 1024).isAbsent // true
     * }}}
     */
    inline def filter(p: A => Boolean): Maybe[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]
      else if (p(unsafeGet(self).asInstanceOf[A])) self
      else zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]

    /**
     * Returns this `Maybe` if it is present and its value does not satisfy `p`,
     * and an absent `Maybe` otherwise.
     */
    inline def filterNot(p: A => Boolean): Maybe[A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]
      else if (p(unsafeGet(self).asInstanceOf[A])) zio.blocks.maybe.Absent.asInstanceOf[Maybe[A]]
      else self

    /**
     * Returns a present `Maybe` of `pf` applied to the value if `pf` is defined
     * at it, and an absent `Maybe` otherwise.
     */
    inline def collect[B](pf: PartialFunction[A, B]): Maybe[B] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) zio.blocks.maybe.Absent.asInstanceOf[Maybe[B]]
      else {
        val value = unsafeGet(self).asInstanceOf[A]
        if (pf.isDefinedAt(value)) present(pf(value)) else zio.blocks.maybe.Absent.asInstanceOf[Maybe[B]]
      }

    /**
     * Returns a view filtered by the given predicate, so that a
     * for-comprehension can use an `if` guard; on a view, the result keeps the
     * view's predicate too. `map`, `flatMap` and `foreach` on the view give the
     * same result as on the `Maybe` filtered by the predicates.
     *
     * {{{
     * val port: Maybe[Int] = Maybe.present(8080)
     * val unprivileged = for {
     *   p <- port
     *   if p >= 1024
     * } yield p
     * unprivileged.toOption // Some(8080)
     * }}}
     */
    def withFilter(p: A => Boolean): WithFilter[A] =
      new WithFilter(self, p)

    /**
     * Returns `Right` of the value, or `Left(left)` if this `Maybe` is absent.
     * `left` is evaluated only in that case.
     */
    inline def toRight[X](left: => X): Either[X, A] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) Left(left) else Right(unsafeGet(self).asInstanceOf[A])

    /**
     * Returns `Left` of the value, or `Right(right)` if this `Maybe` is absent.
     * `right` is evaluated only in that case.
     */
    inline def toLeft[X](right: => X): Either[A, X] =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) Right(right) else Left(unsafeGet(self).asInstanceOf[A])

    /**
     * Returns a present `Maybe` of both values as a pair if both `Maybe`s are
     * present, and an absent `Maybe` otherwise.
     */
    inline def zip[B](that: Maybe[B]): Maybe[(A, B)] =
      if (
        (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent) || (that.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)
      ) zio.blocks.maybe.Absent.asInstanceOf[Maybe[(A, B)]]
      else present((unsafeGet(self).asInstanceOf[A], unsafeGet(that).asInstanceOf[B]))

    /**
     * Splits a `Maybe` of a pair into a pair of `Maybe`s, both absent if this
     * `Maybe` is absent.
     */
    inline def unzip[A1, A2](using ev: A <:< (A1, A2)): (Maybe[A1], Maybe[A2]) =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)
        (zio.blocks.maybe.Absent.asInstanceOf[Maybe[A1]], zio.blocks.maybe.Absent.asInstanceOf[Maybe[A2]])
      else {
        val value = ev(unsafeGet(self).asInstanceOf[A])
        (present(value._1), present(value._2))
      }

    /**
     * Splits a `Maybe` of a triple into three `Maybe`s, all absent if this
     * `Maybe` is absent.
     */
    inline def unzip3[A1, A2, A3](using ev: A <:< (A1, A2, A3)): (Maybe[A1], Maybe[A2], Maybe[A3]) =
      if (self.asInstanceOf[AnyRef] eq zio.blocks.maybe.Absent)
        (
          zio.blocks.maybe.Absent.asInstanceOf[Maybe[A1]],
          zio.blocks.maybe.Absent.asInstanceOf[Maybe[A2]],
          zio.blocks.maybe.Absent.asInstanceOf[Maybe[A3]]
        )
      else {
        val value = ev(unsafeGet(self).asInstanceOf[A])
        (present(value._1), present(value._2), present(value._3))
      }
  }
}
