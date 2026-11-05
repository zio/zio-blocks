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

import scala.language.implicitConversions

/**
 * On Scala 2, the representation of `Maybe`: a sealed trait with the cases
 * `MaybeValue.Present` and `MaybeValue.Absent`, which `Maybe[A]` is an alias
 * of. Match on these cases on Scala 2.
 *
 * {{{
 * // scala-2-only
 * val port: Maybe[Int] = Maybe.present(8080)
 * port match {
 *   case MaybeValue.Present(p) => p
 *   case MaybeValue.Absent     => 80
 * } // 8080
 * }}}
 */
sealed trait MaybeValue[+A]

/**
 * On Scala 2, holds the cases of `MaybeValue` and the `MaybeOps` syntax that
 * adds the `Maybe` operations.
 */
object MaybeValue {

  /**
   * The wrapper for a present value. On Scala 2, `MaybeValue.Present` holds
   * every present value. On Scala 3, `Present` is a top-level class used only
   * for a value that would otherwise look absent: `Maybe.present(null)` is
   * `Present(null)`, and `Maybe.present(Maybe.absent)` is `Present(Absent)`; a
   * plain present value is stored without it. A `Present` is not itself a
   * `Maybe`: build one with `Maybe.present`.
   *
   * @param value
   *   the present value, possibly `null`
   */
  final case class Present[+A](value: A) extends MaybeValue[A]

  /**
   * The absent value, a single shared object. On Scala 3 it is the top-level
   * object `Absent`, and its `toString` is the default one
   * (`zio.blocks.maybe.Absent\$@<hash>`). On Scala 2 it is `MaybeValue.Absent`,
   * which prints as `Absent`. `Maybe.absent` returns it typed as a `Maybe[A]`.
   */
  case object Absent extends MaybeValue[Nothing]

