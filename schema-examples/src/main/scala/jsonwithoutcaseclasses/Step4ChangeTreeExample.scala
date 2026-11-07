package jsonwithoutcaseclasses

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Read and Modify JSON Without Case Classes — Change Values in the Tree
 *
 * Raises every price by 10%, adds a tag, sets and inserts fields, removes
 * `stock` from every book, and shows how setOrFail catches a misspelled path.
 *
 * Run with: cd schema-examples && sbt "runMain
 * jsonwithoutcaseclasses.Step4ChangeTreeExample"
 */
object Step4ChangeTreeExample {
  def main(args: Array[String]): Unit = {
    val bookstore: Json = Bookstore.bookstore

    val raised: Json = bookstore.modify(p".books[*].price") {
      case Json.Number(price) =>
        Json.Number((price * BigDecimal("1.1")).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      case other => other
    }

    println(raised.get(p".books[*].price").asAll[BigDecimal])

    val tagged: Json = raised.modify(p".books[0].tags") {
      case Json.Array(tags) => Json.Array(tags :+ Json.String("bestseller"))
      case other            => other
    }

    val withAvailability: Json = tagged
      .set(p".store", Json.String("Lambda Books Online"))
      .modify(p".books[*]") { book =>
        book.insert(p".available", Json.Boolean(book.get("stock").as[Int].exists(_ > 0)))
      }

    val published: Json = withAvailability.modify(p".books[*]")(_.delete(p".stock"))

    println(published.get(p".books[*]").toChunk.map(_.print).mkString("\n"))

    println(published.set(p".curency", Json.String("EUR")) == published)

    println(published.setOrFail(p".curency", Json.String("EUR")))
  }
}
