/*
 * Copyright 2023 ZIO Blocks Maintainers
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

import zio.blocks.schema.binding._

final case class Term[F[_, _], S, A](
  name: String,
  value: Reflect[F, A],
  doc: Doc = Doc.Empty,
  modifiers: Seq[Modifier.Term] = Nil
) extends Reflectable[A] {
  require(value ne null)

  type Source = S
  type Focus  = A

  def transform[G[_, _]](path: DynamicOptic, termType: Term.Type, f: ReflectTransformer[F, G]): Lazy[Term[G, S, A]] =
    for {
      value <- value.transform(if (termType == Term.Type.Record) path.field(name) else path.caseOf(name), f)
    } yield new Term(name, value, doc, modifiers)
}

object Term {
  sealed trait Type

  object Type {
    case object Record  extends Type
    case object Variant extends Type
  }

  type Bound[S, A] = Term[Binding, S, A]

  trait Updater[F[_, _]] {
    def update[S, A](input: Term[F, S, A]): Option[Term[F, S, A]]
  }
}
