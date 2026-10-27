package correlatelogsandtraces

import zio.http._

/**
 * How to Correlate Logs and Traces Across Services: the glue that lets a
 * propagator read and write the headers of an http-model `Request`.
 */
object HttpGlue {
  val getHeader: (Request, String) => Option[String] =
    (request, name) => request.headers.rawGet(name)

  val setHeader: (Request, String, String) => Request =
    (request, name, value) => request.setHeader(name, value)
}
