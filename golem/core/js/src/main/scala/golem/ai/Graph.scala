package golem.ai

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.JavaScriptException

/**
 * Scala.js wrapper for `golem:graph/...@1.0.0`.
 *
 * Public API avoids `scala.scalajs.js.*` types.
 */
object Graph {
  // ----- Types -----------------------------------------------------------------------------

  sealed trait PropertyValue extends Product with Serializable
  object PropertyValue {
    case object NullValue                               extends PropertyValue
    final case class BooleanValue(value: Boolean)       extends PropertyValue
    final case class Int8Value(value: Byte)             extends PropertyValue
    final case class Int16Value(value: Short)           extends PropertyValue
    final case class Int32Value(value: Int)             extends PropertyValue
    final case class Int64Value(value: Long)            extends PropertyValue
    final case class UInt8Value(value: Int)             extends PropertyValue
    final case class UInt16Value(value: Int)            extends PropertyValue
    final case class UInt32Value(value: Long)           extends PropertyValue
    final case class UInt64Value(value: BigInt)         extends PropertyValue
    final case class Float32Value(value: Float)         extends PropertyValue
    final case class Float64Value(value: Double)        extends PropertyValue
    final case class StringValue(value: String)         extends PropertyValue
    final case class Bytes(value: Array[Byte])          extends PropertyValue
    final case class DateValue(value: Date)             extends PropertyValue
    final case class TimeValue(value: Time)             extends PropertyValue
    final case class DatetimeValue(value: Datetime)     extends PropertyValue
    final case class DurationValue(value: Duration)     extends PropertyValue
    final case class PointValue(value: Point)           extends PropertyValue
    final case class LinestringValue(value: Linestring) extends PropertyValue
    final case class PolygonValue(value: Polygon)       extends PropertyValue
  }

  final case class Date(year: Int, month: Int, day: Int)
  final case class Time(hour: Int, minute: Int, second: Int, nanosecond: Int)
  final case class Datetime(date: Date, time: Time, timezoneOffsetMinutes: Option[Short])
  final case class Duration(seconds: Long, nanoseconds: Int)
  final case class Point(longitude: Double, latitude: Double, altitude: Option[Double])
  final case class Linestring(coordinates: List[Point])
  final case class Polygon(exterior: List[Point], holes: Option[List[List[Point]]])

  sealed trait ElementId extends Product with Serializable
  object ElementId {
    final case class StringValue(value: String) extends ElementId
    final case class Int64(value: Long)         extends ElementId
    final case class Uuid(value: String)        extends ElementId
  }

  type PropertyMap = List[(String, PropertyValue)]

  final case class Vertex(
    id: ElementId,
    vertexType: String,
    additionalLabels: List[String],
    properties: PropertyMap
  )

  final case class Edge(
    id: ElementId,
    edgeType: String,
    fromVertex: ElementId,
    toVertex: ElementId,
    properties: PropertyMap
  )

  final case class Path(vertices: List[Vertex], edges: List[Edge], length: Int)

  sealed trait Direction extends Product with Serializable { def tag: String }
  object Direction {
    case object Outgoing extends Direction { val tag: String = "outgoing" }
    case object Incoming extends Direction { val tag: String = "incoming" }
    case object Both     extends Direction { val tag: String = "both"     }

    def fromTag(tag: String): Direction =
      tag match {
        case "incoming" => Incoming
        case "both"     => Both
        case _          => Outgoing
      }
  }

  sealed trait ComparisonOperator extends Product with Serializable { def tag: String }
  object ComparisonOperator {
    case object Equal              extends ComparisonOperator { val tag: String = "equal"                 }
    case object NotEqual           extends ComparisonOperator { val tag: String = "not-equal"             }
    case object LessThan           extends ComparisonOperator { val tag: String = "less-than"             }
    case object LessThanOrEqual    extends ComparisonOperator { val tag: String = "less-than-or-equal"    }
    case object GreaterThan        extends ComparisonOperator { val tag: String = "greater-than"          }
    case object GreaterThanOrEqual extends ComparisonOperator { val tag: String = "greater-than-or-equal" }
    case object Contains           extends ComparisonOperator { val tag: String = "contains"              }
    case object StartsWith         extends ComparisonOperator { val tag: String = "starts-with"           }
    case object EndsWith           extends ComparisonOperator { val tag: String = "ends-with"             }
    case object RegexMatch         extends ComparisonOperator { val tag: String = "regex-match"           }
    case object InList             extends ComparisonOperator { val tag: String = "in-list"               }
    case object NotInList          extends ComparisonOperator { val tag: String = "not-in-list"           }

    def fromTag(tag: String): ComparisonOperator =
      tag match {
        case "not-equal"             => NotEqual
        case "less-than"             => LessThan
        case "less-than-or-equal"    => LessThanOrEqual
        case "greater-than"          => GreaterThan
        case "greater-than-or-equal" => GreaterThanOrEqual
        case "contains"              => Contains
        case "starts-with"           => StartsWith
        case "ends-with"             => EndsWith
        case "regex-match"           => RegexMatch
        case "in-list"               => InList
        case "not-in-list"           => NotInList
        case _                       => Equal
      }
  }

  final case class FilterCondition(property: String, operator: ComparisonOperator, value: PropertyValue)
  final case class SortSpec(property: String, ascending: Boolean)

  final case class ExecuteQueryOptions(
    query: String,
    parameters: Option[QueryParameters],
    timeoutSeconds: Option[Int],
    maxResults: Option[Int],
    explain: Option[Boolean],
    profile: Option[Boolean]
  )

  sealed trait QueryResult extends Product with Serializable
  object QueryResult {
    final case class Vertices(values: List[Vertex])      extends QueryResult
    final case class Edges(values: List[Edge])           extends QueryResult
    final case class Paths(values: List[Path])           extends QueryResult
    final case class Values(values: List[PropertyValue]) extends QueryResult
    final case class Maps(values: List[PropertyMap])     extends QueryResult
  }

  type QueryParameters = List[(String, PropertyValue)]

  final case class CreateVertexOptions(
    vertexType: String,
    properties: Option[PropertyMap],
    labels: Option[List[String]]
  )

  final case class UpdateVertexOptions(
    id: ElementId,
    properties: PropertyMap,
    partial: Option[Boolean],
    createMissing: Option[Boolean]
  )

  final case class CreateEdgeOptions(
    edgeType: String,
    fromVertex: ElementId,
    toVertex: ElementId,
    properties: Option[PropertyMap]
  )

  final case class CreateMissingEdgeOptions(edgeType: String, fromVertex: ElementId, toVertex: ElementId)

  final case class UpdateEdgeOptions(
    id: ElementId,
    properties: PropertyMap,
    partial: Option[Boolean],
    createMissingWith: Option[CreateMissingEdgeOptions]
  )

  final case class FindVerticesOptions(
    vertexType: Option[String],
    filters: Option[List[FilterCondition]],
    sort: Option[List[SortSpec]],
    limit: Option[Int],
    offset: Option[Int]
  )

  final case class FindEdgesOptions(
    edgeTypes: Option[List[String]],
    filters: Option[List[FilterCondition]],
    sort: Option[List[SortSpec]],
    limit: Option[Int],
    offset: Option[Int]
  )

  final case class GetAdjacentVerticesOptions(
    vertexId: ElementId,
    direction: Direction,
    edgeTypes: Option[List[String]],
    limit: Option[Int]
  )