  /**
   * On Scala 2, adds the `Maybe` operations to a `Maybe` value. Scala 3 defines
   * the same operations as extension methods.
   */
  implicit final class MaybeOps[A](private val self: zio.blocks.maybe.Maybe[A]) extends AnyVal {

    /**
     * Returns `true` if this `Maybe` is absent. A present `null`, as in
     * `Maybe.present(null)`, is not absent.
     */
    @inline def isAbsent: Boolean = self eq Absent

    /**
     * Returns `true` if this `Maybe` holds a value, even a `null` one.
     *
     * {{{
     * Maybe.present(8080).isPresent // true
     * Maybe.absent[Int].isPresent   // false
     * }}}
     */
    @inline def isPresent: Boolean = self ne Absent

    /**
     * Returns `true` if this `Maybe` is absent; the same as `isAbsent`.
     */
    @inline def isEmpty: Boolean = self eq Absent

    /**
     * Returns `true` if this `Maybe` holds a value; the same as `isPresent`.
     */
    @inline def isDefined: Boolean = self ne Absent

    /**
     * Returns `true` if this `Maybe` holds a value; the same as `isPresent`.
     */
    @inline def nonEmpty: Boolean = self ne Absent

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
    @inline def get: A =
      self match {
        case Present(value) => value
        case Absent         => throw new NoSuchElementException("Maybe.absent.get")
      }

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
    @inline def getOrNull: Any =
      self match {
        case Present(value) => value
        case Absent         => null
      }

    /**
     * Returns the value, or `default` if this `Maybe` is absent. `default` is
     * evaluated only in that case.
     *
     * {{{
     * val timeoutSeconds: Maybe[Int] = Maybe.absent
     * timeoutSeconds.getOrElse(30) // 30
     * }}}
     */
    @inline def getOrElse[B >: A](default: => B): B =
      self match {
        case Present(value) => value
        case Absent         => default
      }

    /**
     * Returns this `Maybe` if it is present, and `alternative` otherwise.
     * `alternative` is evaluated only when this `Maybe` is absent.
     */
    @inline def orElse[B >: A](alternative: => zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] =
      self match {
        case Present(_) => self
        case Absent     => alternative
      }

    /**
     * Returns the value, or `null` if this `Maybe` is absent. It compiles only
     * when `null` is a valid `B`, so for a reference type.
     */
    @inline def orNull[B >: A](implicit ev: Null <:< B): B =
      self match {
        case Present(value) => value
        case Absent         => ev(null)
      }

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
    @inline def fold[B](ifAbsent: => B)(ifPresent: A => B): B =
      self match {
        case Present(value) => ifPresent(value)
        case Absent         => ifAbsent
      }

    /**
     * Returns `Some` of the value, or `None` if this `Maybe` is absent. A
     * present `null` gives `Some(null)`.
     *
     * {{{
     * Maybe.present(8080).toOption // Some(8080)
     * Maybe.absent[Int].toOption   // None
     * }}}
     */
    @inline def toOption: Option[A] =
      self match {
        case Present(value) => Some(value)
        case Absent         => None
      }

    /**
     * Returns a one-element `List` of the value, or `Nil` if this `Maybe` is
     * absent.
     */
    @inline def toList: List[A] =
      self match {
        case Present(value) => value :: Nil
        case Absent         => Nil
      }

    /**
     * Returns a one-element `Seq` of the value, or an empty `Seq` if this
     * `Maybe` is absent.
     */
    @inline def toSeq: Seq[A] =
      self match {
        case Present(value) => Seq(value)
        case Absent         => Seq.empty
      }

    /**
     * Returns an iterator over the value, which is empty if this `Maybe` is
     * absent.
     */
    @inline def iterator: Iterator[A] =
      self match {
        case Present(value) => Iterator.single(value)
        case Absent         => Iterator.empty
      }

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
    @inline def map[B](f: A => B): zio.blocks.maybe.Maybe[B] =
      self match {
        case Present(value) => Maybe.present(f(value))
        case Absent         => Maybe.absent
      }

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
    @inline def flatMap[B](f: A => zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] =
      self match {
        case Present(value) => f(value)
        case Absent         => Maybe.absent
      }

    /**
     * Removes one level of nesting: a present `Maybe` that holds a present
     * value gives that value, and one that holds an absent `Maybe` gives
     * absent.
     */
    @inline def flatten[B](implicit ev: A <:< zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] =
      self match {
        case Present(value) => ev(value)
        case Absent         => Maybe.absent
      }

    /**
     * Runs `f` on the value if this `Maybe` is present, and does nothing
     * otherwise; on the view that `withFilter` returns, a value that fails the
     * view's predicate counts as absent.
     */
    @inline def foreach[U](f: A => U): Unit =
      self match {
        case Present(value) =>
          f(value)
          ()
        case Absent => ()
      }

    /**
     * Returns `true` if this `Maybe` is present and its value equals `elem`.
     */
    @inline def contains[A1 >: A](elem: A1): Boolean =
      self match {
        case Present(value) => value == elem
        case Absent         => false
      }

    /**
     * Returns `true` if this `Maybe` is present and its value satisfies `p`.
     */
    @inline def exists(p: A => Boolean): Boolean =
      self match {
        case Present(value) => p(value)
        case Absent         => false
      }

    /**
     * Returns `true` if this `Maybe` is absent or its value satisfies `p`.
     */
    @inline def forall(p: A => Boolean): Boolean =
      self match {
        case Present(value) => p(value)
        case Absent         => true
      }

    /**
     * Returns this `Maybe` if it is present and its value satisfies `p`, and an
     * absent `Maybe` otherwise.
     *
     * {{{
     * val port: Maybe[Int] = Maybe.present(80)
     * port.filter(_ >= 1024).isAbsent // true
     * }}}
     */
    @inline def filter(p: A => Boolean): zio.blocks.maybe.Maybe[A] =
      self match {
        case Present(value) if p(value) => self
        case _                          => Maybe.absent
      }

    /**
     * Returns this `Maybe` if it is present and its value does not satisfy `p`,
     * and an absent `Maybe` otherwise.
     */
    @inline def filterNot(p: A => Boolean): zio.blocks.maybe.Maybe[A] =
      self match {
        case Present(value) if p(value) => Maybe.absent
        case Present(_)                 => self
        case Absent                     => Maybe.absent
      }

    /**
     * Returns a present `Maybe` of `pf` applied to the value if `pf` is defined
     * at it, and an absent `Maybe` otherwise.
     */
    @inline def collect[B](pf: PartialFunction[A, B]): zio.blocks.maybe.Maybe[B] =
      self match {
        case Present(value) if pf.isDefinedAt(value) => Maybe.present(pf(value))
        case _                                       => Maybe.absent
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
    @inline def withFilter(p: A => Boolean): MaybeWithFilter[A] =
      new MaybeWithFilter[A](self, p)

    /**
     * Returns `Right` of the value, or `Left(left)` if this `Maybe` is absent.
     * `left` is evaluated only in that case.
     */
    @inline def toRight[X](left: => X): Either[X, A] =
      self match {
        case Present(value) => Right(value)
        case Absent         => Left(left)
      }

    /**
     * Returns `Left` of the value, or `Right(right)` if this `Maybe` is absent.
     * `right` is evaluated only in that case.
     */
    @inline def toLeft[X](right: => X): Either[A, X] =
      self match {
        case Present(value) => Left(value)
        case Absent         => Right(right)
      }

    /**
     * Returns a present `Maybe` of both values as a pair if both `Maybe`s are
     * present, and an absent `Maybe` otherwise.
     */
    @inline def zip[B](that: zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[(A, B)] =
      (self, that) match {
        case (Present(left), Present(right)) => Maybe.present((left, right))
        case _                               => Maybe.absent
      }

    /**
     * Splits a `Maybe` of a pair into a pair of `Maybe`s, both absent if this
     * `Maybe` is absent.
     */
    @inline def unzip[A1, A2](implicit ev: A <:< (A1, A2)): (zio.blocks.maybe.Maybe[A1], zio.blocks.maybe.Maybe[A2]) =
      self match {
        case Present(value) =>
          val tuple = ev(value)
          (Maybe.present(tuple._1), Maybe.present(tuple._2))
        case Absent => (Maybe.absent, Maybe.absent)
      }

    /**
     * Splits a `Maybe` of a triple into three `Maybe`s, all absent if this
     * `Maybe` is absent.
     */
    @inline def unzip3[A1, A2, A3](implicit
      ev: A <:< (A1, A2, A3)
    ): (zio.blocks.maybe.Maybe[A1], zio.blocks.maybe.Maybe[A2], zio.blocks.maybe.Maybe[A3]) =
      self match {
        case Present(value) =>
          val tuple = ev(value)
          (Maybe.present(tuple._1), Maybe.present(tuple._2), Maybe.present(tuple._3))
        case Absent => (Maybe.absent, Maybe.absent, Maybe.absent)
      }
  }

  /**
   * The view that `withFilter` returns: a `Maybe` together with a predicate, so
   * that a for-comprehension with an `if` guard compiles. Each method gives the
   * same result as calling it on the `Maybe` filtered by the predicate.
   */
  final class MaybeWithFilter[A](self: zio.blocks.maybe.Maybe[A], predicate: A => Boolean) {

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
    def map[B](f: A => B): zio.blocks.maybe.Maybe[B] = self.filter(predicate).map(f)

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
    def flatMap[B](f: A => zio.blocks.maybe.Maybe[B]): zio.blocks.maybe.Maybe[B] = self.filter(predicate).flatMap(f)

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
    def withFilter(q: A => Boolean): MaybeWithFilter[A] = new MaybeWithFilter[A](self, x => predicate(x) && q(x))
  }
}

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
  @inline def apply[A](a: A): Maybe[A] = if (a == null) absent else present(a)

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
  @inline def present[A](a: A): Maybe[A] = MaybeValue.Present(a)

  /**
   * Returns the absent value, typed as a `Maybe[A]`.
   *
   * {{{
   * val timeout: Maybe[Int] = Maybe.absent
   * timeout.getOrElse(30) // 30
   * }}}
   */
  @inline def absent[A]: Maybe[A] = MaybeValue.Absent

  /** Returns the absent value; the same as `Maybe.absent`. */
  @inline def empty[A]: Maybe[A] = MaybeValue.Absent

  /** On Scala 2, returns `MaybeValue.Absent`, the absent value. */
  def Absent: Maybe[Nothing] = MaybeValue.Absent

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
    case None    => absent
  }

