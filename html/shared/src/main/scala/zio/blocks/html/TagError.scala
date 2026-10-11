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

package zio.blocks.html

/**
 * A typed failure describing why a runtime-known tag was rejected by the
 * dynamic generic element factories.
 *
 * Returned by [[zio.blocks.html.HtmlElements.elementDynamic]] and
 * [[zio.blocks.html.HtmlElements.voidElementDynamic]] instead of throwing, so
 * callers handling runtime-known tags deal with the failure explicitly via
 * `Left` / `Right`. Each [[message]] states the violated requirement for the
 * actual tag value and recommends the built-in DSL factory for that tag where
 * one exists (falling back to the matching dynamic alternative for custom tags
 * and tag-generic code) — never a static factory with a runtime string, which
 * cannot compile. (The static [[zio.blocks.html.HtmlElements.element]] and
 * [[zio.blocks.html.HtmlElements.voidElement]] factories report their own
 * compile-time diagnostics with literal replacement calls.)
 */
sealed trait TagError extends Product with Serializable {
  def tag: String
  def message: String
}

object TagError {

  /**
   * The tag is a void element (`br`, `img`, `input`, ...) and therefore cannot
   * carry children: a void element must stay childless.
   */
  final case class VoidTag(tag: String) extends TagError {
    def message: String = {
      val factory = Dom.dslFactoryName(tag).getOrElse(s"""voidElement("$tag")""")
      s"Cannot create <$tag> with elementDynamic: <$tag> is a void HTML tag.\n" +
        s"\nUse the built-in $factory factory instead: ${Dom.voidGuidance(factory)}\n" +
        s"For tag-generic code handling any runtime tag, use voidElementDynamic for this tag and handle Left(error) / Right(element) explicitly."
    }
  }

  /**
   * The tag is not a void element (`div`, `span`, ...) and therefore must carry
   * a closing tag instead of rendering self-closed.
   */
  final case class NonVoidTag(tag: String) extends TagError {
    def message: String = Dom.dslFactoryName(tag) match {
      case Some(factory) =>
        s"Cannot create <$tag> with voidElementDynamic: <$tag> is not a void HTML tag.\n" +
          s"\nUse the built-in $factory factory instead: ${Dom.nonVoidGuidance(tag, factory)}\n" +
          s"For tag-generic code handling any runtime tag, use elementDynamic for this tag and handle Left(error) / Right(element) explicitly."
      case None =>
        s"Cannot create <$tag> with voidElementDynamic: <$tag> is not a void HTML tag.\n" +
          s"\nUse elementDynamic for this tag and handle Left(error) / Right(element) explicitly."
    }
  }
}
