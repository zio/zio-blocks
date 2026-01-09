package zio.blocks.schema.migration

import zio.blocks.schema.migration.optic._

object MacroVerificationSpec extends App {

  println("--- Starting Macro Verification ---")

  // --- Test 1: Happy Path (Standard Class) ---
  case class User(name: String, address: Address)
  case class Address(city: String)

  // ইউজার ইনপুট: _.address.city
  val optic1 = SelectorMacro.translate[User, String](_.address.city)
  
  println(s"Test 1 (Standard Field): ${optic1.render}") 
  // Expected Output: .address.city
  if (optic1.render != ".address.city") throw new RuntimeException("Test 1 Failed!")


  // --- Test 2: Happy Path (Structural Type - The Main Requirement) ---
  // এটা সেই 'selectDynamic' লজিক চেক করবে
  type UserV1 = { def age: Int; def name: String }
  
  // স্ট্রাকচারাল টাইপের জন্য 'Selectable' দরকার হয় Scala 3 তে
  import scala.reflect.Selectable.reflectiveSelectable

  val optic2 = SelectorMacro.translate[UserV1, Int](_.age)
  
  println(s"Test 2 (Structural Type): ${optic2.render}")
  // Expected Output: .age
  if (optic2.render != ".age") throw new RuntimeException("Test 2 Failed! Structural type not recognized.")


  // --- Test 3: Negative Path (Invalid Logic) ---
  // এই অংশটি আন-কমেন্ট করলে কোড কম্পাইল হওয়া উচিত না। 
  // এটাই প্রমাণ যে আমাদের "Red Line" চেক কাজ করছে।
  
  /*
  val failTest = SelectorMacro.translate[User, Int](_.name.length) 
  // Error হওয়া উচিত: "Invalid selector syntax..."
  */

  println("--- Verification Successful! Macro Engine is Solid. ---")
}