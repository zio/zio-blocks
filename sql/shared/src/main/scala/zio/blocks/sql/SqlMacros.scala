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

  def sqlImpl(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Frag] = {
    import quotes.reflect._

    val parts: Option[Seq[String]] = sc match {
      case '{ StringContext(${ Varargs(rawParts) }: _*) } =>
        Some(rawParts.map { case '{ $rawPart: String } => rawPart.valueOrAbort })
      case _ => None
    }

    parts.foreach { ps =>
      SqlValidator.validate(ps).foreach(report.errorAndAbort(_))
    }

    val convertedArgs: Expr[IndexedSeq[DbValue]] = args match {
      case Varargs(argExprs) =>
        val converted: Seq[Expr[DbValue]] = argExprs.map { arg =>
          arg match {
            case '{ $a: DbValue } => a
            case '{ $a: t }       =>
              val widened = TypeRepr.of[t].widen.asType
              widened match {
                case '[w] =>
                  Expr.summon[DbParam[w]] match {
                    case Some(param) => '{ $param.toDbValue(${ a.asExprOf[w] }) }
                    case None        =>
                      report.errorAndAbort(
                        s"No DbParam instance found for type ${Type.show[w]}. " +
                          "Only types with a DbParam[T] instance can be interpolated into sql\"...\".",
                        arg.asTerm.pos
                      )
                  }
              }
          }
        }
        '{ IndexedSeq(${ Varargs(converted) }: _*) }
      case _ =>
        '{
          $args.map {
            case v: DbValue => v; case other => throw new IllegalArgumentException(s"Unexpected value: $other")
          }.toIndexedSeq
        }
    }

    '{ Frag($sc.parts.toIndexedSeq, $convertedArgs) }
  }
}
