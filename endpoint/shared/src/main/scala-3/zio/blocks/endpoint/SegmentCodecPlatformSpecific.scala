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

/**
 * Constructors of the [[SegmentCodec]] companion. The Scala 2 and Scala 3
 * copies differ in how `literal` validates its value and in how the segment
 * names are bounded to a singleton type.
 */
private[endpoint] trait SegmentCodecPlatformSpecific {

  /**
   * Creates a segment that matches `value` exactly. `value` must be one path
   * segment that needs no URL encoding: an empty value, one with `/`, and one
   * with a character such as a space, `%`, `?`, `#` or a non-ASCII letter is
   * rejected. Scala 2 checks at run time and throws; Scala 3 checks at compile
   * time, so `value` must be a compile-time constant and a bad one is a compile
   * error.
   *
   * @throws java.lang.IllegalArgumentException
   *   on Scala 2, when `value` is not valid
   */
  inline def literal(inline value: String): SegmentCodec.Literal =
    ${ SegmentCodecPlatformSpecificMacros.literalImpl('value) }

  // `String & Singleton` preserves literal singletons on Scala 3.
  // (Scala 2.13 has no `&` types; it uses `with`.)
  /**
   * Creates a segment that captures a `Boolean` under `name`, kept as a
   * singleton type.
   */
  def bool[N <: String & Singleton](name: N): SegmentCodec.BoolSeg[N] = SegmentCodec.BoolSeg(name)

  /**
   * Creates a segment that captures an `Int` under `name`, kept as a singleton
   * type.
   */
  def int[N <: String & Singleton](name: N): SegmentCodec.IntSeg[N] = SegmentCodec.IntSeg(name)

  /**
   * Creates a segment that captures a `Long` under `name`, kept as a singleton
   * type.
   */
  def long[N <: String & Singleton](name: N): SegmentCodec.LongSeg[N] = SegmentCodec.LongSeg(name)

  /**
   * Creates a segment that captures a `String` under `name`, kept as a
   * singleton type.
   */
  def string[N <: String & Singleton](name: N): SegmentCodec.StringSeg[N] = SegmentCodec.StringSeg(name)

  /**
   * Creates a segment that captures a `java.util.UUID` under `name`, kept as a
   * singleton type.
   */
  def uuid[N <: String & Singleton](name: N): SegmentCodec.UUIDSeg[N] = SegmentCodec.UUIDSeg(name)
}

/** Macro implementation of [[SegmentCodec.literal]]. */
private[endpoint] object SegmentCodecPlatformSpecificMacros {

  /**
   * Checks `valueExpr` at compile time and expands to the validated literal
   * segment.
   */
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
