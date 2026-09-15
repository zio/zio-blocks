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

package zio.blocks.endpoint

import scala.quoted.*

private[endpoint] trait SegmentCodecPlatformSpecific {

  inline def literal(inline value: String): SegmentCodec.Literal =
    ${ SegmentCodecPlatformSpecificMacros.literalImpl('value) }
}

private[endpoint] object SegmentCodecPlatformSpecificMacros {

  def literalImpl(valueExpr: Expr[String])(using Quotes): Expr[SegmentCodec.Literal] =
    valueExpr.value match {
      case Some(value) =>
        try {
          SegmentCodec.validateLiteralValue(value)
          '{ SegmentCodec.literalValidated(${ Expr(value) }) }
        } catch {
          case error: IllegalArgumentException =>
            quotes.reflect.report.errorAndAbort(error.getMessage)
        }
      case None =>
        quotes.reflect.report.errorAndAbort("SegmentCodec.literal requires a string literal known at compile time")
    }
}
