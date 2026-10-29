package protectpageswithbasicauthorasessioncookie

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Checking a
 * Password
 *
 * Checks a right password, a wrong password and an unknown user against the
 * staff accounts.
 */
// Run with: cd http-model-examples && sbt "runMain protectpageswithbasicauthorasessioncookie.Step4CheckPasswordExample"
object Step4CheckPasswordExample {
  def main(args: Array[String]): Unit = {
    println(Staff.authenticate("ada", "correct horse battery staple"))
    println(Staff.authenticate("ada", "Correct horse battery staple"))
    println(Staff.authenticate("mallory", "correct horse battery staple"))
  }
}
