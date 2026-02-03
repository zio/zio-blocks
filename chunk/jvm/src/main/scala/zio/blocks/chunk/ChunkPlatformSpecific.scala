/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.chunk

import scala.reflect.ClassTag

private[chunk] trait ChunkPlatformSpecific {

  private[chunk] object Tags {
    def fromValue[A](a: A): ClassTag[A] = {
      if (a == null) ClassTag.AnyRef
      else {
        val c = a.getClass
        if (c eq BooleanClassBox) ClassTag.Boolean
        else if (c eq ByteClassBox) ClassTag.Byte
        else if (c eq ShortClassBox) ClassTag.Short
        else if (c eq IntClassBox) ClassTag.Int
        else if (c eq LongClassBox) ClassTag.Long
        else if (c eq FloatClassBox) ClassTag.Float
        else if (c eq DoubleClassBox) ClassTag.Double
        else if (c eq CharClassBox) ClassTag.Char
        else ClassTag.AnyRef
      }
    }.asInstanceOf[ClassTag[A]]

    private[this] val BooleanClassBox = classOf[java.lang.Boolean]
    private[this] val ByteClassBox    = classOf[java.lang.Byte]
    private[this] val ShortClassBox   = classOf[java.lang.Short]
    private[this] val IntClassBox     = classOf[java.lang.Integer]
    private[this] val LongClassBox    = classOf[java.lang.Long]
    private[this] val FloatClassBox   = classOf[java.lang.Float]
    private[this] val DoubleClassBox  = classOf[java.lang.Double]
    private[this] val CharClassBox    = classOf[java.lang.Character]
  }
}
