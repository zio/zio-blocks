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

package zio.blocks.schema.toon.codec

import zio.blocks.schema._
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.toon._

private[toon] final class MapCodecBuilder(
  codecDeriver: CodecDeriver
) {
  private def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): ToonBinaryCodec[A] = codecDeriver.derive(reflect)

  def build[F[_, _], Key, Value, Map[_, _]](
    map: Reflect.Map[F, Key, Value, Map],
    binding: Binding.Map[Map, Key, Value]
  ): ToonBinaryCodec[Map[Key, Value]] = {
    val keyCodec   = deriveCodec(map.key).asInstanceOf[ToonBinaryCodec[Key]]
    val valueCodec = deriveCodec(map.value).asInstanceOf[ToonBinaryCodec[Value]]
    new ToonBinaryCodec[Map[Key, Value]]() {
      private[this] val deconstructor = binding.deconstructor
      private[this] val constructor   = binding.constructor
      private[this] val kCodec        = keyCodec
      private[this] val vCodec        = valueCodec

      def decodeValue(in: ToonReader, default: Map[Key, Value]): Map[Key, Value] = {
        in.skipBlankLines()
        if (!in.hasMoreContent || in.peekTrimmedContent.isEmpty) {
          in.advanceLine()
          in.skipBlankLines()
        }
        val builder    = constructor.newObjectBuilder[Key, Value](8)
        val startDepth = in.getDepth
        while (in.hasMoreLines) {
          in.skipBlankLines()
          if (!in.hasMoreLines || in.getDepth < startDepth) {
            return constructor.resultObject[Key, Value](builder)
          }
          val keyStr    = in.readKey()
          val keyReader = ToonReader(ReaderConfig)
          keyReader.reset(keyStr)
          val key   = kCodec.decodeValue(keyReader, kCodec.nullValue)
          val value = vCodec.decodeValue(in, vCodec.nullValue)
          constructor.addObject(builder, key, value)
        }
        constructor.resultObject[Key, Value](builder)
      }

      def encodeValue(x: Map[Key, Value], out: ToonWriter): Unit = {
        val iter = deconstructor.deconstruct(x)
        while (iter.hasNext) {
          val kv    = iter.next()
          val key   = deconstructor.getKey(kv)
          val value = deconstructor.getValue(kv)
          out.writeKey(encodeKeyToString(key))
          vCodec.encodeValue(value, out)
          out.newLine()
        }
      }

      override def encodeAsField(fieldName: String, x: Map[Key, Value], out: ToonWriter): Unit = {
        out.writeKeyOnly(fieldName)
        out.incrementDepth()
        encodeValue(x, out)
        out.decrementDepth()
      }

      private def encodeKeyToString(key: Key): String = {
        val keyWriter = ToonWriter.fresh(WriterConfig)
        kCodec.encodeValue(key, keyWriter)
        new String(keyWriter.toByteArray, java.nio.charset.StandardCharsets.UTF_8)
      }

      override def nullValue: Map[Key, Value] = constructor.emptyObject[Key, Value]
    }
  }
}
