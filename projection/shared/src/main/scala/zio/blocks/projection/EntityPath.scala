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

package zio.blocks.projection

import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.typeid.TypeId
import scala.annotation.StaticAnnotation

class path(name: String) extends StaticAnnotation {
  def pathName: String = name
}

/**
 * Describes where a projection entity `A` lives on disk and which field is its
 * identifier.
 *
 * `basePath` is the folder name (e.g. `users` derived from `userId`),
 * `entityIdField` is the field name that holds the entity id.
 */
trait EntityPath[A] {

  /** Folder name for the projection's SQLite file(s). */
  def basePath: String

  /** Field name that holds the entity identifier. */
  def entityIdField: String
}

object EntityPath {

  /** Manual construction with an explicit folder and id field. */
  def apply[A](basePath0: String, entityIdField0: String): EntityPath[A] =
    new EntityPath[A] {
      val basePath: String      = basePath0
      val entityIdField: String = entityIdField0
    }

  /**
   * Derive an [[EntityPath]] from `Schema[A]`.
   *
   * Finds the `@Modifier.id` field (or `id` by name), then derives the folder
   * as `pluralize(toSnakeCase(stripIdSuffix(fieldName)))` — e.g. `userId` →
   * `users`, `id` → `ids` (prefer `userId` or `@path` to avoid the generic
   * `ids` folder).
   */
  def derived[A](using schema: Schema[A]): EntityPath[A] = {
    val reflect = schema.reflect
    reflect.asRecord match {
      case Some(record) =>
        val fields              = record.fields
        val idFieldByAnnotation = fields.find(_.modifiers.exists(_.isInstanceOf[Modifier.id]))
        val idFieldByName       = idFieldByAnnotation.orElse(fields.find(_.name == "id"))
        idFieldByName match {
          case Some(field) =>
            val fieldName      = field.name
            val overriddenPath = findPathAnnotation(reflect.typeId)
            val folderName     = overriddenPath.getOrElse(deriveFolderName(fieldName))
            new EntityPath[A] {
              val basePath: String      = folderName
              val entityIdField: String = fieldName
            }
          case None =>
            throw new RuntimeException(
              s"Entity ${reflect.typeId.fullName} must have @Modifier.id field or field named 'id'. " +
                s"Annotate a field with @Modifier.id or name it 'id'."
            )
        }
      case None =>
        throw new RuntimeException(
          s"EntityPath requires a record type (case class), but ${reflect.typeId.fullName} is not a record."
        )
    }
  }

  def derived[A](pathOverride: String)(using schema: Schema[A]): EntityPath[A] = {
    val reflect = schema.reflect
    reflect.asRecord match {
      case Some(record) =>
        val fields              = record.fields
        val idFieldByAnnotation = fields.find(_.modifiers.exists(_.isInstanceOf[Modifier.id]))
        val idFieldByName       = idFieldByAnnotation.orElse(fields.find(_.name == "id"))
        idFieldByName match {
          case Some(field) =>
            new EntityPath[A] {
              val basePath: String      = pathOverride
              val entityIdField: String = field.name
            }
          case None =>
            throw new RuntimeException(
              s"Entity ${reflect.typeId.fullName} must have @Modifier.id field or field named 'id'. " +
                s"Annotate a field with @Modifier.id or name it 'id'."
            )
        }
      case None =>
        throw new RuntimeException(
          s"EntityPath requires a record type, but ${reflect.typeId.fullName} is not a record."
        )
    }
  }

  private[projection] def deriveFolderName(fieldName: String): String = {
    val stripped   = stripIdSuffix(fieldName)
    val snakeCased = toSnakeCase(stripped)
    pluralize(snakeCased)
  }

  private[projection] def stripIdSuffix(fieldName: String): String = {
    val lower = fieldName.toLowerCase
    if (lower.endsWith("id") && fieldName.length > 2) {
      val stripped = fieldName.dropRight(2)
      if (stripped.endsWith("_")) stripped.dropRight(1)
      else stripped
    } else fieldName
  }

  private[projection] def toSnakeCase(s: String): String = {
    val len = s.length
    if (len == 0) return s
    val sb                       = new StringBuilder(len << 1)
    var i                        = 0
    var isPrecedingNotUpperCased = false
    while (i < len) {
      isPrecedingNotUpperCased = {
        val ch = s.charAt(i)
        i += 1
        if (ch == '_' || ch == '-') {
          sb.append('_')
          false
        } else if (!ch.isUpper) {
          sb.append(ch.toLower)
          true
        } else {
          if (isPrecedingNotUpperCased || (i > 1 && i < len && !s.charAt(i).isUpper))
            sb.append('_')
          sb.append(ch.toLower)
          false
        }
      }
    }
    sb.toString
  }

  private[projection] def pluralize(s: String): String =
    if (s.isEmpty) s
    else if (s.endsWith("s") || s.endsWith("x") || s.endsWith("ch") || s.endsWith("sh") || s.endsWith("zz"))
      s + "es"
    else if (s.endsWith("iz")) s.dropRight(1) + "zzes"
    else if (s.endsWith("z")) s + "es"
    else if (s.endsWith("y") && s.length > 1 && !isVowel(s.charAt(s.length - 2)))
      s.dropRight(1) + "ies"
    else s + "s"

  private def isVowel(c: Char): Boolean = "aeiouAEIOU".indexOf(c) >= 0

  private[projection] def findPathAnnotation(typeId: TypeId[?]): Option[String] =
    typeId.annotations.collectFirst {
      case ann if ann.name == "path" =>
        ann.args.collectFirst { case zio.blocks.typeid.AnnotationArg.Const(value: String) => value }
    }.flatten

  // M3: validate basePath does not contain traversal
  private[projection] def validateBasePath(path: String): Unit = {
    require(path.nonEmpty, s"invalid basePath $path")
    require(!path.contains("/") || !path.contains(".."), s"invalid basePath $path")
    require(!path.contains("\\"), s"invalid basePath $path")
    require(!path.contains(".."), s"invalid basePath $path")
  }
}
