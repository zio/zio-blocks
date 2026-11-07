package protectpageswithbasicauthorasessioncookie

import protectpageswithbasicauthorasessioncookie.Logout._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Logging Out
 *
 * Signs Grace in, signs her out, and shows that the old cookie no longer opens
 * the staff page.
 */
// Run with: cd http-model-examples && sbt "runMain protectpageswithbasicauthorasessioncookie.Step10LogoutExample"
object Step10LogoutExample {
  def main(args: Array[String]): Unit =
    JdkHttp.withServer(staffApp) { port =>
      val cookie = Client.logIn(port, "grace", "tea and biscuits at four")
      Client.call(port, "POST", "/logout", cookie = cookie)
      Client.call(port, "GET", "/admin", cookie = cookie)
    }
}