  final case class GetConnectedEdgesOptions(
    vertexId: ElementId,
    direction: Direction,
    edgeTypes: Option[List[String]],
    limit: Option[Int]
  )

  final case class QueryExecutionResult(
    queryResultValue: QueryResult,
    executionTimeMs: Option[Int],
    rowsAffected: Option[Int],
    explanation: Option[String],
    profileData: Option[String]
  )

  final case class PathOptions(
    maxDepth: Option[Int],
    edgeTypes: Option[List[String]],
    vertexTypes: Option[List[String]],
    vertexFilters: Option[List[FilterCondition]],
    edgeFilters: Option[List[FilterCondition]]
  )

  final case class GetNeighborhoodOptions(
    center: ElementId,
    depth: Int,
    direction: Direction,
    edgeTypes: Option[List[String]],
    maxVertices: Option[Int]
  )

  final case class Subgraph(vertices: List[Vertex], edges: List[Edge])

  final case class FindShortestPathOptions(fromVertex: ElementId, toVertex: ElementId, path: Option[PathOptions])
  final case class FindAllPathsOptions(
    fromVertex: ElementId,
    toVertex: ElementId,
    path: Option[PathOptions],
    limit: Option[Int]
  )
  final case class PathExistsOptions(fromVertex: ElementId, toVertex: ElementId, path: Option[PathOptions])
  final case class GetVerticesAtDistanceOptions(
    source: ElementId,
    distance: Int,
    direction: Direction,
    edgeTypes: Option[List[String]]
  )

  // ----- Errors ----------------------------------------------------------------------------

  sealed trait GraphError extends Product with Serializable { def tag: String }
  object GraphError {
    final case class UnsupportedOperation(message: String) extends GraphError {
      val tag: String = "unsupported-operation"
    }
    final case class ConnectionFailed(message: String)     extends GraphError { val tag: String = "connection-failed" }
    final case class AuthenticationFailed(message: String) extends GraphError {
      val tag: String = "authentication-failed"
    }
    final case class AuthorizationFailed(message: String) extends GraphError {
      val tag: String = "authorization-failed"
    }
    final case class ElementNotFound(id: ElementId)       extends GraphError { val tag: String = "element-not-found" }
    final case class DuplicateElement(id: ElementId)      extends GraphError { val tag: String = "duplicate-element" }
    final case class SchemaViolation(message: String)     extends GraphError { val tag: String = "schema-violation"  }
    final case class ConstraintViolation(message: String) extends GraphError {
      val tag: String = "constraint-violation"
    }
    final case class InvalidPropertyType(message: String) extends GraphError {
      val tag: String = "invalid-property-type"
    }
    final case class InvalidQuery(message: String)       extends GraphError { val tag: String = "invalid-query"        }
    final case class TransactionFailed(message: String)  extends GraphError { val tag: String = "transaction-failed"   }
    case object TransactionConflict                      extends GraphError { val tag: String = "transaction-conflict" }
    case object TransactionTimeout                       extends GraphError { val tag: String = "transaction-timeout"  }
    case object DeadlockDetected                         extends GraphError { val tag: String = "deadlock-detected"    }
    case object Timeout                                  extends GraphError { val tag: String = "timeout"              }
    final case class ResourceExhausted(message: String)  extends GraphError { val tag: String = "resource-exhausted"   }
    final case class InternalError(message: String)      extends GraphError { val tag: String = "internal-error"       }
    final case class ServiceUnavailable(message: String) extends GraphError { val tag: String = "service-unavailable"  }
  }

  // ----- Connection ------------------------------------------------------------------------

  final case class ConnectionConfig(
    hosts: Option[List[String]],
    port: Option[Int],
    databaseName: Option[String],
    username: Option[String],
    password: Option[String],
    timeoutSeconds: Option[Int],
    maxConnections: Option[Int],
    providerConfig: List[(String, String)]
  )

  final case class GraphStatistics(
    vertexCount: Option[BigInt],
    edgeCount: Option[BigInt],
    labelCount: Option[Int],
    propertyCount: Option[BigInt]
  )

  final class GraphHandle private[golem] (private val underlying: js.Dynamic) {
    def beginTransaction(): Either[GraphError, TransactionHandle] =
      wrapResult(underlying.beginTransaction())(d => new TransactionHandle(d.asInstanceOf[js.Dynamic]))

    def beginReadTransaction(): Either[GraphError, TransactionHandle] =
      wrapResult(underlying.beginReadTransaction())(d => new TransactionHandle(d.asInstanceOf[js.Dynamic]))

    def ping(): Either[GraphError, Unit] =
      wrapResult(underlying.ping())(_ => ())

    def close(): Either[GraphError, Unit] =
      wrapResult(underlying.close())(_ => ())

    def getStatistics(): Either[GraphError, GraphStatistics] =
      wrapResult(underlying.getStatistics())(fromJsGraphStatistics)
  }

  def connectResult(config: ConnectionConfig): Either[GraphError, GraphHandle] =
    wrapResult(ConnectionModule.connect(toJsConnectionConfig(config)))(d => new GraphHandle(d.asInstanceOf[js.Dynamic]))

