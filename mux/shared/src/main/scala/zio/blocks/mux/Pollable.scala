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

package zio.blocks.mux

import scala.quoted.*

/**
 * Compile-time proof that a message type can flow through the allocation-free
 * poll API without colliding with the empty/error outcomes.
 *
 * `MuxStream.receive` returns `Out | MuxEmpty | MuxError` with no
 * `Some`/`Option` wrapper on the hot path. That direct union is only
 * unambiguous when no value can be both a message and an empty/error outcome.
 * `Pollable[A]` witnesses exactly that: summoning it for a message type proves
 * (at compile time) that `A` is disjoint from the poll outcome family
 * (`MuxError` and `MuxEmpty`).
 *
 * The check runs in the [[Pollable.derived]] macro when `Mux.apply` summons
 * evidence for its `In`/`Out` parameters, so every library-constructed mux is
 * gated at construction. Hand-rolled `MuxStream` implementations that bypass
 * `Mux.apply` with overlapping message types are unsupported: their poll
 * results would be ambiguous by construction.
 *
 * ===What is accepted===
 *   - Concrete reference types disjoint from the error family: `String`,
 *     `Array[Byte]`, final case classes, and sealed traits/hierarchies (e.g.
 *     HTTP/2 frame ADTs) that no error variant extends.
 *   - Transparent aliases that resolve to an accepted type (`type Msg =
 *     String`), and singleton/literal types that widen to one.
 *   - Generic containers over abstract element types (`List[A]`): the
 *     container's runtime class never changes with its arguments, and no error
 *     class extends the container, so disjointness holds for every
 *     instantiation. (Dually, a container *of* errors such as `List[MuxError]`
 *     is accepted: the list itself is disjoint from the error family; its
 *     elements are inert data, never confused with the poll outcome.)
 *   - `Null`/`Nothing`: no error value inhabits them — `Nothing` is
 *     uninhabited, and `null` payloads are rejected at runtime with
 *     `ProtocolError` before they could collide.
 *
 * ===What is rejected===
 *   - Supertypes of errors: `Any`, `AnyRef`, `Matchable`, `Object`, `Product`,
 *     `Serializable`, ... A `StreamClosed` value *is* a `Product`, so a
 *     `Product`-typed message could be an error in disguise. Note that a naive
 *     `NotGiven[A <:< MuxError]` check would wrongly accept these: `Any` is not
 *     a *subtype* of `MuxError`, yet every error *is* an `Any`. The macro
 *     therefore checks conformance in both directions (see below).
 *   - Opaque aliases, abstract members, parameterized aliases, and match types:
 *     their right-hand side is invisible or unreducible from the use site, so
 *     disjointness cannot be proven — even when the hidden type would be safe.
 *     This is deliberately conservative: an `opaque type Msg = MuxError`
 *     legally carries error values, so accepting it would make poll results
 *     ambiguous. Summon the evidence inside the scope where the alias is
 *     defined instead.
 *   - Generic or abstract message types (bare type parameters, abstract
 *     intersections/unions such as `A | String`, and transparent aliases that
 *     hide any of those, e.g. `type Alias = A`): they could be instantiated
 *     with an overlapping type, and the derivation resolves aliases with
 *     `dealias` precisely so such hiding cannot bypass the gate. Generic code
 *     should propagate the evidence instead: `def drain[A](mux: Mux[Int, A,
 *     A])(using Pollable[A])` summons the caller's evidence without re-running
 *     the check, and the concrete call site at the end of the chain proves it.
 *
 * ===Soundness argument===
 * `MuxError` is `sealed` in `MuxError.scala`, so no new error variants can
 * appear outside it, and all current variants are `final` (the `MuxClosed`
 * singleton included). The blocker set is derived from the family's actual
 * children at compile time — not from a count — so a replacement variant with
 * the same child count is enumerated just the same; derivation fails closed
 * when the children metadata is unavailable. The checks are:
 *   1. Structural gate: type variables, abstract members, opaque aliases,
 *      parameterized aliases, and match types abort because disjointness is
 *      unprovable for them. Transparent aliases are resolved with `dealias`
 *      (the opaque check runs before dealiasing so an opaque origin can never
 *      be erased, and the recursion is depth-bounded so termination is
 *      obvious). Type arguments nested under a provable constructor are left
 *      alone per the container rule above.
 *   2. Singleton collapse: the checked type is widened, so a stable error value
 *      cannot hide behind its singleton type (`q.type` widens to `QueueFull`,
 *      which the blockers catch).
 *   3. Forward conformance: if any blocker (error variant, whole family, or
 *      empty singleton) conforms to the message type, some error/empty value
 *      inhabits it — ambiguous, reject. This catches supertypes, unions
 *      containing errors, and the error/empty types themselves.
 *   4. Reverse conformance: if the message type itself conforms to `MuxError`
 *      or `MuxEmpty`, every message *is* an error/empty outcome (e.g.
 *      intersections such as `X & MuxError`) — ambiguous, reject. Bottom types
 *      (`Nothing`, `Null`) are exempt: both are provably disjoint (see above)
 *      despite conforming to everything.
 */
sealed trait Pollable[A]

/**
 * The companion of [[Pollable]]. Its `derived` given builds the evidence at
 * compile time with the checks described on [[Pollable]], so `Mux.apply` finds
 * it for a message type with no explicit instance. Compilation fails for a
 * message type that overlaps `MuxError` or `MuxEmpty`, and for a bare type
 * parameter, abstract type member, opaque alias or match type, because
 * disjointness cannot be proven for it. A generic container such as `List[A]`
 * is accepted.
 */
object Pollable {

  /**
   * Derive [[Pollable]] evidence for a concrete message type, failing
   * compilation with an explicit diagnostic for overlapping or unprovable
   * types.
   */
  inline given derived[A]: Pollable[A] = ${ PollableMacros.derive[A] }
}

