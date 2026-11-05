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

package zio.http

/**
 * The boundary string that separates the parts of a multipart body. It is the
 * `boundary` parameter of a [[ContentType]]. `toString` returns `value`.
 */
final case class Boundary(value: String) {
  override def toString: String = value
}

/** Constructors for [[Boundary]]. */
object Boundary {

  /**
   * Returns a new boundary of 24 random letters and digits (`A-Z`, `a-z`,
   * `0-9`), drawn with `scala.util.Random`. Each call gives a different value
   * in practice.
   */
  def generate: Boundary = {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val sb    = new StringBuilder(24)
    var i     = 0
    while (i < 24) {
      sb.append(chars.charAt(scala.util.Random.nextInt(chars.length)))
      i += 1
    }
    Boundary(sb.toString)
  }
}
