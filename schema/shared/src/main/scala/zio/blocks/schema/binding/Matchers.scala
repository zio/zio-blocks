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

final case class Matchers[+A](matchers: IndexedSeq[Matcher[? <: A]]) {
  def apply(index: Int): Matcher[? <: A] = matchers(index)
}

object Matchers {
  def apply[A](matchers: Matcher[? <: A]*): Matchers[A] = new Matchers(matchers.toIndexedSeq)
}
