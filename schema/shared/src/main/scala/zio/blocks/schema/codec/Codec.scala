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

package zio.blocks.schema.codec
import zio.blocks.schema.SchemaError

/**
 * Represents an abstraction for encoding and decoding values between a specific
 * input representation and a specific output representation.
 *
 * @tparam DecodeInput
 *   The type of input data used for decoding operations.
 * @tparam EncodeOutput
 *   The type of output data used for encoding operations.
 * @tparam Value
 *   The type of values being encoded or decoded.
 */
abstract class Codec[DecodeInput, EncodeOutput, Value] {

  /**
   * Encodes the given value into the specified output representation.
   *
   * @param value
   *   The value to be encoded.
   * @param output
   *   The target output into which the value will be encoded.
   * @return
   *   This method does not return a value.
   */
  def encode(value: Value, output: EncodeOutput): Unit

  /**
   * Decodes the specified input into a value of the target type.
   *
   * @param input
   *   The input data that needs to be decoded.
   * @return
   *   Either a `SchemaError` indicating the decoding failure, or a decoded
   *   value.
   */
  def decode(input: DecodeInput): Either[SchemaError, Value]
}
