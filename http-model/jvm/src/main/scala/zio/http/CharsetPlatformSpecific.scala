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

private[http] trait CharsetPlatformSpecific { self: Charset =>
  def toJava: java.nio.charset.Charset = self match {
    case Charset.UTF8       => java.nio.charset.StandardCharsets.UTF_8
    case Charset.ASCII      => java.nio.charset.StandardCharsets.US_ASCII
    case Charset.ISO_8859_1 => java.nio.charset.StandardCharsets.ISO_8859_1
    case Charset.UTF16      => java.nio.charset.StandardCharsets.UTF_16
    case Charset.UTF16BE    => java.nio.charset.StandardCharsets.UTF_16BE
    case Charset.UTF16LE    => java.nio.charset.StandardCharsets.UTF_16LE
  }
}
