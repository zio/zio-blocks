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

package zio.blocks.streams.verification

sealed trait ValueType extends Product with Serializable { def id: String }
object ValueType {
  case object Ref     extends ValueType { val id = "ref"     }
  case object Boolean extends ValueType { val id = "boolean" }
  case object Byte    extends ValueType { val id = "byte"    }
  case object Char    extends ValueType { val id = "char"    }
  case object Short   extends ValueType { val id = "short"   }
  case object Int     extends ValueType { val id = "int"     }
  case object Long    extends ValueType { val id = "long"    }
  case object Float   extends ValueType { val id = "float"   }
  case object Double  extends ValueType { val id = "double"  }

  val all: Vector[ValueType]      = Vector(Ref, Boolean, Byte, Char, Short, Int, Long, Float, Double)
  val physical: Vector[ValueType] = Vector(Ref, Int, Long, Float, Double)
}
