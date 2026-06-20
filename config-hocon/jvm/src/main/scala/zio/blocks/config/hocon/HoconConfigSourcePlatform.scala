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

private[hocon] object HoconConfigSourcePlatform {

  private def readUtf8(file: java.io.File): String = {
    val source = scala.io.Source.fromFile(file, "UTF-8")
    try source.mkString
    finally source.close()
  }

  private def isWithinBase(file: java.io.File, base: java.io.File): Boolean =
    file.toPath.startsWith(base.toPath)

  def fromFile(
    path: String,
    allowedBase: Option[java.io.File] = None,
    maxIncludeDepth: Int = 10
  ): Either[ConfigError, ConfigSource] = {
    val canonical        = new java.io.File(path).getCanonicalFile
    val allowedBaseCanon = allowedBase.map(_.getCanonicalFile)

    def includeResolver(baseDir: java.io.File, includeDepth: Int): String => Option[HoconParser.IncludedResource] = {
      resource =>
        val nextDepth = includeDepth + 1
        if (nextDepth > maxIncludeDepth)
          throw HoconError(s"Include depth exceeded ($maxIncludeDepth)", 0, 0)

        val includeFile = new java.io.File(baseDir, resource).getCanonicalFile

        allowedBaseCanon.foreach { base =>
          if (!isWithinBase(includeFile, base))
            throw HoconError(s"Include path traversal: $resource resolves outside ${base.getPath}", 0, 0)
        }

        if (includeFile.exists())
          Some(
            HoconParser.IncludedResource(readUtf8(includeFile), includeResolver(includeFile.getParentFile, nextDepth))
          )
        else None
    }

    val traversalError = allowedBaseCanon.flatMap { base =>
      if (!isWithinBase(canonical, base))
        Some(Left(ConfigError.ParseError(path, "hocon:file", s"path inside ${base.getPath}", None)))
      else None
    }

    traversalError.getOrElse {
      if (!canonical.exists())
        Left(ConfigError.ParseError(path, "hocon:file", "existing file", None))
      else
        HoconConfigSource.fromStringWithResolver(
          readUtf8(canonical),
          s"hocon:${canonical.getName}",
          includeResolver(canonical.getParentFile, 0)
        )
    }
  }
}
