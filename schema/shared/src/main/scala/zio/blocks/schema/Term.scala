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

import zio.blocks.docs.Doc
import zio.blocks.schema.binding._

/**
 * A named child of a [[Reflect]] node: a field of a record or a case of a
 * variant. `S` is the type of the record or variant, and `A` the type of the
 * field or case; the type members `Source` and `Focus` name them.
 *
 * @param name
 *   the name of the field or case
 * @param value
 *   the node of `A`
 * @param doc
 *   the documentation of the field or case
 * @param modifiers
 *   modifiers such as `Modifier.rename` or `Modifier.transient`
 */
final case class Term[F[_, _], S, A](
  name: String,
  value: Reflect[F, A],
  doc: Doc = Doc.empty,
  modifiers: Seq[Modifier.Term] = Nil
) extends Reflectable[A] {
  require(value ne null)

  type Source = S
  type Focus  = A

  /**
   * Returns this term with `value` transformed by `f`, at `path` extended with
   * this term's field step (for `Term.Type.Record`) or case step (for
   * `Term.Type.Variant`). The name, documentation and modifiers stay the same.
   */
  def transform[G[_, _]](path: DynamicOptic, termType: Term.Type, f: ReflectTransformer[F, G]): Lazy[Term[G, S, A]] =
    for {
      value <- value.transform(if (termType == Term.Type.Record) path.field(name) else path.caseOf(name), f)
    } yield new Term(name, value, doc, modifiers)

  override def toString: String = ReflectPrinter.printTerm(this)
}

/** The kinds of term, and helpers for [[Term]]. */
object Term {

  /** Whether a [[Term]] is a field of a record or a case of a variant. */
  sealed trait Type

  /** The two kinds of term: `Record` for a field, `Variant` for a case. */
  object Type {
    case object Record  extends Type
    case object Variant extends Type
  }

  /** A term of a bound reflect. */
  type Bound[S, A] = Term[Binding, S, A]

  /**
   * A function that replaces a term of any type, which
   * `Reflect.Record#modifyField` and `Reflect.Variant#modifyCase` apply.
   */
  trait Updater[F[_, _]] {

    /**
     * Returns the term that replaces `input`, or `None`, which makes
     * `modifyField` or `modifyCase` return `None`.
     */
    def update[S, A](input: Term[F, S, A]): Option[Term[F, S, A]]
  }
}
