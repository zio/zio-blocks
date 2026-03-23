package golem

import golem.data.multimodal.Multimodal
import golem.data.unstructured.{AllowedLanguages, AllowedMimeTypes, BinarySegment, TextSegment}
import golem.runtime.autowire.{AgentImplementation, MethodBinding}
import zio.test._
import zio.blocks.schema.Schema

import scala.concurrent.Future
import scala.scalajs.js

object SchemaVerificationSpec extends ZIOSpecDefault {

  // ---------------------------------------------------------------------------
  // Fixture types
  // ---------------------------------------------------------------------------

  final case class PersonInfo(name: String, age: Int)
  object PersonInfo { implicit val schema: Schema[PersonInfo] = Schema.derived }

  final case class Address(street: String, city: String, zip: Int)
  object Address { implicit val schema: Schema[Address] = Schema.derived }

  sealed trait SvLang
  object SvLang {
    implicit val allowed: AllowedLanguages[SvLang] = new AllowedLanguages[SvLang] {
      override val codes: Option[List[String]] = Some(List("en", "de"))
    }
  }

  sealed trait SvMime
  object SvMime {
    implicit val allowed: AllowedMimeTypes[SvMime] = new AllowedMimeTypes[SvMime] {
      override val mimeTypes: Option[List[String]] = Some(List("application/json"))
    }
  }

  // ---------------------------------------------------------------------------
  // Agent with many parameter/return type combinations
  // ---------------------------------------------------------------------------

  @agentDefinition("schema-verify-agent")
  trait SchemaVerifyAgent extends BaseAgent[Unit] {
    def stringMethod(s: String): Future[String]
    def intMethod(i: Int): Future[Int]
    def boolMethod(b: Boolean): Future[Boolean]
    def longMethod(l: Long): Future[Long]
    def doubleMethod(d: Double): Future[Double]
    def floatMethod(f: Float): Future[Float]
    def optionMethod(o: Option[String]): Future[Option[String]]
    def listMethod(l: List[Int]): Future[List[Int]]
    def caseClassMethod(p: PersonInfo): Future[PersonInfo]
    def multiParamMethod(name: String, age: Int, active: Boolean): Future[String]
    def unitReturnMethod(s: String): Future[Unit]
    def textSegmentMethod(t: TextSegment[SvLang]): Future[TextSegment[SvLang]]
    def binarySegmentMethod(b: BinarySegment[SvMime]): Future[BinarySegment[SvMime]]
    def multimodalMethod(m: Multimodal[PersonInfo]): Future[Multimodal[PersonInfo]]
  }

  @agentImplementation()
  final class SchemaVerifyAgentImpl() extends SchemaVerifyAgent {
    override def stringMethod(s: String): Future[String]                                   = Future.successful(s)
    override def intMethod(i: Int): Future[Int]                                            = Future.successful(i)
    override def boolMethod(b: Boolean): Future[Boolean]                                   = Future.successful(b)
    override def longMethod(l: Long): Future[Long]                                         = Future.successful(l)
    override def doubleMethod(d: Double): Future[Double]                                   = Future.successful(d)
    override def floatMethod(f: Float): Future[Float]                                      = Future.successful(f)
    override def optionMethod(o: Option[String]): Future[Option[String]]                   = Future.successful(o)
    override def listMethod(l: List[Int]): Future[List[Int]]                               = Future.successful(l)
    override def caseClassMethod(p: PersonInfo): Future[PersonInfo]                        = Future.successful(p)
    override def multiParamMethod(name: String, age: Int, active: Boolean): Future[String] =
      Future.successful(s"$name-$age-$active")
    override def unitReturnMethod(s: String): Future[Unit]                                    = Future.successful(())
    override def textSegmentMethod(t: TextSegment[SvLang]): Future[TextSegment[SvLang]]       = Future.successful(t)
    override def binarySegmentMethod(b: BinarySegment[SvMime]): Future[BinarySegment[SvMime]] = Future.successful(b)
    override def multimodalMethod(m: Multimodal[PersonInfo]): Future[Multimodal[PersonInfo]]  = Future.successful(m)
  }

