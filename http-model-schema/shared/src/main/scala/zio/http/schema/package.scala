package zio.http

import scala.language.implicitConversions

import zio.http.{Headers, QueryParams, Request, Response}

package object schema {
  implicit def queryParamsSchemaOps(qp: QueryParams): QueryParamsSchemaOps = new QueryParamsSchemaOps(qp)
  implicit def headersSchemaOps(headers: Headers): HeadersSchemaOps        = new HeadersSchemaOps(headers)
  implicit def requestSchemaOps(request: Request): RequestSchemaOps        = new RequestSchemaOps(request)
  implicit def responseSchemaOps(response: Response): ResponseSchemaOps    = new ResponseSchemaOps(response)
}
