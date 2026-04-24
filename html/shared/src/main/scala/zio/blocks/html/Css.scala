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

import zio.blocks.chunk.Chunk

/**
 * A sealed ADT representing CSS content.
 *
 * Variants:
 *   - [[Css.Rule]] — a single CSS rule: `selector { property: value; ... }`
 *   - [[Css.Sheet]] — multiple rules
 *   - [[Css.Raw]] — raw CSS string (for media queries, keyframes, etc.)
 *   - [[Css.Comment]] — CSS comment: `/* text */`
 *
 * Rendering:
 *   - `render` produces minified CSS
 *   - `render(indent)` produces pretty-printed CSS with the specified
 *     indentation
 */

sealed trait Css extends Product with Serializable {
  def render: String
  def render(indent: Int): String
}

object Css {

  final case class Rule(selector: CssSelector, declarations: Chunk[Declaration]) extends Css {
    def render: String = {
      val sb = new java.lang.StringBuilder
      sb.append(selector.render)
      sb.append('{')
      var i = 0
      while (i < declarations.length) {
        val d = declarations(i)
        sb.append(d.property)
        sb.append(':')
        sb.append(d.value)
        sb.append(';')
        i += 1
      }
      sb.append('}')
      sb.toString
    }

    def render(indent: Int): String = {
      if (indent <= 0) return render
      val sb        = new java.lang.StringBuilder
      val indentStr = " " * indent
      sb.append(selector.render)
      sb.append(" {\n")
      var i = 0
      while (i < declarations.length) {
        val d = declarations(i)
        sb.append(indentStr)
        sb.append(d.property)
        sb.append(": ")
        sb.append(d.value)
        sb.append(";\n")
        i += 1
      }
      sb.append('}')
      sb.toString
    }
  }

  final case class Declaration private (property: String, value: String)

  object Declaration {
    def apply[A](property: String, value: A)(implicit ev: ToCss[A]): Declaration =
      new Declaration(property, ev.toCss(value))
  }

  final case class Sheet(rules: Chunk[Css]) extends Css {
    def render: String = {
      val sb = new java.lang.StringBuilder
      var i  = 0
      while (i < rules.length) {
        sb.append(rules(i).render)
        i += 1
      }
      sb.toString
    }

    def render(indent: Int): String = {
      if (indent <= 0) return render
      val sb = new java.lang.StringBuilder
      var i  = 0
      while (i < rules.length) {
        if (i > 0) sb.append('\n')
        sb.append(rules(i).render(indent))
        i += 1
      }
      sb.toString
    }
  }

  final case class Raw(value: String) extends Css {
    def render: String              = value
    def render(indent: Int): String = value
    def stripMargin: Raw            = Raw(value.stripMargin)
  }

  final case class Comment(text: String) extends Css {
    def render: String              = "/*" + text.replace("*/", "* /") + "*/"
    def render(indent: Int): String = "/* " + text.replace("*/", "* /") + " */"
  }

  def apply(value: String): Css = Raw(value)
}