  private lazy val defn = AgentImplementation.registerClass[SchemaVerifyAgent, SchemaVerifyAgentImpl]

  private def findMethod(name: String): MethodBinding[SchemaVerifyAgent] =
    defn.methodMetadata.find(_.metadata.name == name).getOrElse(sys.error(s"method not found: $name"))

  private def schemaTag(schema: golem.host.js.JsDataSchema): String =
    schema.tag

  private val multimodalMethods = Set("multimodalMethod")

  private def inputElementCount(methodName: String): Int = {
    val m   = findMethod(methodName)
    val arr = m.inputSchema.asInstanceOf[js.Dynamic].selectDynamic("val").asInstanceOf[js.Array[js.Any]]
    arr.length
  }

  private def outputElementCount(methodName: String): Int = {
    val m   = findMethod(methodName)
    val arr = m.outputSchema.asInstanceOf[js.Dynamic].selectDynamic("val").asInstanceOf[js.Array[js.Any]]
    arr.length
  }

  private def inputElementNames(methodName: String): List[String] = {
    val m   = findMethod(methodName)
    val arr = m.inputSchema.asInstanceOf[js.Dynamic].selectDynamic("val").asInstanceOf[js.Array[js.Array[js.Any]]]
    (0 until arr.length).map(i => arr(i)(0).asInstanceOf[String]).toList
  }

  private def firstInputElementTag(methodName: String): String = {
    val m    = findMethod(methodName)
    val arr  = m.inputSchema.asInstanceOf[js.Dynamic].selectDynamic("val").asInstanceOf[js.Array[js.Array[js.Any]]]
    val elem = arr(0)(1).asInstanceOf[js.Dynamic]
    elem.selectDynamic("tag").asInstanceOf[String]
  }

  def spec = suite("SchemaVerificationSpec")(
    test("non-multimodal methods have tuple inputSchema tag") {
      defn.methodMetadata
        .filterNot(m => multimodalMethods(m.metadata.name))
        .foreach { m =>
          assert(schemaTag(m.inputSchema) == "tuple")(Assertion.isTrue)
        }
      assertCompletes
    },
    test("non-multimodal methods have tuple outputSchema tag") {
      defn.methodMetadata
        .filterNot(m => multimodalMethods(m.metadata.name))
        .foreach { m =>
          assert(schemaTag(m.outputSchema) == "tuple")(Assertion.isTrue)
        }
      assertCompletes
    },
    test("single-param method has 1 input element") {
      assertTrue(
        inputElementCount("stringMethod") == 1,
        inputElementCount("intMethod") == 1,
        inputElementCount("boolMethod") == 1,
        inputElementCount("optionMethod") == 1,
        inputElementCount("listMethod") == 1,
        inputElementCount("caseClassMethod") == 1
      )
    },
    test("multi-param method has 3 input elements") {
      assertTrue(inputElementCount("multiParamMethod") == 3)
    },
    test("string-returning method has 1 output element") {
      assertTrue(outputElementCount("stringMethod") == 1)
    },
    test("unit-returning method has 0 output elements") {
      assertTrue(outputElementCount("unitReturnMethod") == 0)
    },
    test("single-param method element name is 'value' (from GolemSchema.single)") {
      assertTrue(
        inputElementNames("stringMethod") == List("value"),
        inputElementNames("intMethod") == List("value"),
        inputElementNames("caseClassMethod") == List("value")
      )
    },
    test("multi-param method element names match parameter names") {
      assertTrue(inputElementNames("multiParamMethod") == List("name", "age", "active"))
    },
    test("TextSegment parameter produces unstructured-text schema tag") {
      assertTrue(firstInputElementTag("textSegmentMethod") == "unstructured-text")
    },
    test("BinarySegment parameter produces unstructured-binary schema tag") {
      assertTrue(firstInputElementTag("binarySegmentMethod") == "unstructured-binary")
    },
    test("Multimodal parameter produces multimodal input schema tag") {
      val m = findMethod("multimodalMethod")
      assertTrue(schemaTag(m.inputSchema) == "multimodal")
    },
    test("schema-verify agent has 14 registered methods") {
      assertTrue(defn.methodMetadata.size == 14)
    }
  )
}
