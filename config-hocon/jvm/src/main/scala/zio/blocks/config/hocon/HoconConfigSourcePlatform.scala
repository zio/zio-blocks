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

package zio.blocks.config.hocon

import zio.blocks.config.ConfigSource

object HoconConfigSourcePlatform {

  def fromFile(path: String): Either[HoconError, ConfigSource] = {
    val file = new java.io.File(path)
    if (!file.exists())
      Left(HoconError(s"File not found: $path", 0, 0))
    else {
      val source  = scala.io.Source.fromFile(file, "UTF-8")
      val content =
        try source.mkString
        finally source.close()
      HoconConfigSource.fromStringWithId(content, s"hocon:${file.getName}")
    }
  }
}
