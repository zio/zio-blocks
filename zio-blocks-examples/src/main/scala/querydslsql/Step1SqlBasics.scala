package querydslsql

/**
 * Query DSL SQL Generation — Part 2, Step 1: SQL Basics
 *
 * Demonstrates extracting column names from optic paths using DynamicOptic,
 * formatting SQL literals, building the core SQL interpreter via pattern
 * matching on SchemaExpr, and generating basic SQL WHERE clauses.
 *
 * Column name extraction, SQL literal formatting, and the core SQL interpreter
 * are defined in the package object (package.scala). The Product domain type
 * is defined in Common.scala.
 *
 * Run with: sbt "examples/runMain querydslsql.Step1SqlBasics"
 */
object Step1SqlBasics extends App {

  // --- Column Name Extraction ---

  println("=== Column Name Extraction ===")
  println()
  println(s"Product.price    -> ${columnName(Product.price)}")
  println(s"Product.name     -> ${columnName(Product.name)}")
  println(s"Product.category -> ${columnName(Product.category)}")
  println()

  // --- Basic SQL Generation ---

  val isElectronics  = Product.category === "Electronics"
  val expensiveItems = Product.price > 100.0
  val highRated      = Product.rating >= 4

  println("=== Basic SQL Generation ===")
  println()
  println(s"isElectronics  -> ${toSql(isElectronics)}")
  println(s"expensiveItems -> ${toSql(expensiveItems)}")
  println(s"highRated      -> ${toSql(highRated)}")
}
