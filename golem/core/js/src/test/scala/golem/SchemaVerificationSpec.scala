package golem

import golem.data.multimodal.Multimodal
import golem.data.unstructured.{AllowedLanguages, AllowedMimeTypes, BinarySegment, TextSegment}
import golem.runtime.autowire.{AgentImplementation, MethodBinding}
import golem.runtime.annotations.{agentDefinition, agentImplementation}
import org.scalatest.funsuite.AnyFunSuite
import zio.blocks.schema.Schema

import scala.concurrent.Future
import scala.scalajs.js

/**
 * Verifies that agent method schemas are correctly generated for various
 * parameter and return types. This mirrors the TS SDK's decorator.test.ts which
 * verifies schema structure after registration.
 */
final class SchemaVerificationSpec extends AnyFunSuite {

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

  private lazy val defn = AgentImplementation.register[SchemaVerifyAgent]("schema-verify-agent")(
    new SchemaVerifyAgentImpl()
  )

  private def findMethod(name: String): MethodBinding[SchemaVerifyAgent] =
    defn.methodMetadata.find(_.metadata.name == name).getOrElse(sys.error(s"method not found: $name"))

  private def schemaTag(schema: js.Dynamic): String =
    schema.selectDynamic("tag").asInstanceOf[String]

  // ---------------------------------------------------------------------------
  // Tests: all schemas have correct top-level tags
  // ---------------------------------------------------------------------------

  private val multimodalMethods = Set("multimodalMethod")

  test("non-multimodal methods have tuple inputSchema tag") {
    defn.methodMetadata
      .filterNot(m => multimodalMethods(m.metadata.name))
      .foreach { m =>
        assert(schemaTag(m.inputSchema) == "tuple", s"method ${m.metadata.name} inputSchema should be tuple")
      }
  }

  test("non-multimodal methods have tuple outputSchema tag") {
    defn.methodMetadata
      .filterNot(m => multimodalMethods(m.metadata.name))
      .foreach { m =>
        assert(schemaTag(m.outputSchema) == "tuple", s"method ${m.metadata.name} outputSchema should be tuple")
      }
  }

  // ---------------------------------------------------------------------------
  // Tests: inputSchema element counts match parameter counts
  // ---------------------------------------------------------------------------

  private def inputElementCount(methodName: String): Int = {
    val m   = findMethod(methodName)
    val arr = m.inputSchema.selectDynamic("val").asInstanceOf[js.Array[js.Any]]
    arr.length
  }

  test("single-param method has 1 input element") {
    assert(inputElementCount("stringMethod") == 1)
    assert(inputElementCount("intMethod") == 1)
    assert(inputElementCount("boolMethod") == 1)
    assert(inputElementCount("optionMethod") == 1)
    assert(inputElementCount("listMethod") == 1)
    assert(inputElementCount("caseClassMethod") == 1)
  }

  test("multi-param method has 3 input elements") {
    assert(inputElementCount("multiParamMethod") == 3)
  }

  // ---------------------------------------------------------------------------
  // Tests: output element counts
  // ---------------------------------------------------------------------------

  private def outputElementCount(methodName: String): Int = {
    val m   = findMethod(methodName)
    val arr = m.outputSchema.selectDynamic("val").asInstanceOf[js.Array[js.Any]]
    arr.length
  }

  test("string-returning method has 1 output element") {
    assert(outputElementCount("stringMethod") == 1)
  }

  test("unit-returning method has 0 output elements") {
    assert(outputElementCount("unitReturnMethod") == 0)
  }

  // ---------------------------------------------------------------------------
  // Tests: input element names match parameter names
  // ---------------------------------------------------------------------------

  private def inputElementNames(methodName: String): List[String] = {
    val m   = findMethod(methodName)
    val arr = m.inputSchema.selectDynamic("val").asInstanceOf[js.Array[js.Array[js.Any]]]
    (0 until arr.length).map(i => arr(i)(0).asInstanceOf[String]).toList
  }

  test("single-param method element name is 'value' (from GolemSchema.single)") {
    assert(inputElementNames("stringMethod") == List("value"))
    assert(inputElementNames("intMethod") == List("value"))
    assert(inputElementNames("caseClassMethod") == List("value"))
  }

  test("multi-param method element names match parameter names") {
    assert(inputElementNames("multiParamMethod") == List("name", "age", "active"))
  }

  // ---------------------------------------------------------------------------
  // Tests: unstructured/multimodal schema tags
  // ---------------------------------------------------------------------------

  private def firstInputElementTag(methodName: String): String = {
    val m    = findMethod(methodName)
    val arr  = m.inputSchema.selectDynamic("val").asInstanceOf[js.Array[js.Array[js.Any]]]
    val elem = arr(0)(1).asInstanceOf[js.Dynamic]
    elem.selectDynamic("tag").asInstanceOf[String]
  }

  test("TextSegment parameter produces unstructured-text schema tag") {
    assert(firstInputElementTag("textSegmentMethod") == "unstructured-text")
  }

  test("BinarySegment parameter produces unstructured-binary schema tag") {
    assert(firstInputElementTag("binarySegmentMethod") == "unstructured-binary")
  }

  test("Multimodal parameter produces multimodal input schema tag") {
    val m = findMethod("multimodalMethod")
    assert(schemaTag(m.inputSchema) == "multimodal")
  }

  // ---------------------------------------------------------------------------
  // Tests: method count
  // ---------------------------------------------------------------------------

  test("schema-verify agent has 14 registered methods") {
    assert(defn.methodMetadata.size == 14)
  }
}