  /**
   * Converts an `Option` to a `Maybe` implicitly, as `fromOption` does. On
   * Scala 2 the compiler finds it only after `import Maybe._` (or
   * `import Maybe.optionToMaybe`), and the call site needs
   * `scala.language.implicitConversions`. On Scala 3 a `given Conversion` in
   * `Maybe` applies without an import.
   */
  implicit def optionToMaybe[A](opt: Option[A]): Maybe[A] = fromOption(opt)

  /**
   * Returns `true` if `x` is the absent value. Used by other ZIO Blocks
   * modules, such as `schema`; not for public use.
   */
  private[blocks] def unsafeIsAbsent(x: Maybe[Any]): Boolean = x eq MaybeValue.Absent

  /**
   * Returns the value, unwrapping the `MaybeValue.Present`, or `null` if `x` is
   * absent. Used by other ZIO Blocks modules, such as `schema`; not for public
   * use.
   */
  private[blocks] def unsafeGet(x: Maybe[Any]): Any = x match {
    case MaybeValue.Present(value) => value
    case MaybeValue.Absent         => null
  }

  /**
   * Returns `x` as a `Maybe`, `null` giving the absent value and any other
   * value a `MaybeValue.Present`. Used by other ZIO Blocks modules, such as
   * `schema`; not for public use.
   */
  private[blocks] def unsafeWrap[A](x: Any): Maybe[A] =
    if (x == null) MaybeValue.Absent else MaybeValue.Present(x.asInstanceOf[A])
}
