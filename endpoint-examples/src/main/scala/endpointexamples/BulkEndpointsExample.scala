package endpointexamples

import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method
import zio.blocks.schema.Schema
import zio.http.Status

@main def runBulkEndpointsExample(): Unit =
  val api = "api" / endpoints {
    val customer = Endpoint(Method.GET / "customers")
    Endpoint(Method.GET / "health")
  }

  // val-named and auto-named access
  val c = api.customer
  val h = api.`GET /health`

  assert(c.route.render == "GET /customers")
  assert(h.route.render == "GET /health")

  println("BulkEndpointsExample OK")
