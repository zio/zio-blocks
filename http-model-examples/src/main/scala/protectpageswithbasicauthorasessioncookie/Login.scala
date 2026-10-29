package protectpageswithbasicauthorasessioncookie

import zio.blocks.html._
import zio.http._
import protectpageswithbasicauthorasessioncookie.Roastery._
import protectpageswithbasicauthorasessioncookie.SessionCookie._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Logging In With a
 * Form
 *
 * The sign-in page and the `logIn` handler, shared by the login sections and
 * the complete example.
 */
object Login {
  def loginPage(message: String): Response =
    page(
      "Staff Sign-In",
      p(message),
      form(method := "post", action := "/login")(
        label("Username ", input(name := "username")),
        label("Password ", input(name := "password", typeAttr := "password")),
        button("Sign in")
      )
    )

  def logIn(request: Request): Response = {
    val submitted = Form.fromString(request.body.text)
    val user      = for {
      username <- submitted.get("username")
      password <- submitted.get("password")
      user     <- Staff.authenticate(username, password)
    } yield user
    user match {
      case Some(name) => Response.seeOther("/admin").addCookie(sessionCookie(Sessions.start(name)))
      case None       => loginPage("Wrong username or password.").copy(status = Status.Forbidden)
    }
  }
}
