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

package zio.blocks.docs.frontmatter.yaml

import zio.blocks.chunk.Chunk
import zio.blocks.docs.{ParseError, Parser as DocParser}
import zio.blocks.schema.yaml.{Yaml, YamlReader}

import scala.annotation.tailrec

object Parser {
  def parse(input: String): Either[ParseError, DocWithYamlFrontmatter] = {

    val frontmatterEnd: Int = if (!input.startsWith("---\n")) -1 else input.indexOf("\n---\n", 4)
    if (frontmatterEnd == -1) {
      for {
        doc <- DocParser.parse(input)
      } yield DocWithYamlFrontmatter(frontmatter = None, doc = doc)
    } else {
      val frontmatterInput: String = input.substring(4, frontmatterEnd)
      val frontmatterLines: Int = frontmatterInput.count(_ == '\n')+3
      val docInput: String = input.substring(frontmatterEnd+5)

      def frontMatterError[A](message: String): Left[ParseError, A] = Left(ParseError(
        message = message,
        line = 1,
        column = 1,
        input = ""
      ))

      for {
        yaml: Yaml <- YamlReader.read(frontmatterInput).left.map(yamlError =>
          ParseError(
            message = "Error parsing frontmatter: " + yamlError.getMessage,
            line = yamlError.line.getOrElse(1),
            column = yamlError.column.getOrElse(1),
            input = frontmatterInput
          )
        )
        mapping: List[(Yaml, Yaml)] <- yaml match {
          case Yaml.Mapping(entries: Chunk[(Yaml, Yaml)]) => Right(entries.toList)
          case _ => frontMatterError("Frontmatter must be a mapping")
        }
        stringMapping: Seq[(String, Yaml)] <- sequence(mapping)((keyYaml, value) => keyYaml match {
          case Yaml.Scalar(key, _) => Right((key, value))
          case _ => frontMatterError(s"Frontmatter key must be a string: $keyYaml")
        })
        doc <- DocParser.parse(docInput).left.map(parseError => parseError.copy(line = parseError.line+frontmatterLines))
      } yield DocWithYamlFrontmatter(frontmatter = Some(stringMapping.toMap), doc = doc)
    }
  }

  private def sequence[A, E, B](as: List[A])(f: A => Either[E, B]): Either[E, List[B]] = {
    @tailrec
    def loop(as: List[A], result: List[B]): Either[E, List[B]] = as match {
      case Nil => Right(result)
      case a :: as => f(a) match {
        case Right(b) => loop(as, result :+ b)
        case Left(e) => Left(e)
      }
    }

    loop(as, List.empty)
  }
}
