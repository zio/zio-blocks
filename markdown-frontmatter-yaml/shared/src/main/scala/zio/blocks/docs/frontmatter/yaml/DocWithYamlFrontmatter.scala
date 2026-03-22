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

import zio.blocks.docs.Doc
import zio.blocks.schema.yaml.Yaml

final case class DocWithYamlFrontmatter(frontmatter: Option[Map[String, Yaml]], doc: Doc)
    extends Product
    with Serializable {

  override def toString: String =
    Renderer.render(this)

  def frontmatterKey(key: String): Option[Yaml] = frontmatter.flatMap(_.get(key))
}
