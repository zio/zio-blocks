package deriveyourowntypeclass

import zio.blocks.schema._

/**
 * How to Derive Your Own Type Class from a Schema — Packaging the Type Class
 * for Reuse
 *
 * Derives Redact for a recursive Category with a derives Schema, Redact clause.
 *
 * Run with: cd schema-examples && sbt "runMain
 * deriveyourowntypeclass.Step4PackagingExample"
 */
final case class Category(name: String, subcategories: List[Category]) derives Schema, Redact

object Step4PackagingExample {
  def main(args: Array[String]): Unit = {
    val programming = Category("Programming", List(Category("Scala", List(Category("Functional Programming", Nil)))))

    println(summon[Redact[Category]].redact(programming))
  }
}
