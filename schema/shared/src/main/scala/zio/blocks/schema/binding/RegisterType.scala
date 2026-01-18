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

package zio.blocks.schema.binding

sealed trait RegisterType[A]

object RegisterType {
  case object Unit                 extends RegisterType[Unit]
  case object Char                 extends RegisterType[Char]
  case object Byte                 extends RegisterType[Byte]
  case object Short                extends RegisterType[Short]
  case object Int                  extends RegisterType[Int]
  case object Long                 extends RegisterType[Long]
  case object Float                extends RegisterType[Float]
  case object Double               extends RegisterType[Double]
  case object Boolean              extends RegisterType[Boolean]
  sealed trait Object[A <: AnyRef] extends RegisterType[A]

  def Object[A <: AnyRef](): Object[A] = _object.asInstanceOf[Object[A]]

  private[this] val _object = new Object[AnyRef] {
    override def toString: String = "Object"
  }
}
