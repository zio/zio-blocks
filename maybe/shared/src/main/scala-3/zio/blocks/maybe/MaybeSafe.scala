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

import scala.quoted.*

/**
 * A compile-time guard that ensures `Maybe.apply` and `Maybe.present` are only
 * called with type parameters that produce a sound `Maybe[A]`.
 *
 * `Maybe[+A]` is an opaque type alias for `A | Null`. When `A` is `Null`,
 * `Any`, `AnyRef`, another `Maybe[_]`, or a union type, the resulting `Maybe`
 * either degenerates (e.g. `Maybe[Maybe[String]] = String | Null =
 * Maybe[String]`) or admits unsound values that violate `Maybe`'s invariants.
 *
 * `MaybeSafe` has no runtime representation — it exists only to guide implicit
 * resolution at compile time. Its `given` instance is implemented with a Scala
 * 3 macro that inspects the type argument and rejects unsound types with a
 * clear error message.
 *
 * @tparam A
 *   the type to validate — a `MaybeSafe[A]` instance is available exactly when
 *   `A` is a sound `Maybe` element type
 */
sealed trait MaybeSafe[A]

/**
 * Provides `MaybeSafe` evidence via a Scala 3 macro.
 *
 * The macro performs structural checks that `NotGiven` cannot express, in
 * particular the exclusion of union types (e.g. `String | Null`).
 */
object MaybeSafe {
  transparent inline given [A]: MaybeSafe[A] = ${ maybeSafeImpl[A] }

  def maybeSafeImpl[A: Type](using Quotes): Expr[MaybeSafe[A]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[A]

    // 1. Reject union types (OrType) — NotGiven cannot express this check.
    //    Union types can embed Null or Maybe, e.g. String | Null or Maybe[Int] | Null,
    //    which cause the opaque underlying type A | Null to degenerate.
    tpe match {
      case _: OrType =>
        report.errorAndAbort(
          errorMsg("union types are not supported as Maybe elements")
        )
      case _ => ()
    }

    // 2. Reject Null — Maybe[Null] = Null | Null = Null = Maybe.absent.
    if tpe =:= TypeRepr.of[Null] then report.errorAndAbort(errorMsg("Maybe[Null] degenerates to Null"))

    // 3. Reject Any — too broad to produce a sound Maybe element.
    if tpe =:= TypeRepr.of[Any] then report.errorAndAbort(errorMsg("Maybe[Any] is too broad"))

    // 4. Reject AnyRef — too broad; also catches aliases of AnyRef.
    if tpe =:= TypeRepr.of[AnyRef] then report.errorAndAbort(errorMsg("Maybe[AnyRef] is too broad"))

    // 5. Reject nested Maybe[_] — e.g. Maybe[Maybe[String]].
    //    Since Maybe[+A] = A | Null, the outer A | Null becomes (A | Null) | Null = A | Null = Maybe[A].
    val maybeSym = TypeRepr.of[Maybe[Any]].typeSymbol
    tpe match {
      case AppliedType(tycon, _) if tycon.typeSymbol == maybeSym =>
        report.errorAndAbort(errorMsg("nested Maybe types are not supported"))
      case _ => ()
    }

    '{ null.asInstanceOf[MaybeSafe[A]] }
  }

  private def errorMsg(cause: String): String =
    s"MaybeSafe validation failed — $cause. Use a concrete, non-nullable type (e.g. String, Int, User)."
}
