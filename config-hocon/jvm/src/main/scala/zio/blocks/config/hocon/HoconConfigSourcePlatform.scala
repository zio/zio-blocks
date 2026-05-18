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

import zio.blocks.config.{ConfigError, ConfigSource}

object HoconConfigSourcePlatform {

  def fromFile(
    path: String,
    allowedBase: Option[java.io.File] = None,
    maxIncludeDepth: Int = 10
  ): Either[ConfigError, ConfigSource] = {
    val file      = new java.io.File(path)
    val canonical = file.getCanonicalFile

    val traversalError = allowedBase.flatMap { base =>
      val baseCanon = base.getCanonicalFile
      if (!canonical.getPath.startsWith(baseCanon.getPath))
        Some(Left(ConfigError.ParseError(path, "hocon:file", s"path inside ${base.getPath}", None)))
      else None
    }

    traversalError.getOrElse {
      if (!canonical.exists())
        Left(ConfigError.ParseError(path, "hocon:file", "existing file", None))
      else {
        val source  = scala.io.Source.fromFile(canonical, "UTF-8")
        val content =
          try source.mkString
          finally source.close()

        val baseDir = canonical.getParentFile
        var includeDepth = 0
        val callback: String => Option[String] = { resource =>
          includeDepth += 1
          if (includeDepth > maxIncludeDepth)
            throw HoconError(s"Include depth exceeded ($maxIncludeDepth)", 0, 0)
          val incFile = new java.io.File(baseDir, resource).getCanonicalFile
          allowedBase.foreach { base =>
            val baseCanon = base.getCanonicalFile
            if (!incFile.getPath.startsWith(baseCanon.getPath))
              throw HoconError(s"Include path traversal: $resource resolves outside ${base.getPath}", 0, 0)
          }
          if (incFile.exists()) {
            val src = scala.io.Source.fromFile(incFile, "UTF-8")
            try Some(src.mkString)
            finally src.close()
          } else None
        }

        HoconConfigSource.fromStringWithCallback(content, s"hocon:${canonical.getName}", callback)
      }
    }
  }
}
