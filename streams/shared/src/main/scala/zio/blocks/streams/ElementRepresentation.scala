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

package zio.blocks.streams

private[streams] sealed trait ElementRepresentation {
  final def stableJvmType: Option[JvmType] = this match {
    case ElementRepresentation.Known(jvmType) => Some(jvmType)
    case ElementRepresentation.Boxed          => Some(JvmType.AnyRef)
    case ElementRepresentation.LateBound      => None
  }
}

private[streams] object ElementRepresentation {
  case object Boxed extends ElementRepresentation

  final case class Known(jvmType: JvmType) extends ElementRepresentation

  case object LateBound extends ElementRepresentation

  def fromJvmType(jvmType: JvmType): ElementRepresentation =
    if (jvmType eq JvmType.AnyRef) Boxed
    else if (jvmType eq JvmType.Boolean) BooleanRepresentation
    else if (jvmType eq JvmType.Byte) ByteRepresentation
    else if (jvmType eq JvmType.Char) CharRepresentation
    else if (jvmType eq JvmType.Double) DoubleRepresentation
    else if (jvmType eq JvmType.Float) FloatRepresentation
    else if (jvmType eq JvmType.Int) IntRepresentation
    else if (jvmType eq JvmType.Long) LongRepresentation
    else if (jvmType eq JvmType.Short) ShortRepresentation
    else Known(jvmType)

  def resolve(representation: ElementRepresentation, actual: => JvmType): JvmType =
    representation.stableJvmType.getOrElse(actual)

  private val BooleanRepresentation = Known(JvmType.Boolean)
  private val ByteRepresentation    = Known(JvmType.Byte)
  private val CharRepresentation    = Known(JvmType.Char)
  private val DoubleRepresentation  = Known(JvmType.Double)
  private val FloatRepresentation   = Known(JvmType.Float)
  private val IntRepresentation     = Known(JvmType.Int)
  private val LongRepresentation    = Known(JvmType.Long)
  private val ShortRepresentation   = Known(JvmType.Short)
}
