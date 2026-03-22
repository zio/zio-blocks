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

package zio.blocks.schema.csv

import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.TextCodec

import scala.annotation.switch

/**
 * Abstract codec for encoding and decoding values to/from CSV format.
 *
 * Extends `TextCodec[A]` to provide CSV-specific serialization.
 *
 * @param valueType
 *   integer representing the register type for this codec (0=object, 1=int,
 *   2=long, 3=float, 4=double, 5=boolean, 6=byte, 7=char, 8=short, 9=unit)
 * @tparam A
 *   The type being encoded/decoded
 */
abstract class CsvCodec[A](val valueType: Int = CsvCodec.objectType) extends TextCodec[A] {

  /**
   * Computes the appropriate `RegisterOffset` based on the value type.
   */
  val valueOffset: RegisterOffset.RegisterOffset = (valueType: @switch) match {
    case 0 => RegisterOffset(objects = 1)
    case 1 => RegisterOffset(ints = 1)
    case 2 => RegisterOffset(longs = 1)
    case 3 => RegisterOffset(floats = 1)
    case 4 => RegisterOffset(doubles = 1)
    case 5 => RegisterOffset(booleans = 1)
    case 6 => RegisterOffset(bytes = 1)
    case 7 => RegisterOffset(chars = 1)
    case 8 => RegisterOffset(shorts = 1)
    case _ => RegisterOffset.Zero
  }

  /**
   * Returns the header names for CSV columns.
   *
   * @return
   *   field names that will appear in the CSV header row
   */
  def headerNames: IndexedSeq[String]

  /**
   * Returns the null/default value for this type.
   *
   * Used when decoding missing or empty fields.
   *
   * @return
   *   the default value
   */
  def nullValue: A
}

object CsvCodec {
  final val objectType  = 0
  final val intType     = 1
  final val longType    = 2
  final val floatType   = 3
  final val doubleType  = 4
  final val booleanType = 5
  final val byteType    = 6
  final val charType    = 7
  final val shortType   = 8
  final val unitType    = 9
}
