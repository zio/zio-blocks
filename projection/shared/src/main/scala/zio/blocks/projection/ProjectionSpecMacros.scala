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

package zio.blocks.projection

import scala.quoted.*

object ProjectionSpecMacros {

  def updateImpl[A: Type, E: Type](
    builder: Expr[HandlerBuilder[E, A]],
    selector: Expr[A => Any],
    f: Expr[(E, ProjectionContext) => Any]
  )(using Quotes): Expr[ProjectionSpec[A]] =
    '{ $builder.updateWithField(${ FieldSelectorMacros.extractAndMapFieldImpl[A](selector) }, $f) }

  def routedByFieldImpl[A: Type, E: Type](
    builder: Expr[FromBuilder[A]],
    selector: Expr[E => Any]
  )(using Quotes): Expr[ProjectionSpec[A]] =
    '{ $builder.routedBy((e: E) => $selector(e).toString) }
}
