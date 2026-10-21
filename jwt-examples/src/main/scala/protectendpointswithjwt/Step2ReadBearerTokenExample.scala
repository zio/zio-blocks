package protectendpointswithjwt

import zio.http._

/**
 * How to Protect an Endpoint with JWT Bearer Tokens: Step 2 — Reading the
 * Bearer Token
 *
 * Reads the token from the `Authorization` header with the typed header API,
 * for a Bearer header, a Basic header, a header with no token and no header.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * protectendpointswithjwt.Step2ReadBearerTokenExample"
 */
object Step2ReadBearerTokenExample {

  def bearerToken(request: Request): Option[String] =
    request.header(Header.Authorization).collect { case Header.Authorization.Bearer(token) => token }

  def ordersRequest(authorization: Option[String]): Request = {
    val request = Request.get(URL.parse("/orders").fold(error => sys.error(error), url => url))
    authorization.fold(request)(value => request.addHeader("authorization", value))
  }

  def main(args: Array[String]): Unit = {
    println(bearerToken(ordersRequest(Some("Bearer eyJhbGciOiJIUzI1NiJ9.e30.c2ln"))))
    println(bearerToken(ordersRequest(Some("bearer eyJhbGciOiJIUzI1NiJ9.e30.c2ln"))))
    println(bearerToken(ordersRequest(Some("Basic cmVhZGVyOnNlY3JldA=="))))
    println(bearerToken(ordersRequest(Some("Bearer"))))
    println(bearerToken(ordersRequest(None)))
  }
}
