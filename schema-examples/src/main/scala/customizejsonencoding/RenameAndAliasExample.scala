package customizejsonencoding

import zio.blocks.schema._

/**
 * Customize JSON: Rename One Field and Accept Its Old Name
 *
 * Renames one field with Modifier.rename, accepts an old name with
 * Modifier.alias, and shows the Scala name is no longer read.
 *
 * Run with: cd schema-examples && sbt "runMain
 * customizejsonencoding.RenameAndAliasExample"
 */
object RenameAndAliasExample extends App {

  case class Book(
    title: String,
    @Modifier.rename("isbn")
    @Modifier.alias("isbn_13")
    isbn13: Option[String]
  )
  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  val pis = Book("Programming in Scala", Some("9780981531601"))

  // The encoder writes the renamed key
  println(pis.toJsonString)

  // The decoder accepts both the new name and the alias
  println("""{"title":"Programming in Scala","isbn":"9780981531601"}""".fromJson[Book])
  println("""{"title":"Programming in Scala","isbn_13":"9780981531601"}""".fromJson[Book])

  // The original Scala name is no longer accepted, so the value is lost
  println("""{"title":"Programming in Scala","isbn13":"9780981531601"}""".fromJson[Book])
}
