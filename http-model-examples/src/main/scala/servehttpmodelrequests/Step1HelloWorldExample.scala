package servehttpmodelrequests

import zio.http._

/**
 * How to Serve http-model Requests with the JDK HTTP Server — Serving a Hello
 * World Handler
 *
 * Serves one `Request => Response` function on a free loopback port, calls it
 * with GET and HEAD, then shows that a failing handler becomes a 500 response.
 *
 * Run with: cd http-model-examples && sbt "runMain
 * servehttpmodelrequests.Step1HelloWorldExample"
 */
object Step1HelloWorldExample {
  val hello: Request => Response =
    request => Response.text(s"Hello from ${request.method.name} ${request.path.encode}")

  val broken: Request => Response =
    _ => throw new IllegalStateException("the catalog is not loaded")

  def main(args: Array[String]): Unit = {
    JdkHttp.withServer(hello) { port =>
      Client.call(port, "GET", "/books")
      Client.call(port, "HEAD", "/books")
    }
    JdkHttp.withServer(broken) { port =>
      Client.call(port, "GET", "/books")
    }
  }
}
