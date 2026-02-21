package golem.ai

import org.scalatest.funsuite.AnyFunSuite

final class GraphCompileSpec extends AnyFunSuite {
  import Graph._

  test("PropertyValue — all 21 variants") {
    val values: List[PropertyValue] = List(
      PropertyValue.NullValue,
      PropertyValue.BooleanValue(true),
      PropertyValue.Int8Value(1.toByte),
      PropertyValue.Int16Value(2.toShort),
      PropertyValue.Int32Value(3),
      PropertyValue.Int64Value(4L),
      PropertyValue.UInt8Value(5),
      PropertyValue.UInt16Value(6),
      PropertyValue.UInt32Value(7L),
      PropertyValue.UInt64Value(BigInt(8)),
      PropertyValue.Float32Value(1.5f),
      PropertyValue.Float64Value(2.5),
      PropertyValue.StringValue("hello"),
      PropertyValue.Bytes(Array[Byte](1, 2, 3)),
      PropertyValue.DateValue(Date(2024, 6, 15)),
      PropertyValue.TimeValue(Time(14, 30, 45, 0)),
      PropertyValue.DatetimeValue(Datetime(Date(2024, 6, 15), Time(14, 30, 45, 0), Some(0.toShort))),
      PropertyValue.DurationValue(Duration(3600L, 0)),
      PropertyValue.PointValue(Point(1.0, 2.0, Some(3.0))),
      PropertyValue.LinestringValue(Linestring(List(Point(0.0, 0.0, None), Point(1.0, 1.0, None)))),
      PropertyValue.PolygonValue(Polygon(List(Point(0, 0, None), Point(1, 0, None), Point(0, 1, None)), None))
    )
    assert(values.size == 21)
  }

  test("ElementId — all 3 variants") {
    val ids: List[ElementId] = List(
      ElementId.StringValue("abc"),
      ElementId.Int64(42L),
      ElementId.Uuid("550e8400-e29b-41d4-a716-446655440000")
    )
    assert(ids.size == 3)
  }

  test("Vertex construction") {
    val v = Vertex(
      id = ElementId.Int64(1L),
      vertexType = "Person",
      additionalLabels = List("Employee"),
      properties = List("name" -> PropertyValue.StringValue("Alice"))
    )
    assert(v.vertexType == "Person")
    assert(v.properties.size == 1)
  }

  test("Edge construction") {
    val e = Edge(
      id = ElementId.Int64(100L),
      edgeType = "KNOWS",
      fromVertex = ElementId.Int64(1L),
      toVertex = ElementId.Int64(2L),
      properties = List("since" -> PropertyValue.Int32Value(2020))
    )
    assert(e.edgeType == "KNOWS")
  }

  test("Path construction") {
    val v1   = Vertex(ElementId.Int64(1L), "A", Nil, Nil)
    val v2   = Vertex(ElementId.Int64(2L), "B", Nil, Nil)
    val edge = Edge(ElementId.Int64(10L), "E", ElementId.Int64(1L), ElementId.Int64(2L), Nil)
    val path = Path(List(v1, v2), List(edge), 1)
    assert(path.length == 1)
  }

  test("Direction — all 3 variants and fromTag") {
    val dirs = List(Direction.Outgoing, Direction.Incoming, Direction.Both)
    assert(dirs.size == 3)
    dirs.foreach(d => assert(Direction.fromTag(d.tag) eq d))
  }

  test("ComparisonOperator — all 12 variants") {
    val ops = List(
      ComparisonOperator.Equal,
      ComparisonOperator.NotEqual,
      ComparisonOperator.LessThan,
      ComparisonOperator.LessThanOrEqual,
      ComparisonOperator.GreaterThan,
      ComparisonOperator.GreaterThanOrEqual,
      ComparisonOperator.Contains,
      ComparisonOperator.StartsWith,
      ComparisonOperator.EndsWith,
      ComparisonOperator.RegexMatch,
      ComparisonOperator.InList,
      ComparisonOperator.NotInList
    )
    assert(ops.size == 12)
    ops.foreach(o => assert(ComparisonOperator.fromTag(o.tag) eq o))
  }

  test("QueryResult — all 5 variants") {
    val results: List[QueryResult] = List(
      QueryResult.Vertices(Nil),
      QueryResult.Edges(Nil),
      QueryResult.Paths(Nil),
      QueryResult.Values(List(PropertyValue.Int32Value(1))),
      QueryResult.Maps(List(List("k" -> PropertyValue.StringValue("v"))))
    )
    assert(results.size == 5)
  }

  test("GraphError — all 15 variants") {
    val errors: List[GraphError] = List(
      GraphError.UnsupportedOperation("msg"),
      GraphError.ConnectionFailed("msg"),
      GraphError.AuthenticationFailed("msg"),
      GraphError.AuthorizationFailed("msg"),
      GraphError.ElementNotFound(ElementId.Int64(1L)),
      GraphError.DuplicateElement(ElementId.StringValue("x")),
      GraphError.SchemaViolation("msg"),
      GraphError.ConstraintViolation("msg"),
      GraphError.InvalidPropertyType("msg"),
      GraphError.InvalidQuery("msg"),
      GraphError.TransactionFailed("msg"),
      GraphError.TransactionConflict,
      GraphError.TransactionTimeout,
      GraphError.DeadlockDetected,
      GraphError.Timeout
    )
    assert(errors.size >= 15)
  }

  test("GraphError additional variants") {
    val more: List[GraphError] = List(
      GraphError.ResourceExhausted("msg"),
      GraphError.InternalError("msg"),
      GraphError.ServiceUnavailable("msg")
    )
    assert(more.size == 3)
  }

  test("ConnectionConfig construction") {
    val config = ConnectionConfig(
      hosts = Some(List("localhost")),
      port = Some(7687),
      databaseName = Some("neo4j"),
      username = Some("user"),
      password = Some("pass"),
      timeoutSeconds = Some(30),
      maxConnections = Some(10),
      providerConfig = List("key" -> "value")
    )
    assert(config.port.contains(7687))
    assert(config.providerConfig.size == 1)
  }

  test("GraphStatistics construction") {
    val stats = GraphStatistics(
      vertexCount = Some(BigInt(1000)),
      edgeCount = Some(BigInt(5000)),
      labelCount = Some(5),
      propertyCount = Some(BigInt(20))
    )
    assert(stats.vertexCount.contains(BigInt(1000)))
  }

  test("supporting record types compile") {
    val _ = FilterCondition("name", ComparisonOperator.Equal, PropertyValue.StringValue("x"))
    val _ = SortSpec("name", ascending = true)
    val _ = ExecuteQueryOptions("MATCH (n) RETURN n", None, None, None, None, None)
    val _ = CreateVertexOptions("Person", None, None)
    val _ = UpdateVertexOptions(ElementId.Int64(1L), Nil, None, None)
    val _ = CreateEdgeOptions("KNOWS", ElementId.Int64(1L), ElementId.Int64(2L), None)
    val _ = UpdateEdgeOptions(ElementId.Int64(10L), Nil, None, None)
    assert(true)
  }
}