private[mux] object PollableMacros {

  /** Diagnostic for message types that overlap the poll outcome family. */
  val overlapMessage: String =
    "Pollable derivation failed: the message type overlaps MuxError or MuxEmpty, " +
      "so poll results would be ambiguous. Choose a message type disjoint from MuxError and MuxEmpty " +
      "(for example a final case class or a sealed trait that no error variant extends)."

  /** Diagnostic for message types whose disjointness cannot be proven. */
  val abstractMessage: String =
    "Pollable derivation failed: the message type is generic or abstract, so disjointness from " +
      "MuxError and MuxEmpty cannot be proven. Add an explicit Pollable evidence parameter " +
      "(for example def drain[A](mux: Mux[Int, A, A])(using Pollable[A])) and summon it at a " +
      "concrete call site with a type disjoint from MuxError and MuxEmpty."

  /** Diagnostic for opaque, parameterized, or match message types. */
  val opaqueMessage: String =
    "Pollable derivation failed: the message type is an opaque alias, a parameterized alias, or a " +
      "match type, so disjointness from MuxError and MuxEmpty cannot be proven from this scope " +
      "(an opaque alias for MuxError would legally carry error values). Use a concrete message type, " +
      "or summon Pollable evidence inside the scope where the alias is defined."

  /** Diagnostic for unavailable sealed-family metadata (fail closed). */
  val driftMessage: String =
    "Pollable derivation failed: the sealed MuxError family exposes no children in this compilation, " +
      "so the error blocker set cannot be derived. Failing closed: disjointness cannot be proven " +
      "without it."

  def derive[A: Type](using Quotes): Expr[Pollable[A]] = {
    import quotes.reflect.*

    val errorRepr   = TypeRepr.of[MuxError]
    val emptyRepr   = TypeRepr.of[MuxEmpty]
    val nothingRepr = TypeRepr.of[Nothing]
    val nullRepr    = TypeRepr.of[Null]

    // Structural gate: abort on anything whose disjointness is unprovable
    // (type variables, abstract members, opaque aliases, parameterized
    // aliases, match types). Transparent aliases are resolved with dealias
    // (opaque origin is checked BEFORE dealiasing so opacity can never be
    // erased); the recursion is bounded so termination is obvious — legal
    // code cannot contain cyclic aliases anyway. Type arguments nested under
    // a provable constructor are left alone: the container's runtime class
    // never changes with its arguments.
    val maxAliasDepth                                    = 64
    def normalize(t: TypeRepr, depth: Int = 0): TypeRepr =
      if (depth > maxAliasDepth) report.errorAndAbort(opaqueMessage)
      else
        t match {
          case _: ParamRef => report.errorAndAbort(abstractMessage)
          case tr: TypeRef =>
            val s = tr.typeSymbol
            if (s.isTypeParam) report.errorAndAbort(abstractMessage)
            else if (s.flags.is(Flags.Opaque)) report.errorAndAbort(opaqueMessage)
            else if (s.flags.is(Flags.Deferred)) report.errorAndAbort(abstractMessage)
            else if (s.isAliasType && s.paramSymss.nonEmpty && s.paramSymss.exists(_.nonEmpty))
              report.errorAndAbort(opaqueMessage)
            else {
              val d = tr.dealias
              if (d =:= t) t else normalize(d, depth + 1)
            }
          case AppliedType(tycon, _)        => normalize(tycon, depth + 1); t
          case AndType(l, r)                => normalize(l, depth + 1); normalize(r, depth + 1); t
          case OrType(l, r)                 => normalize(l, depth + 1); normalize(r, depth + 1); t
          case Refinement(parent, _, _)     => normalize(parent, depth + 1); t
          case AnnotatedType(underlying, _) => normalize(underlying, depth + 1); t
          case _: MatchType                 => report.errorAndAbort(opaqueMessage)
          case _                            => t
        }

    val checked = normalize(TypeRepr.of[A])

    // Collapse singleton/literal types so a stable error value cannot hide
    // behind its singleton type.
    val widened = checked.widen

    // Blocker set derived from the sealed family's actual children, so a
    // replacement variant with the same child count is enumerated just the
    // same. Whole-family and singleton backstops cover any representation
    // quirks of children. Fail closed when the metadata is unavailable.
    val direct = errorRepr.typeSymbol.children
    if (direct.isEmpty) report.errorAndAbort(driftMessage)
    def leaves(s: Symbol): List[Symbol] = {
      val c = s.children
      if (c.isEmpty) List(s) else c.flatMap(leaves)
    }
    val derived: List[TypeRepr] = direct.flatMap(leaves).flatMap { s =>
      if (s.isClassDef) {
        val m = s.companionModule
        if (s.flags.is(Flags.Module) && !m.isNoSymbol) List(m.termRef, s.typeRef)
        else List(s.typeRef)
      } else List(s.termRef)
    }
    val blockers: List[TypeRepr] =
      errorRepr :: emptyRepr :: TypeRepr.of[MuxError.MuxClosed.type] :: derived

    // Forward conformance: an error/empty value inhabits the message type.
    if (blockers.exists(_ <:< widened)) report.errorAndAbort(overlapMessage)

    // Reverse conformance: every message is an error/empty outcome, e.g.
    // intersections such as `X & MuxError`. Bottom types are exempt: both are
    // provably disjoint despite conforming to everything.
    val isBottom = (widened =:= nothingRepr) || (widened =:= nullRepr)
    if (!isBottom) {
      if (checked <:< errorRepr) report.errorAndAbort(overlapMessage)
      if (checked <:< emptyRepr) report.errorAndAbort(overlapMessage)
    }

    '{ new Pollable[A] {} }
  }
}