  def connect(config: ConnectionConfig): GraphHandle =
    connectResult(config) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalStateException(s"Graph error ${err.tag}")
    }

  // ----- Transactions ---------------------------------------------------------------------

  final class TransactionHandle private[golem] (private val underlying: js.Dynamic) {
    def executeQuery(options: ExecuteQueryOptions): Either[GraphError, QueryExecutionResult] =
      wrapResult(underlying.executeQuery(toJsExecuteQueryOptions(options)))(fromJsQueryExecutionResult)

    def findShortestPath(options: FindShortestPathOptions): Either[GraphError, Option[Path]] =
      wrapResult(underlying.findShortestPath(toJsFindShortestPathOptions(options)))(fromJsOptionalPath)

    def findAllPaths(options: FindAllPathsOptions): Either[GraphError, List[Path]] =
      wrapResult(underlying.findAllPaths(toJsFindAllPathsOptions(options)))(fromJsPathList)

    def getNeighborhood(options: GetNeighborhoodOptions): Either[GraphError, Subgraph] =
      wrapResult(underlying.getNeighborhood(toJsGetNeighborhoodOptions(options)))(fromJsSubgraph)

    def pathExists(options: PathExistsOptions): Either[GraphError, Boolean] =
      wrapResult(underlying.pathExists(toJsPathExistsOptions(options)))(_.toString.toBoolean)

    def getVerticesAtDistance(options: GetVerticesAtDistanceOptions): Either[GraphError, List[Vertex]] =
      wrapResult(underlying.getVerticesAtDistance(toJsGetVerticesAtDistanceOptions(options)))(fromJsVertexList)

    def getAdjacentVertices(options: GetAdjacentVerticesOptions): Either[GraphError, List[Vertex]] =
      wrapResult(underlying.getAdjacentVertices(toJsGetAdjacentVerticesOptions(options)))(fromJsVertexList)

    def getConnectedEdges(options: GetConnectedEdgesOptions): Either[GraphError, List[Edge]] =
      wrapResult(underlying.getConnectedEdges(toJsGetConnectedEdgesOptions(options)))(fromJsEdgeList)

    def createVertex(options: CreateVertexOptions): Either[GraphError, Vertex] =
      wrapResult(underlying.createVertex(toJsCreateVertexOptions(options)))(fromJsVertex)

    def createVertices(vertices: List[CreateVertexOptions]): Either[GraphError, List[Vertex]] =
      wrapResult(underlying.createVertices(js.Array(vertices.map(toJsCreateVertexOptions): _*)))(fromJsVertexList)

    def getVertex(id: ElementId): Either[GraphError, Option[Vertex]] =
      wrapResult(underlying.getVertex(toJsElementId(id)))(fromJsOptionalVertex)

    def updateVertex(options: UpdateVertexOptions): Either[GraphError, Vertex] =
      wrapResult(underlying.updateVertex(toJsUpdateVertexOptions(options)))(fromJsVertex)

    def deleteVertex(id: ElementId, deleteEdges: Boolean): Either[GraphError, Unit] =
      wrapResult(underlying.deleteVertex(toJsElementId(id), deleteEdges))(_ => ())

    def findVertices(options: FindVerticesOptions): Either[GraphError, List[Vertex]] =
      wrapResult(underlying.findVertices(toJsFindVerticesOptions(options)))(fromJsVertexList)

    def createEdge(options: CreateEdgeOptions): Either[GraphError, Edge] =
      wrapResult(underlying.createEdge(toJsCreateEdgeOptions(options)))(fromJsEdge)

    def createEdges(edges: List[CreateEdgeOptions]): Either[GraphError, List[Edge]] =
      wrapResult(underlying.createEdges(js.Array(edges.map(toJsCreateEdgeOptions): _*)))(fromJsEdgeList)

    def getEdge(id: ElementId): Either[GraphError, Option[Edge]] =
      wrapResult(underlying.getEdge(toJsElementId(id)))(fromJsOptionalEdge)

    def updateEdge(options: UpdateEdgeOptions): Either[GraphError, Edge] =
      wrapResult(underlying.updateEdge(toJsUpdateEdgeOptions(options)))(fromJsEdge)

    def deleteEdge(id: ElementId): Either[GraphError, Unit] =
      wrapResult(underlying.deleteEdge(toJsElementId(id)))(_ => ())

    def findEdges(options: FindEdgesOptions): Either[GraphError, List[Edge]] =
      wrapResult(underlying.findEdges(toJsFindEdgesOptions(options)))(fromJsEdgeList)

    def commit(): Either[GraphError, Unit] =
      wrapResult(underlying.commit())(_ => ())

    def rollback(): Either[GraphError, Unit] =
      wrapResult(underlying.rollback())(_ => ())

    def isActive(): Boolean =
      underlying.isActive().asInstanceOf[Boolean]
  }

  // ----- Schema ----------------------------------------------------------------------------

  sealed trait PropertyType extends Product with Serializable { def tag: String }
  object PropertyType {
    case object BooleanType  extends PropertyType { val tag: String = "boolean"      }
    case object Int32Type    extends PropertyType { val tag: String = "int32"        }
    case object Int64Type    extends PropertyType { val tag: String = "int64"        }
    case object Float32Type  extends PropertyType { val tag: String = "float32-type" }
    case object Float64Type  extends PropertyType { val tag: String = "float64-type" }
    case object StringType   extends PropertyType { val tag: String = "string-type"  }
    case object Bytes        extends PropertyType { val tag: String = "bytes"        }
    case object DateType     extends PropertyType { val tag: String = "date"         }
    case object DatetimeType extends PropertyType { val tag: String = "datetime"     }
    case object PointType    extends PropertyType { val tag: String = "point"        }
    case object ListType     extends PropertyType { val tag: String = "list-type"    }
    case object MapType      extends PropertyType { val tag: String = "map-type"     }

    def fromTag(tag: String): PropertyType =
      tag match {
        case "int32"        => Int32Type
        case "int64"        => Int64Type
        case "float32-type" => Float32Type
        case "float64-type" => Float64Type
        case "string-type"  => StringType
        case "bytes"        => Bytes
        case "date"         => DateType
        case "datetime"     => DatetimeType
        case "point"        => PointType
        case "list-type"    => ListType
        case "map-type"     => MapType
        case _              => BooleanType
      }
  }

  sealed trait IndexType extends Product with Serializable { def tag: String }
  object IndexType {
    case object Exact      extends IndexType { val tag: String = "exact"      }
    case object Range      extends IndexType { val tag: String = "range"      }
    case object Text       extends IndexType { val tag: String = "text"       }
    case object Geospatial extends IndexType { val tag: String = "geospatial" }

    def fromTag(tag: String): IndexType =
      tag match {
        case "range"      => Range
        case "text"       => Text
        case "geospatial" => Geospatial
        case _            => Exact
      }
  }

  final case class PropertyDefinition(
    name: String,
    propertyType: PropertyType,
    required: Boolean,
    unique: Boolean,
    defaultValue: Option[PropertyValue]
  )

  final case class VertexLabelSchema(label: String, properties: List[PropertyDefinition], container: Option[String])
  final case class EdgeLabelSchema(
    label: String,
    properties: List[PropertyDefinition],
    fromLabels: Option[List[String]],
    toLabels: Option[List[String]],
    container: Option[String]
  )

  final case class IndexDefinition(
    name: String,
    label: String,
    properties: List[String],
    indexType: IndexType,
    unique: Boolean,
    container: Option[String]
  )

  final case class EdgeTypeDefinition(collection: String, fromCollections: List[String], toCollections: List[String])

  sealed trait ContainerType extends Product with Serializable { def tag: String }
  object ContainerType {
    case object VertexContainer extends ContainerType { val tag: String = "vertex-container" }
    case object EdgeContainer   extends ContainerType { val tag: String = "edge-container"   }

    def fromTag(tag: String): ContainerType = if (tag == "edge-container") EdgeContainer else VertexContainer
  }

  final case class ContainerInfo(name: String, containerType: ContainerType, elementCount: Option[BigInt])

  final class SchemaManager private[golem] (private val underlying: js.Dynamic) {
    def defineVertexLabel(schema: VertexLabelSchema): Either[GraphError, Unit] =
      wrapResult(underlying.defineVertexLabel(toJsVertexLabelSchema(schema)))(_ => ())

    def defineEdgeLabel(schema: EdgeLabelSchema): Either[GraphError, Unit] =
      wrapResult(underlying.defineEdgeLabel(toJsEdgeLabelSchema(schema)))(_ => ())

    def getVertexLabelSchema(label: String): Either[GraphError, Option[VertexLabelSchema]] =
      wrapResult(underlying.getVertexLabelSchema(label))(fromJsOptionalVertexLabelSchema)

    def getEdgeLabelSchema(label: String): Either[GraphError, Option[EdgeLabelSchema]] =
      wrapResult(underlying.getEdgeLabelSchema(label))(fromJsOptionalEdgeLabelSchema)

    def listVertexLabels(): Either[GraphError, List[String]] =
      wrapResult(underlying.listVertexLabels())(asArray(_).toList.map(_.toString))

    def listEdgeLabels(): Either[GraphError, List[String]] =
      wrapResult(underlying.listEdgeLabels())(asArray(_).toList.map(_.toString))

    def createIndex(index: IndexDefinition): Either[GraphError, Unit] =
      wrapResult(underlying.createIndex(toJsIndexDefinition(index)))(_ => ())

    def dropIndex(name: String): Either[GraphError, Unit] =
      wrapResult(underlying.dropIndex(name))(_ => ())

    def listIndexes(): Either[GraphError, List[IndexDefinition]] =
      wrapResult(underlying.listIndexes())(fromJsIndexList)

    def getIndex(name: String): Either[GraphError, Option[IndexDefinition]] =
      wrapResult(underlying.getIndex(name))(fromJsOptionalIndexDefinition)

    def defineEdgeType(definition: EdgeTypeDefinition): Either[GraphError, Unit] =
      wrapResult(underlying.defineEdgeType(toJsEdgeTypeDefinition(definition)))(_ => ())

    def listEdgeTypes(): Either[GraphError, List[EdgeTypeDefinition]] =
      wrapResult(underlying.listEdgeTypes())(fromJsEdgeTypeList)

    def createContainer(name: String, containerType: ContainerType): Either[GraphError, Unit] =
      wrapResult(underlying.createContainer(name, containerType.tag))(_ => ())

    def listContainers(): Either[GraphError, List[ContainerInfo]] =
      wrapResult(underlying.listContainers())(fromJsContainerInfoList)
  }

  def getSchemaManagerResult(config: Option[ConnectionConfig]): Either[GraphError, SchemaManager] =
    wrapResult(SchemaModule.getSchemaManager(config.fold[js.Any](js.undefined)(toJsConnectionConfig)))(d =>
      new SchemaManager(d.asInstanceOf[js.Dynamic])
    )

  def getSchemaManager(config: Option[ConnectionConfig]): SchemaManager =
    getSchemaManagerResult(config) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalStateException(s"Graph error ${err.tag}")
    }

  // ----- Conversions -----------------------------------------------------------------------

  private type JObj = js.Dictionary[js.Any]

  private def toJsPropertyValue(value: PropertyValue): JObj =
    value match {
      case PropertyValue.NullValue =>
        js.Dictionary[js.Any]("tag" -> "null-value")
      case PropertyValue.BooleanValue(v) =>
        js.Dictionary[js.Any]("tag" -> "boolean", "val" -> v)
      case PropertyValue.Int8Value(v) =>
        js.Dictionary[js.Any]("tag" -> "int8", "val" -> v.toInt)
      case PropertyValue.Int16Value(v) =>
        js.Dictionary[js.Any]("tag" -> "int16", "val" -> v.toInt)
      case PropertyValue.Int32Value(v) =>
        js.Dictionary[js.Any]("tag" -> "int32", "val" -> v)
      case PropertyValue.Int64Value(v) =>
        js.Dictionary[js.Any]("tag" -> "int64", "val" -> toJsBigInt(BigInt(v)))
      case PropertyValue.UInt8Value(v) =>
        js.Dictionary[js.Any]("tag" -> "uint8", "val" -> v)
      case PropertyValue.UInt16Value(v) =>
        js.Dictionary[js.Any]("tag" -> "uint16", "val" -> v)
      case PropertyValue.UInt32Value(v) =>
        js.Dictionary[js.Any]("tag" -> "uint32", "val" -> v.toDouble)
      case PropertyValue.UInt64Value(v) =>
        js.Dictionary[js.Any]("tag" -> "uint64", "val" -> toJsBigInt(v))
      case PropertyValue.Float32Value(v) =>
        js.Dictionary[js.Any]("tag" -> "float32-value", "val" -> v)
      case PropertyValue.Float64Value(v) =>
        js.Dictionary[js.Any]("tag" -> "float64-value", "val" -> v)
      case PropertyValue.StringValue(v) =>
        js.Dictionary[js.Any]("tag" -> "string-value", "val" -> v)
      case PropertyValue.Bytes(v) =>
        js.Dictionary[js.Any]("tag" -> "bytes", "val" -> toJsByteArray(v))
      case PropertyValue.DateValue(v) =>
        js.Dictionary[js.Any]("tag" -> "date", "val" -> toJsDate(v))
      case PropertyValue.TimeValue(v) =>
        js.Dictionary[js.Any]("tag" -> "time", "val" -> toJsTime(v))
      case PropertyValue.DatetimeValue(v) =>
        js.Dictionary[js.Any]("tag" -> "datetime", "val" -> toJsDatetime(v))
      case PropertyValue.DurationValue(v) =>
        js.Dictionary[js.Any]("tag" -> "duration", "val" -> toJsDuration(v))
      case PropertyValue.PointValue(v) =>
        js.Dictionary[js.Any]("tag" -> "point", "val" -> toJsPoint(v))
      case PropertyValue.LinestringValue(v) =>
        js.Dictionary[js.Any]("tag" -> "linestring", "val" -> toJsLinestring(v))
      case PropertyValue.PolygonValue(v) =>
        js.Dictionary[js.Any]("tag" -> "polygon", "val" -> toJsPolygon(v))
    }

  private def fromJsPropertyValue(raw: js.Dynamic): PropertyValue = {
    val tag = tagOf(raw)
    val v   = valOf(raw)
    tag match {
      case "null-value"    => PropertyValue.NullValue
      case "boolean"       => PropertyValue.BooleanValue(v.asInstanceOf[Boolean])
      case "int8"          => PropertyValue.Int8Value(v.toString.toInt.toByte)
      case "int16"         => PropertyValue.Int16Value(v.toString.toInt.toShort)
      case "int32"         => PropertyValue.Int32Value(v.toString.toInt)
      case "int64"         => PropertyValue.Int64Value(fromJsBigInt(v).toLong)
      case "uint8"         => PropertyValue.UInt8Value(v.toString.toInt)
      case "uint16"        => PropertyValue.UInt16Value(v.toString.toInt)
      case "uint32"        => PropertyValue.UInt32Value(v.toString.toLong)
      case "uint64"        => PropertyValue.UInt64Value(fromJsBigInt(v))
      case "float32-value" => PropertyValue.Float32Value(v.toString.toFloat)
      case "float64-value" => PropertyValue.Float64Value(v.toString.toDouble)
      case "string-value"  => PropertyValue.StringValue(v.toString)
      case "bytes"         => PropertyValue.Bytes(fromJsByteArray(v))
      case "date"          => PropertyValue.DateValue(fromJsDate(v))
      case "time"          => PropertyValue.TimeValue(fromJsTime(v))
      case "datetime"      => PropertyValue.DatetimeValue(fromJsDatetime(v))
      case "duration"      => PropertyValue.DurationValue(fromJsDuration(v))
      case "point"         => PropertyValue.PointValue(fromJsPoint(v))
      case "linestring"    => PropertyValue.LinestringValue(fromJsLinestring(v))
      case "polygon"       => PropertyValue.PolygonValue(fromJsPolygon(v))
      case _               => PropertyValue.StringValue(v.toString)
    }
  }

  private def toJsDate(value: Date): JObj =
    js.Dictionary[js.Any]("year" -> value.year, "month" -> value.month, "day" -> value.day)

  private def fromJsDate(raw: js.Dynamic): Date = {
    val obj = raw.asInstanceOf[JObj]
    Date(
      year = obj.getOrElse("year", 0).toString.toInt,
      month = obj.getOrElse("month", 1).toString.toInt,
      day = obj.getOrElse("day", 1).toString.toInt
    )
  }

  private def toJsTime(value: Time): JObj =
    js.Dictionary[js.Any](
      "hour"       -> value.hour,
      "minute"     -> value.minute,
      "second"     -> value.second,
      "nanosecond" -> value.nanosecond
    )

  private def fromJsTime(raw: js.Dynamic): Time = {
    val obj = raw.asInstanceOf[JObj]
    Time(
      hour = obj.getOrElse("hour", 0).toString.toInt,
      minute = obj.getOrElse("minute", 0).toString.toInt,
      second = obj.getOrElse("second", 0).toString.toInt,
      nanosecond = obj.getOrElse("nanosecond", 0).toString.toInt
    )
  }

  private def toJsDatetime(value: Datetime): JObj =
    js.Dictionary[js.Any](
      "date"                    -> toJsDate(value.date),
      "time"                    -> toJsTime(value.time),
      "timezone-offset-minutes" -> value.timezoneOffsetMinutes.fold[js.Any](js.undefined)(identity)
    )

  private def fromJsDatetime(raw: js.Dynamic): Datetime = {
    val obj = raw.asInstanceOf[JObj]
    Datetime(
      date = fromJsDate(obj.getOrElse("date", js.Dictionary()).asInstanceOf[js.Dynamic]),
      time = fromJsTime(obj.getOrElse("time", js.Dictionary()).asInstanceOf[js.Dynamic]),
      timezoneOffsetMinutes = obj.get("timezone-offset-minutes").map(_.toString.toShort)
    )
  }

  private def toJsDuration(value: Duration): JObj =
    js.Dictionary[js.Any](
      "seconds"     -> toJsBigInt(BigInt(value.seconds)),
      "nanoseconds" -> value.nanoseconds
    )

  private def fromJsDuration(raw: js.Dynamic): Duration = {
    val obj = raw.asInstanceOf[JObj]
    Duration(
      seconds = fromJsBigInt(obj.getOrElse("seconds", 0)).toLong,
      nanoseconds = obj.getOrElse("nanoseconds", 0).toString.toInt
    )
  }

  private def toJsPoint(value: Point): JObj =
    js.Dictionary[js.Any](
      "longitude" -> value.longitude,
      "latitude"  -> value.latitude,
      "altitude"  -> value.altitude.fold[js.Any](js.undefined)(identity)
    )

  private def fromJsPoint(raw: js.Dynamic): Point = {
    val obj = raw.asInstanceOf[JObj]
    Point(
      longitude = obj.getOrElse("longitude", 0.0).toString.toDouble,
      latitude = obj.getOrElse("latitude", 0.0).toString.toDouble,
      altitude = obj.get("altitude").map(_.toString.toDouble)
    )
  }

  private def toJsLinestring(value: Linestring): JObj =
    js.Dictionary[js.Any]("coordinates" -> js.Array(value.coordinates.map(toJsPoint): _*))

  private def fromJsLinestring(raw: js.Dynamic): Linestring = {
    val obj = raw.asInstanceOf[JObj]
    Linestring(asArray(obj.getOrElse("coordinates", js.Array())).toList.map(fromJsPoint))
  }

  private def toJsPolygon(value: Polygon): JObj =
    js.Dictionary[js.Any](
      "exterior" -> js.Array(value.exterior.map(toJsPoint): _*),
      "holes"    -> value.holes.fold[js.Any](js.undefined)(hs => js.Array(hs.map(h => js.Array(h.map(toJsPoint): _*)): _*))
    )

  private def fromJsPolygon(raw: js.Dynamic): Polygon = {
    val obj      = raw.asInstanceOf[JObj]
    val exterior = asArray(obj.getOrElse("exterior", js.Array())).toList.map(fromJsPoint)
    val holes    = obj.get("holes").map { value =>
      asArray(value).toList.map(inner => asArray(inner).toList.map(fromJsPoint))
    }
    Polygon(exterior, holes)
  }

  private def toJsElementId(id: ElementId): JObj =
    id match {
      case ElementId.StringValue(value) =>
        js.Dictionary[js.Any]("tag" -> "string-value", "val" -> value)
      case ElementId.Int64(value) =>
        js.Dictionary[js.Any]("tag" -> "int64", "val" -> toJsBigInt(BigInt(value)))
      case ElementId.Uuid(value) =>
        js.Dictionary[js.Any]("tag" -> "uuid", "val" -> value)
    }

  private def fromJsElementId(raw: js.Dynamic): ElementId = {
    val tag = tagOf(raw)
    val v   = valOf(raw)
    tag match {
      case "int64" => ElementId.Int64(fromJsBigInt(v).toLong)
      case "uuid"  => ElementId.Uuid(v.toString)
      case _       => ElementId.StringValue(v.toString)
    }
  }

  private def toJsPropertyMap(values: PropertyMap): js.Array[js.Array[js.Any]] =
    js.Array(values.map { case (k, v) => js.Array[js.Any](k, toJsPropertyValue(v)) }: _*)

  private def fromJsPropertyMap(raw: js.Any): PropertyMap =
    asArray(raw).toList.map { item =>
      val tuple = item.asInstanceOf[js.Array[js.Any]]
      val key   = tuple(0).toString
      val value = fromJsPropertyValue(tuple(1).asInstanceOf[js.Dynamic])
      key -> value
    }

  private def fromJsVertex(raw: js.Dynamic): Vertex = {
    val obj = raw.asInstanceOf[JObj]
    Vertex(
      id = fromJsElementId(obj.getOrElse("id", js.Dictionary()).asInstanceOf[js.Dynamic]),
      vertexType = obj.getOrElse("vertex-type", "").toString,
      additionalLabels = asArray(obj.getOrElse("additional-labels", js.Array())).toList.map(_.toString),
      properties = fromJsPropertyMap(obj.getOrElse("properties", js.Array()))
    )
  }

  private def fromJsVertexList(raw: js.Dynamic): List[Vertex] =
    asArray(raw).toList.map(fromJsVertex)

  private def fromJsOptionalVertex(raw: js.Dynamic): Option[Vertex] =
    optionDynamic(raw).map(fromJsVertex)

  private def fromJsEdge(raw: js.Dynamic): Edge = {
    val obj = raw.asInstanceOf[JObj]
    Edge(
      id = fromJsElementId(obj.getOrElse("id", js.Dictionary()).asInstanceOf[js.Dynamic]),
      edgeType = obj.getOrElse("edge-type", "").toString,
      fromVertex = fromJsElementId(obj.getOrElse("from-vertex", js.Dictionary()).asInstanceOf[js.Dynamic]),
      toVertex = fromJsElementId(obj.getOrElse("to-vertex", js.Dictionary()).asInstanceOf[js.Dynamic]),
      properties = fromJsPropertyMap(obj.getOrElse("properties", js.Array()))
    )
  }

  private def fromJsEdgeList(raw: js.Dynamic): List[Edge] =
    asArray(raw).toList.map(fromJsEdge)

  private def fromJsOptionalEdge(raw: js.Dynamic): Option[Edge] =
    optionDynamic(raw).map(fromJsEdge)

  private def fromJsPath(raw: js.Dynamic): Path = {
    val obj = raw.asInstanceOf[JObj]
    Path(
      vertices = asArray(obj.getOrElse("vertices", js.Array())).toList.map(fromJsVertex),
      edges = asArray(obj.getOrElse("edges", js.Array())).toList.map(fromJsEdge),
      length = obj.getOrElse("length", 0).toString.toInt
    )
  }

  private def fromJsPathList(raw: js.Dynamic): List[Path] =
    asArray(raw).toList.map(fromJsPath)

  private def fromJsOptionalPath(raw: js.Dynamic): Option[Path] =
    optionDynamic(raw).map(fromJsPath)

  private def toJsExecuteQueryOptions(options: ExecuteQueryOptions): JObj =
    js.Dictionary[js.Any](
      "query"           -> options.query,
      "parameters"      -> options.parameters.fold[js.Any](js.undefined)(toJsPropertyMap),
      "timeout-seconds" -> options.timeoutSeconds.fold[js.Any](js.undefined)(identity),
      "max-results"     -> options.maxResults.fold[js.Any](js.undefined)(identity),
      "explain"         -> options.explain.fold[js.Any](js.undefined)(identity),
      "profile"         -> options.profile.fold[js.Any](js.undefined)(identity)
    )

  private def fromJsQueryExecutionResult(raw: js.Dynamic): QueryExecutionResult = {
    val obj = raw.asInstanceOf[JObj]
    QueryExecutionResult(
      queryResultValue =
        fromJsQueryResult(obj.getOrElse("query-result-value", js.Dictionary()).asInstanceOf[js.Dynamic]),
      executionTimeMs = obj.get("execution-time-ms").map(_.toString.toInt),
      rowsAffected = obj.get("rows-affected").map(_.toString.toInt),
      explanation = obj.get("explanation").map(_.toString),
      profileData = obj.get("profile-data").map(_.toString)
    )
  }

  private def fromJsQueryResult(raw: js.Dynamic): QueryResult = {
    val tag = tagOf(raw)
    val v   = valOf(raw)
    tag match {
      case "vertices" => QueryResult.Vertices(asArray(v).toList.map(fromJsVertex))
      case "edges"    => QueryResult.Edges(asArray(v).toList.map(fromJsEdge))
      case "paths"    => QueryResult.Paths(asArray(v).toList.map(fromJsPath))
      case "values"   => QueryResult.Values(asArray(v).toList.map(fromJsPropertyValue))
      case "maps"     =>
        val maps = asArray(v).toList.map(fromJsPropertyMap)
        QueryResult.Maps(maps)
      case _ => QueryResult.Values(Nil)
    }
  }

  private def toJsCreateVertexOptions(options: CreateVertexOptions): JObj =
    js.Dictionary[js.Any](
      "vertex-type" -> options.vertexType,
      "properties"  -> options.properties.fold[js.Any](js.undefined)(toJsPropertyMap),
      "labels"      -> options.labels.fold[js.Any](js.undefined)(ls => js.Array(ls: _*))
    )

  private def toJsUpdateVertexOptions(options: UpdateVertexOptions): JObj =
    js.Dictionary[js.Any](
      "id"             -> toJsElementId(options.id),
      "properties"     -> toJsPropertyMap(options.properties),
      "partial"        -> options.partial.fold[js.Any](js.undefined)(identity),
      "create-missing" -> options.createMissing.fold[js.Any](js.undefined)(identity)
    )

  private def toJsCreateEdgeOptions(options: CreateEdgeOptions): JObj =
    js.Dictionary[js.Any](
      "edge-type"   -> options.edgeType,
      "from-vertex" -> toJsElementId(options.fromVertex),
      "to-vertex"   -> toJsElementId(options.toVertex),
      "properties"  -> options.properties.fold[js.Any](js.undefined)(toJsPropertyMap)
    )

  private def toJsCreateMissingEdgeOptions(options: CreateMissingEdgeOptions): JObj =
    js.Dictionary[js.Any](
      "edge-type"   -> options.edgeType,
      "from-vertex" -> toJsElementId(options.fromVertex),
      "to-vertex"   -> toJsElementId(options.toVertex)
    )

  private def toJsUpdateEdgeOptions(options: UpdateEdgeOptions): JObj =
    js.Dictionary[js.Any](
      "id"                  -> toJsElementId(options.id),
      "properties"          -> toJsPropertyMap(options.properties),
      "partial"             -> options.partial.fold[js.Any](js.undefined)(identity),
      "create-missing-with" -> options.createMissingWith.fold[js.Any](js.undefined)(toJsCreateMissingEdgeOptions)
    )

  private def toJsFindVerticesOptions(options: FindVerticesOptions): JObj =
    js.Dictionary[js.Any](
      "vertex-type" -> options.vertexType.fold[js.Any](js.undefined)(identity),
      "filters"     -> options.filters.fold[js.Any](js.undefined)(fs => js.Array(fs.map(toJsFilterCondition): _*)),
      "sort"        -> options.sort.fold[js.Any](js.undefined)(ss => js.Array(ss.map(toJsSortSpec): _*)),
      "limit"       -> options.limit.fold[js.Any](js.undefined)(identity),
      "offset"      -> options.offset.fold[js.Any](js.undefined)(identity)
    )

  private def toJsFindEdgesOptions(options: FindEdgesOptions): JObj =
    js.Dictionary[js.Any](
      "edge-types" -> options.edgeTypes.fold[js.Any](js.undefined)(xs => js.Array(xs: _*)),
      "filters"    -> options.filters.fold[js.Any](js.undefined)(fs => js.Array(fs.map(toJsFilterCondition): _*)),
      "sort"       -> options.sort.fold[js.Any](js.undefined)(ss => js.Array(ss.map(toJsSortSpec): _*)),
      "limit"      -> options.limit.fold[js.Any](js.undefined)(identity),
      "offset"     -> options.offset.fold[js.Any](js.undefined)(identity)
    )

  private def toJsGetAdjacentVerticesOptions(options: GetAdjacentVerticesOptions): JObj =
    js.Dictionary[js.Any](
      "vertex-id"  -> toJsElementId(options.vertexId),
      "direction"  -> options.direction.tag,
      "edge-types" -> options.edgeTypes.fold[js.Any](js.undefined)(xs => js.Array(xs: _*)),
      "limit"      -> options.limit.fold[js.Any](js.undefined)(identity)
    )

  private def toJsGetConnectedEdgesOptions(options: GetConnectedEdgesOptions): JObj =
    js.Dictionary[js.Any](
      "vertex-id"  -> toJsElementId(options.vertexId),
      "direction"  -> options.direction.tag,
      "edge-types" -> options.edgeTypes.fold[js.Any](js.undefined)(xs => js.Array(xs: _*)),
      "limit"      -> options.limit.fold[js.Any](js.undefined)(identity)
    )

  private def toJsPathOptions(options: PathOptions): JObj =
    js.Dictionary[js.Any](
      "max-depth"      -> options.maxDepth.fold[js.Any](js.undefined)(identity),
      "edge-types"     -> options.edgeTypes.fold[js.Any](js.undefined)(xs => js.Array(xs: _*)),
      "vertex-types"   -> options.vertexTypes.fold[js.Any](js.undefined)(xs => js.Array(xs: _*)),
      "vertex-filters" -> options.vertexFilters.fold[js.Any](js.undefined)(fs =>
        js.Array(fs.map(toJsFilterCondition): _*)
      ),
      "edge-filters" -> options.edgeFilters.fold[js.Any](js.undefined)(fs => js.Array(fs.map(toJsFilterCondition): _*))
    )

  private def toJsFindShortestPathOptions(options: FindShortestPathOptions): JObj =
    js.Dictionary[js.Any](
      "from-vertex" -> toJsElementId(options.fromVertex),
      "to-vertex"   -> toJsElementId(options.toVertex),
      "path"        -> options.path.fold[js.Any](js.undefined)(toJsPathOptions)
    )

  private def toJsFindAllPathsOptions(options: FindAllPathsOptions): JObj =
    js.Dictionary[js.Any](
      "from-vertex" -> toJsElementId(options.fromVertex),
      "to-vertex"   -> toJsElementId(options.toVertex),
      "path"        -> options.path.fold[js.Any](js.undefined)(toJsPathOptions),
      "limit"       -> options.limit.fold[js.Any](js.undefined)(identity)
    )

  private def toJsPathExistsOptions(options: PathExistsOptions): JObj =
    js.Dictionary[js.Any](
      "from-vertex" -> toJsElementId(options.fromVertex),
      "to-vertex"   -> toJsElementId(options.toVertex),
      "path"        -> options.path.fold[js.Any](js.undefined)(toJsPathOptions)
    )

  private def toJsGetNeighborhoodOptions(options: GetNeighborhoodOptions): JObj =
    js.Dictionary[js.Any](
      "center"       -> toJsElementId(options.center),
      "depth"        -> options.depth,
      "direction"    -> options.direction.tag,
      "edge-types"   -> options.edgeTypes.fold[js.Any](js.undefined)(xs => js.Array(xs: _*)),
      "max-vertices" -> options.maxVertices.fold[js.Any](js.undefined)(identity)
    )

  private def toJsGetVerticesAtDistanceOptions(options: GetVerticesAtDistanceOptions): JObj =
    js.Dictionary[js.Any](
      "source"     -> toJsElementId(options.source),
      "distance"   -> options.distance,
      "direction"  -> options.direction.tag,
      "edge-types" -> options.edgeTypes.fold[js.Any](js.undefined)(xs => js.Array(xs: _*))
    )

  private def toJsFilterCondition(cond: FilterCondition): JObj =
    js.Dictionary[js.Any](
      "property" -> cond.property,
      "operator" -> cond.operator.tag,
      "value"    -> toJsPropertyValue(cond.value)
    )

  private def toJsSortSpec(spec: SortSpec): JObj =
    js.Dictionary[js.Any]("property" -> spec.property, "ascending" -> spec.ascending)

  private def fromJsSubgraph(raw: js.Dynamic): Subgraph = {
    val obj = raw.asInstanceOf[JObj]
    Subgraph(
      vertices = asArray(obj.getOrElse("vertices", js.Array())).toList.map(fromJsVertex),
      edges = asArray(obj.getOrElse("edges", js.Array())).toList.map(fromJsEdge)
    )
  }

  private def toJsConnectionConfig(config: ConnectionConfig): JObj =
    js.Dictionary[js.Any](
      "hosts"           -> config.hosts.fold[js.Any](js.undefined)(hs => js.Array(hs: _*)),
      "port"            -> config.port.fold[js.Any](js.undefined)(identity),
      "database-name"   -> config.databaseName.fold[js.Any](js.undefined)(identity),
      "username"        -> config.username.fold[js.Any](js.undefined)(identity),
      "password"        -> config.password.fold[js.Any](js.undefined)(identity),
      "timeout-seconds" -> config.timeoutSeconds.fold[js.Any](js.undefined)(identity),
      "max-connections" -> config.maxConnections.fold[js.Any](js.undefined)(identity),
      "provider-config" -> js.Array(config.providerConfig.map { case (k, v) => js.Array[js.Any](k, v) }: _*)
    )

  private def fromJsGraphStatistics(raw: js.Dynamic): GraphStatistics = {
    val obj = raw.asInstanceOf[JObj]
    GraphStatistics(
      vertexCount = obj.get("vertex-count").map(fromJsBigInt),
      edgeCount = obj.get("edge-count").map(fromJsBigInt),
      labelCount = obj.get("label-count").map(_.toString.toInt),
      propertyCount = obj.get("property-count").map(fromJsBigInt)
    )
  }

  private def toJsPropertyDefinition(defn: PropertyDefinition): JObj =
    js.Dictionary[js.Any](
      "name"          -> defn.name,
      "property-type" -> defn.propertyType.tag,
      "required"      -> defn.required,
      "unique"        -> defn.unique,
      "default-value" -> defn.defaultValue.fold[js.Any](js.undefined)(toJsPropertyValue)
    )

  private def fromJsPropertyDefinition(raw: js.Dynamic): PropertyDefinition = {
    val obj = raw.asInstanceOf[JObj]
    PropertyDefinition(
      name = obj.getOrElse("name", "").toString,
      propertyType = PropertyType.fromTag(obj.getOrElse("property-type", "boolean").toString),
      required = obj.getOrElse("required", false).asInstanceOf[Boolean],
      unique = obj.getOrElse("unique", false).asInstanceOf[Boolean],
      defaultValue = obj.get("default-value").map(_.asInstanceOf[js.Dynamic]).map(fromJsPropertyValue)
    )
  }

  private def toJsVertexLabelSchema(schema: VertexLabelSchema): JObj =
    js.Dictionary[js.Any](
      "label"      -> schema.label,
      "properties" -> js.Array(schema.properties.map(toJsPropertyDefinition): _*),
      "container"  -> schema.container.fold[js.Any](js.undefined)(identity)
    )

  private def fromJsVertexLabelSchema(raw: js.Dynamic): VertexLabelSchema = {
    val obj = raw.asInstanceOf[JObj]
    VertexLabelSchema(
      label = obj.getOrElse("label", "").toString,
      properties = asArray(obj.getOrElse("properties", js.Array())).toList.map(fromJsPropertyDefinition),
      container = obj.get("container").map(_.toString)
    )
  }

  private def fromJsOptionalVertexLabelSchema(raw: js.Dynamic): Option[VertexLabelSchema] =
    optionDynamic(raw).map(fromJsVertexLabelSchema)

  private def toJsEdgeLabelSchema(schema: EdgeLabelSchema): JObj =
    js.Dictionary[js.Any](
      "label"       -> schema.label,
      "properties"  -> js.Array(schema.properties.map(toJsPropertyDefinition): _*),
      "from-labels" -> schema.fromLabels.fold[js.Any](js.undefined)(ls => js.Array(ls: _*)),
      "to-labels"   -> schema.toLabels.fold[js.Any](js.undefined)(ls => js.Array(ls: _*)),
      "container"   -> schema.container.fold[js.Any](js.undefined)(identity)
    )

  private def fromJsEdgeLabelSchema(raw: js.Dynamic): EdgeLabelSchema = {
    val obj = raw.asInstanceOf[JObj]
    EdgeLabelSchema(
      label = obj.getOrElse("label", "").toString,
      properties = asArray(obj.getOrElse("properties", js.Array())).toList.map(fromJsPropertyDefinition),
      fromLabels = obj.get("from-labels").map(asArray).map(_.toList.map(_.toString)),
      toLabels = obj.get("to-labels").map(asArray).map(_.toList.map(_.toString)),
      container = obj.get("container").map(_.toString)
    )
  }

  private def fromJsOptionalEdgeLabelSchema(raw: js.Dynamic): Option[EdgeLabelSchema] =
    optionDynamic(raw).map(fromJsEdgeLabelSchema)

  private def toJsIndexDefinition(defn: IndexDefinition): JObj =
    js.Dictionary[js.Any](
      "name"       -> defn.name,
      "label"      -> defn.label,
      "properties" -> js.Array(defn.properties: _*),
      "index-type" -> defn.indexType.tag,
      "unique"     -> defn.unique,
      "container"  -> defn.container.fold[js.Any](js.undefined)(identity)
    )

  private def fromJsIndexDefinition(raw: js.Dynamic): IndexDefinition = {
    val obj = raw.asInstanceOf[JObj]
    IndexDefinition(
      name = obj.getOrElse("name", "").toString,
      label = obj.getOrElse("label", "").toString,
      properties = asArray(obj.getOrElse("properties", js.Array())).toList.map(_.toString),
      indexType = IndexType.fromTag(obj.getOrElse("index-type", "exact").toString),
      unique = obj.getOrElse("unique", false).asInstanceOf[Boolean],
      container = obj.get("container").map(_.toString)
    )
  }

  private def fromJsIndexList(raw: js.Dynamic): List[IndexDefinition] =
    asArray(raw).toList.map(fromJsIndexDefinition)

  private def fromJsOptionalIndexDefinition(raw: js.Dynamic): Option[IndexDefinition] =
    optionDynamic(raw).map(fromJsIndexDefinition)

  private def toJsEdgeTypeDefinition(defn: EdgeTypeDefinition): JObj =
    js.Dictionary[js.Any](
      "collection"       -> defn.collection,
      "from-collections" -> js.Array(defn.fromCollections: _*),
      "to-collections"   -> js.Array(defn.toCollections: _*)
    )

  private def fromJsEdgeTypeDefinition(raw: js.Dynamic): EdgeTypeDefinition = {
    val obj = raw.asInstanceOf[JObj]
    EdgeTypeDefinition(
      collection = obj.getOrElse("collection", "").toString,
      fromCollections = asArray(obj.getOrElse("from-collections", js.Array())).toList.map(_.toString),
      toCollections = asArray(obj.getOrElse("to-collections", js.Array())).toList.map(_.toString)
    )
  }

  private def fromJsEdgeTypeList(raw: js.Dynamic): List[EdgeTypeDefinition] =
    asArray(raw).toList.map(fromJsEdgeTypeDefinition)

  private def fromJsContainerInfo(raw: js.Dynamic): ContainerInfo = {
    val obj = raw.asInstanceOf[JObj]
    ContainerInfo(
      name = obj.getOrElse("name", "").toString,
      containerType = ContainerType.fromTag(obj.getOrElse("container-type", "vertex-container").toString),
      elementCount = obj.get("element-count").map(fromJsBigInt)
    )
  }

  private def fromJsContainerInfoList(raw: js.Dynamic): List[ContainerInfo] =
    asArray(raw).toList.map(fromJsContainerInfo)

  private def fromJsGraphError(raw: js.Dynamic): GraphError = {
    val tag = tagOf(raw)
    val v   = valOf(raw)
    tag match {
      case "unsupported-operation" => GraphError.UnsupportedOperation(v.toString)
      case "connection-failed"     => GraphError.ConnectionFailed(v.toString)
      case "authentication-failed" => GraphError.AuthenticationFailed(v.toString)
      case "authorization-failed"  => GraphError.AuthorizationFailed(v.toString)
      case "element-not-found"     => GraphError.ElementNotFound(fromJsElementId(v))
      case "duplicate-element"     => GraphError.DuplicateElement(fromJsElementId(v))
      case "schema-violation"      => GraphError.SchemaViolation(v.toString)
      case "constraint-violation"  => GraphError.ConstraintViolation(v.toString)
      case "invalid-property-type" => GraphError.InvalidPropertyType(v.toString)
      case "invalid-query"         => GraphError.InvalidQuery(v.toString)
      case "transaction-failed"    => GraphError.TransactionFailed(v.toString)
      case "transaction-conflict"  => GraphError.TransactionConflict
      case "transaction-timeout"   => GraphError.TransactionTimeout
      case "deadlock-detected"     => GraphError.DeadlockDetected
      case "timeout"               => GraphError.Timeout
      case "resource-exhausted"    => GraphError.ResourceExhausted(v.toString)
      case "internal-error"        => GraphError.InternalError(v.toString)
      case "service-unavailable"   => GraphError.ServiceUnavailable(v.toString)
      case _                       => GraphError.InternalError(v.toString)
    }
  }

  private def wrapResult[A](thunk: => js.Dynamic)(decode: js.Dynamic => A): Either[GraphError, A] =
    try {
      decodeResult(thunk)(decode, fromJsGraphError)
    } catch {
      case JavaScriptException(value) =>
        Left(fromJsGraphError(value.asInstanceOf[js.Dynamic]))
      case other: Throwable =>
        Left(GraphError.InternalError(other.toString))
    }

  private def decodeResult[A](
    raw: js.Dynamic
  )(ok: js.Dynamic => A, err: js.Dynamic => GraphError): Either[GraphError, A] = {
    val tag = tagOf(raw)
    tag match {
      case "ok" | "success" => Right(ok(valOf(raw)))
      case "err" | "error"  => Left(err(valOf(raw)))
      case _                => Right(ok(raw))
    }
  }

  private def tagOf(value: js.Dynamic): String = {
    val tag = value.asInstanceOf[js.Dynamic].selectDynamic("tag")
    if (js.isUndefined(tag) || tag == null) "" else tag.toString
  }

  private def valOf(value: js.Dynamic): js.Dynamic = {
    val dyn = value.asInstanceOf[js.Dynamic]
    if (!js.isUndefined(dyn.selectDynamic("val"))) dyn.selectDynamic("val").asInstanceOf[js.Dynamic]
    else if (!js.isUndefined(dyn.selectDynamic("value"))) dyn.selectDynamic("value").asInstanceOf[js.Dynamic]
    else dyn
  }

  private def optionDynamic(value: js.Any): Option[js.Dynamic] =
    if (value == null || js.isUndefined(value)) None
    else {
      val dyn = value.asInstanceOf[js.Dynamic]
      val tag = dyn.selectDynamic("tag")
      if (!js.isUndefined(tag) && tag != null) {
        tag.toString match {
          case "some" => Some(dyn.selectDynamic("val").asInstanceOf[js.Dynamic])
          case "none" => None
          case _      => Some(dyn)
        }
      } else Some(dyn)
    }

  private def asArray(value: js.Any): js.Array[js.Dynamic] =
    value.asInstanceOf[js.Array[js.Dynamic]]

  private def toJsBigInt(value: BigInt): js.BigInt =
    js.BigInt(value.toString)

  private def fromJsBigInt(value: js.Any): BigInt =
    BigInt(value.toString)

  private def toJsByteArray(bytes: Array[Byte]): Uint8Array = {
    val array = new Uint8Array(bytes.length)
    var i     = 0
    while (i < bytes.length) {
      array(i) = (bytes(i) & 0xff).toShort
      i += 1
    }
    array
  }

  private def fromJsByteArray(value: js.Dynamic): Array[Byte] = {
    val any = value.asInstanceOf[js.Any]
    if (js.Array.isArray(any)) {
      any.asInstanceOf[js.Array[js.Dynamic]].map(_.toString.toInt.toByte).toArray
    } else if (any.isInstanceOf[Uint8Array]) {
      val typed = any.asInstanceOf[Uint8Array]
      val out   = new Array[Byte](typed.length)
      var i     = 0
      while (i < typed.length) {
        out(i) = typed(i).toByte
        i += 1
      }
      out
    } else {
      any.toString.getBytes("UTF-8")
    }
  }

  @js.native
  @JSImport("golem:graph/connection@1.0.0", JSImport.Namespace)
  private object ConnectionModule extends js.Object {
    def connect(config: js.Dictionary[js.Any]): js.Dynamic = js.native
  }

  @js.native
  @JSImport("golem:graph/schema@1.0.0", JSImport.Namespace)
  private object SchemaModule extends js.Object {
    def getSchemaManager(config: js.Any): js.Dynamic = js.native
  }
}
