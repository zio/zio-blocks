/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.markdown

/**
 * Typeclass for types that can be converted to Markdown inline content.
 *
 * This is used by the `md` string interpolator to embed values into Markdown.
 */
trait ToMarkdown[-A] {

  /**
   * Converts a value to Markdown inline content.
   */
  def toMarkdown(a: A): Inline
}

object ToMarkdown {

  def apply[A](implicit ev: ToMarkdown[A]): ToMarkdown[A] = ev

  /**
   * Creates a ToMarkdown instance from a function.
   */
  def instance[A](f: A => Inline): ToMarkdown[A] = (a: A) => f(a)

  // Built-in instances

  implicit val stringToMarkdown: ToMarkdown[String] =
    instance(s => Inline.Text(s))

  implicit val intToMarkdown: ToMarkdown[Int] =
    instance(i => Inline.Text(i.toString))

  implicit val longToMarkdown: ToMarkdown[Long] =
    instance(l => Inline.Text(l.toString))

  implicit val doubleToMarkdown: ToMarkdown[Double] =
    instance(d => Inline.Text(d.toString))

  implicit val floatToMarkdown: ToMarkdown[Float] =
    instance(f => Inline.Text(f.toString))

  implicit val booleanToMarkdown: ToMarkdown[Boolean] =
    instance(b => Inline.Text(b.toString))

  implicit val inlineToMarkdown: ToMarkdown[Inline] =
    instance(identity)

  implicit val charToMarkdown: ToMarkdown[Char] =
    instance(c => Inline.Text(c.toString))

  implicit val bigIntToMarkdown: ToMarkdown[BigInt] =
    instance(bi => Inline.Text(bi.toString))

  implicit val bigDecimalToMarkdown: ToMarkdown[BigDecimal] =
    instance(bd => Inline.Text(bd.toString))
}
