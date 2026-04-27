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

package zio.blocks.sql

import scala.quoted._

private[sql] object SqlMacros {

  def sqlImpl(sc: Expr[StringContext], args: Expr[Seq[DbValue]])(using Quotes): Expr[Frag] = {
    import quotes.reflect._

    val parts: Option[Seq[String]] = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        Some(rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort })
      case _ => None
    }

    parts.foreach { ps =>
      SqlValidator.validate(ps).foreach(report.errorAndAbort(_))
    }

    '{ Frag($sc.parts.toIndexedSeq, $args.toIndexedSeq) }
  }
}
