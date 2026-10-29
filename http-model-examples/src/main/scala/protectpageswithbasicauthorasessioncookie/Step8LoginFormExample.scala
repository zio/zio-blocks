package protectpageswithbasicauthorasessioncookie

import zio.http._
import protectpageswithbasicauthorasessioncookie.Login._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Logging In With a
 * Form
 *
 * Serves `logIn` for every path and posts a wrong password, then the right one.
 */
// Run with: cd http-model-examples && sbt "runMain protectpageswithbasicauthorasessioncookie.Step8LoginFormExample"
object Step8LoginFormExample {
  def main(args: Array[String]): Unit =
    JdkHttp.withServer(logIn) { port =>
      Client.call(port, "POST", "/login", Form("username" -> "ada", "password" -> "espresso"))
      Client.call(port, "POST", "/login", Form("username" -> "ada", "password" -> "correct horse battery staple"))
    }
}
