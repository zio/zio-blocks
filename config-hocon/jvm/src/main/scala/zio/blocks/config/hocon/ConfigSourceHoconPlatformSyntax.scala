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

/**
 * Adds `ConfigSource.fromFile` and `ConfigSource.fromFileWithLimits`, which
 * read HOCON files, on the JVM. On Scala.js it adds nothing.
 */
trait ConfigSourceHoconPlatformSyntax {

  /** The JVM-only `ConfigSource.fromFile` constructors. */
  implicit final class ConfigSourceHoconPlatformOps(private val companion: ConfigSource.type) {

    /**
     * Reads the HOCON file at `path` and returns its values like
     * `ConfigSource.fromHocon`, as a source named `hocon:<file name>`. Each
     * `include "file"` is read relative to the including file's folder, up to
     * `maxIncludeDepth` levels deep; each file may be at most 1 MiB.
     *
     * With `allowedBase`, the file and every included file must lie inside that
     * folder. An include whose file does not exist is skipped without an error,
     * and an absolute include path is looked up inside the including folder, so
     * it is usually skipped too, a known bug.
     *
     * When the file at `path` is missing, outside `allowedBase` or too big, the
     * result is a [[zio.blocks.config.ConfigError.ParseError]] whose path is
     * the file's path. Problems found while parsing (a syntax error, an
     * included file that is too big or outside `allowedBase`, an include nested
     * too deep) give a `ParseError` with an empty path.
     *
     * {{{
     * import java.nio.file.Files
     * import zio.blocks.config._
     * import zio.blocks.config.hocon._
     *
     * val dir  = Files.createTempDirectory("app")
     * Files.write(dir.resolve("db.conf"), "host = db.internal\nport = 5432".getBytes("UTF-8"))
     * val file = Files.write(dir.resolve("application.conf"), "http.port = 8080\ndb { include \"db.conf\" }".getBytes("UTF-8"))
     *
     * val source = ConfigSource.fromFile(file.toString)
     * val keys   = source.map(_.all("").keySet.toList.sorted)
     * // Right(List(db.host, db.port, http.port))
     * }}}
     *
     * @see
     *   [[https://zioblocks.com/reference/config/formats/ File Formats]]
     */
    def fromFile(
      path: String,
      allowedBase: Option[java.io.File] = None,
      maxIncludeDepth: Int = 10
    ): Either[ConfigError, ConfigSource] =
      HoconConfigSourcePlatform.fromFile(path, allowedBase, maxIncludeDepth)

    /**
     * Same as `fromFileWithLimits` with every argument given; kept for binary
     * compatibility.
     */
    def fromFile(
      path: String,
      allowedBase: Option[java.io.File],
      maxIncludeDepth: Int,
      maxFileBytes: Long
    ): Either[ConfigError, ConfigSource] =
      HoconConfigSourcePlatform.fromFile(path, allowedBase, maxIncludeDepth, maxFileBytes)

    /**
     * Reads a HOCON file like `fromFile`, with `maxFileBytes` as the size limit
     * for the file and for each included file. A `maxFileBytes` of 0 or less,
     * or a negative `maxIncludeDepth`, is a
     * [[zio.blocks.config.ConfigError.ParseError]].
     */
    def fromFileWithLimits(
      path: String,
      allowedBase: Option[java.io.File] = None,
      maxIncludeDepth: Int = 10,
      maxFileBytes: Long = HoconConfigSourcePlatform.DefaultMaxFileBytes
    ): Either[ConfigError, ConfigSource] =
      HoconConfigSourcePlatform.fromFileWithLimits(path, allowedBase, maxIncludeDepth, maxFileBytes)
  }
}
