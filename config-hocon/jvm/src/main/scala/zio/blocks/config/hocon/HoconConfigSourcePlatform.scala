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

import scala.util.control.NonFatal

import zio.blocks.config.{ConfigError, ConfigSource}

private[hocon] object HoconConfigSourcePlatform {

  /** Default maximum size for a single HOCON file (root or included), in bytes. */
  val DefaultMaxFileBytes: Long = 1024L * 1024L // 1 MiB

  private def readUtf8Bounded(file: java.io.File, maxFileBytes: Long): String = {
    try {
      val len = try file.length()
      catch { case NonFatal(e) => throw toHoconError(e, "Failed to read file") }
      if (len > maxFileBytes)
        throw HoconError(s"File size exceeds limit ($maxFileBytes bytes)", 0, 0)
      try {
        if (!file.isFile)
          throw HoconError("Not a regular file", 0, 0)
      } catch { case NonFatal(e) => throw toHoconError(e, "Failed to read file") }
      val in = try new java.io.FileInputStream(file)
      catch { case NonFatal(e) => throw toHoconError(e, "Failed to open file") }
      try {
        val out = new java.io.ByteArrayOutputStream()
        val buf = new Array[Byte](8192)
        var total: Long = 0L
        var n           = try in.read(buf)
        catch { case NonFatal(e) => throw toHoconError(e, "Failed to read file") }
        while (n != -1) {
          total += n
          if (total > maxFileBytes)
            throw HoconError(s"File size exceeds limit ($maxFileBytes bytes)", 0, 0)
          out.write(buf, 0, n)
          n = try in.read(buf)
          catch { case NonFatal(e) => throw toHoconError(e, "Failed to read file") }
        }
        new String(out.toByteArray, java.nio.charset.StandardCharsets.UTF_8)
      } finally
        try in.close()
        catch { case NonFatal(_) => () }
    } catch {
      case e: HoconError => throw e
      case NonFatal(e)   => throw toHoconError(e, "Failed to read file")
    }
  }

  private def toHoconError(cause: Throwable, message: String): HoconError = {
    val e = HoconError(message, 0, 0)
    e.initCause(cause)
    e
  }

  private def toParseError(path: String, sourceId: String, cause: Throwable): ConfigError =
    cause match {
      case h: HoconError => ConfigError.ParseError(path, sourceId, "valid HOCON", Some(h))
      case _             => ConfigError.ParseError(path, sourceId, "valid HOCON", Some(cause))
    }

  private def isWithinBase(file: java.io.File, base: java.io.File): Boolean =
    file.toPath.startsWith(base.toPath)

  /**
   * Load a HOCON file from the filesystem, resolving `include` directives.
   *
   * @param maxFileBytes
   *   Maximum allowed size in bytes for the root file and for each included file. Files larger than this are
   *   rejected with a typed [[zio.blocks.config.ConfigError.ParseError]] and are never fully loaded into memory.
   *   Defaults to 1 MiB ([[DefaultMaxFileBytes]]).
   */
  def fromFile(
    path: String,
    allowedBase: Option[java.io.File] = None,
    maxIncludeDepth: Int = 10,
    maxFileBytes: Long = DefaultMaxFileBytes
  ): Either[ConfigError, ConfigSource] = {
    if (maxFileBytes <= 0)
      Left(
        ConfigError.ParseError(path, "hocon:file", s"positive maxFileBytes (got $maxFileBytes)", None)
      )
    else if (maxIncludeDepth < 0)
      Left(
        ConfigError.ParseError(path, "hocon:file", s"non-negative maxIncludeDepth (got $maxIncludeDepth)", None)
      )
    else
      try {
        val canonical        = new java.io.File(path).getCanonicalFile
        val allowedBaseCanon = allowedBase.map(_.getCanonicalFile)

        def includeResolver(baseDir: java.io.File, includeDepth: Int): String => Option[HoconParser.IncludedResource] = {
          resource =>
            try {
              val nextDepth = includeDepth + 1
              if (nextDepth > maxIncludeDepth)
                throw HoconError(s"Include depth exceeded ($maxIncludeDepth)", 0, 0)

              val includeFile =
                try new java.io.File(baseDir, resource).getCanonicalFile
                catch { case NonFatal(e) => throw toHoconError(e, "Failed to resolve include") }

              try {
                allowedBaseCanon.foreach { base =>
                  if (!isWithinBase(includeFile, base))
                    throw HoconError(s"Include path traversal: $resource resolves outside ${base.getPath}", 0, 0)
                }
              } catch {
                case e: HoconError => throw e
                case NonFatal(e)   => throw toHoconError(e, "Failed to resolve include")
              }

              val exists =
                try includeFile.exists()
                catch { case NonFatal(e) => throw toHoconError(e, "Failed to read include") }

              if (exists) {
                try {
                  if (!includeFile.isFile)
                    throw HoconError("Not a regular file", 0, 0)
                } catch {
                  case e: HoconError => throw e
                  case NonFatal(e)   => throw toHoconError(e, "Failed to read include")
                }
                Some(
                  HoconParser.IncludedResource(
                    readUtf8Bounded(includeFile, maxFileBytes),
                    includeResolver(includeFile.getParentFile, nextDepth)
                  )
                )
              } else None
            } catch {
              case e: HoconError => throw e
              case NonFatal(e)   => throw toHoconError(e, "Failed to resolve include")
            }
        }

        val traversalError: Option[Either[ConfigError, ConfigSource]] =
          allowedBaseCanon.flatMap { base =>
            if (!isWithinBase(canonical, base))
              Some(Left(ConfigError.ParseError(path, "hocon:file", s"path inside ${base.getPath}", None)))
            else None
          }

        traversalError.getOrElse {
          if (!canonical.exists())
            Left(ConfigError.ParseError(path, "hocon:file", "existing file", None))
          else if (!canonical.isFile)
            Left(
              ConfigError.ParseError(
                canonical.getPath,
                s"hocon:${canonical.getName}",
                "regular file",
                Some(HoconError("Not a regular file", 0, 0))
              )
            )
          else
            try
              HoconConfigSource.fromStringWithResolver(
                readUtf8Bounded(canonical, maxFileBytes),
                s"hocon:${canonical.getName}",
                includeResolver(canonical.getParentFile, 0)
              )
            catch {
              case e: HoconError => Left(toParseError(canonical.getPath, s"hocon:${canonical.getName}", e))
              case NonFatal(e)   => Left(toParseError(canonical.getPath, s"hocon:${canonical.getName}", e))
            }
        }
      } catch {
        case e: HoconError => Left(toParseError(path, "hocon:file", e))
        case NonFatal(e)   => Left(toParseError(path, "hocon:file", e))
      }
  }
}
