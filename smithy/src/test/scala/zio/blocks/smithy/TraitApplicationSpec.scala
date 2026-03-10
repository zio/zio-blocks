package zio.blocks.smithy

import zio.test._

object TraitApplicationSpec extends ZIOSpecDefault {
  def spec = suite("TraitApplicationSpec")(
    test("create basic trait application with no value") {
      val trait1 = TraitApplication(ShapeId("smithy.api", "required"), None)
      assertTrue(
        trait1.id == ShapeId("smithy.api", "required"),
        trait1.value == None
      )
    },
    test("create trait application with string value") {
      val value  = NodeValue.String("my documentation")
      val trait1 = TraitApplication(ShapeId("smithy.api", "documentation"), Some(value))
      assertTrue(
        trait1.id == ShapeId("smithy.api", "documentation"),
        trait1.value == Some(value)
      )
    },
    test("TraitApplication.required creates @required trait") {
      val trait1 = TraitApplication.required
      assertTrue(
        trait1.id == ShapeId("smithy.api", "required"),
        trait1.value == None
      )
    },
    test("TraitApplication.documentation creates @documentation trait") {
      val text   = "This is a shape"
      val trait1 = TraitApplication.documentation(text)
      assertTrue(
        trait1.id == ShapeId("smithy.api", "documentation"),
        trait1.value.contains(NodeValue.String(text))
      )
    },
    test("TraitApplication.http creates @http trait with method and uri") {
      val method = "GET"
      val uri    = "/users/{id}"
      val trait1 = TraitApplication.http(method, uri)
      assertTrue(
        trait1.id == ShapeId("smithy.api", "http"),
        trait1.value.isDefined
      )
    },
    test("TraitApplication.http value contains correct fields") {
      val method = "POST"
      val uri    = "/api/create"
      val trait1 = TraitApplication.http(method, uri)
      val value  = trait1.value.get
      assertTrue(value.isInstanceOf[NodeValue.Object])
    },
    test("TraitApplication.error creates @error trait") {
      val errorType = "client"
      val trait1    = TraitApplication.error(errorType)
      assertTrue(
        trait1.id == ShapeId("smithy.api", "error"),
        trait1.value.contains(NodeValue.String(errorType))
      )
    },
    test("multiple trait applications are independent") {
      val t1 = TraitApplication.required
      val t2 = TraitApplication.documentation("doc text")
      val t3 = TraitApplication.error("server")
      assertTrue(
        t1.id != t2.id,
        t2.id != t3.id,
        t1.value != t2.value,
        t1.value != t3.value
      )
    },
    test("trait application with complex object value") {
      val objectValue = NodeValue.Object(
        List(
          "method" -> NodeValue.String("GET"),
          "uri"    -> NodeValue.String("/path")
        )
      )
      val trait1 = TraitApplication(ShapeId("smithy.api", "http"), Some(objectValue))
      assertTrue(
        trait1.id == ShapeId("smithy.api", "http"),
        trait1.value == Some(objectValue),
        trait1.value.get.isInstanceOf[NodeValue.Object]
      )
    },
    test("trait application with array value") {
      val arrayValue = NodeValue.Array(
        List(
          NodeValue.String("error1"),
          NodeValue.String("error2")
        )
      )
      val trait1 = TraitApplication(ShapeId("smithy.api", "errors"), Some(arrayValue))
      assertTrue(
        trait1.id == ShapeId("smithy.api", "errors"),
        trait1.value == Some(arrayValue),
        trait1.value.get.isInstanceOf[NodeValue.Array]
      )
    },
    test("TraitApplication equality") {
      val t1 = TraitApplication.required
      val t2 = TraitApplication.required
      assertTrue(t1 == t2)
    },
    test("TraitApplication inequality") {
      val t1 = TraitApplication.required
      val t2 = TraitApplication.documentation("test")
      assertTrue(t1 != t2)
    }
  )
}
