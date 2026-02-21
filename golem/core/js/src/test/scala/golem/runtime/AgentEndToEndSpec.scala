package golem.runtime

import golem.runtime.autowire.{AgentImplementation, HostPayload, MethodBinding}
import golem.runtime.Sum
import golem.BaseAgent
import golem.runtime.annotations.{DurabilityMode, agentDefinition}
import golem.runtime.util.FutureInterop
import org.scalatest.funsuite.AsyncFunSuite
import zio.blocks.schema.Schema

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

final class AgentEndToEndSpec extends AsyncFunSuite {
  override implicit def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  // ---------------------------------------------------------------------------
  // Fixture types
  // ---------------------------------------------------------------------------

  final case class DeepNested(label: String, values: List[Int])
  object DeepNested { implicit val schema: Schema[DeepNested] = Schema.derived }

  final case class Outer(name: String, inner: DeepNested)
  object Outer { implicit val schema: Schema[Outer] = Schema.derived }

  // ---------------------------------------------------------------------------
  // Agent with many method signatures for roundtrip testing
  // ---------------------------------------------------------------------------

  @agentDefinition("e2e-broad", mode = DurabilityMode.Durable)
  trait BroadAgent extends BaseAgent[Unit] {
    def echo(in: String): Future[String]
    def add(in: Sum): Future[Int]
    def echoInt(in: Int): Future[Int]
    def echoBoolean(in: Boolean): Future[Boolean]
    def echoOptionSome(in: Option[String]): Future[Option[String]]
    def echoOptionNone(in: Option[String]): Future[Option[String]]
    def echoList(in: List[Int]): Future[List[Int]]
    def echoListEmpty(in: List[Int]): Future[List[Int]]
    def echoNested(in: Outer): Future[Outer]
    def multiParam(a: String, b: Int): Future[String]
    def asyncVoid(in: String): Future[Unit]
    def echoLong(in: Long): Future[Long]
    def echoDouble(in: Double): Future[Double]
  }

  private val broadImpl = new BroadAgent {
    override def echo(in: String): Future[String]                           = Future.successful(s"hello $in")
    override def add(in: Sum): Future[Int]                                  = Future.successful(in.a + in.b)
    override def echoInt(in: Int): Future[Int]                              = Future.successful(in)
    override def echoBoolean(in: Boolean): Future[Boolean]                  = Future.successful(in)
    override def echoOptionSome(in: Option[String]): Future[Option[String]] = Future.successful(in)
    override def echoOptionNone(in: Option[String]): Future[Option[String]] = Future.successful(in)
    override def echoList(in: List[Int]): Future[List[Int]]                 = Future.successful(in)
    override def echoListEmpty(in: List[Int]): Future[List[Int]]            = Future.successful(in)
    override def echoNested(in: Outer): Future[Outer]                       = Future.successful(in)
    override def multiParam(a: String, b: Int): Future[String]              = Future.successful(s"$a-$b")
    override def asyncVoid(in: String): Future[Unit]                        = Future.successful(())
    override def echoLong(in: Long): Future[Long]                           = Future.successful(in)
    override def echoDouble(in: Double): Future[Double]                     = Future.successful(in)
  }

  private lazy val broadDefn = AgentImplementation.register[BroadAgent]("e2e-broad")(broadImpl)

  private def liftEither[A](e: Either[String, A]): Future[A] =
    e.fold(err => Future.failed(js.JavaScriptException(err)), Future.successful)

  private def binding[T](
    name: String,
    defn: golem.runtime.autowire.AgentDefinition[T]
  ): MethodBinding[T] =
    defn.methodMetadata.find(_.metadata.name == name).getOrElse(sys.error(s"binding not found: $name"))

  private def roundtrip[In: Schema, Out: Schema](
    methodName: String,
    input: In,
    expected: Out
  ): Future[org.scalatest.Assertion] = {
    val b = binding(methodName, broadDefn)
    for {
      payload <- liftEither(HostPayload.encode[In](input))
      raw     <- FutureInterop.fromPromise(b.invoke(broadImpl, payload))
      decoded <- liftEither(HostPayload.decode[Out](raw))
    } yield assert(decoded == expected)
  }

  // ---------------------------------------------------------------------------
  // Tests: String roundtrip
  // ---------------------------------------------------------------------------

  test("echo string roundtrips through binding") {
    roundtrip[String, String]("echo", "world", "hello world")
  }

  // ---------------------------------------------------------------------------
  // Tests: Case class payload
  // ---------------------------------------------------------------------------

  test("case class payload roundtrips through binding") {
    roundtrip[Sum, Int]("add", Sum(2, 3), 5)
  }

  // ---------------------------------------------------------------------------
  // Tests: Primitive types
  // ---------------------------------------------------------------------------

  test("Int roundtrips through binding") {
    roundtrip[Int, Int]("echoInt", 42, 42)
  }

  test("Boolean roundtrips through binding") {
    roundtrip[Boolean, Boolean]("echoBoolean", true, true)
  }

  test("Long roundtrips through binding") {
    roundtrip[Long, Long]("echoLong", 9876543210L, 9876543210L)
  }

  test("Double roundtrips through binding") {
    roundtrip[Double, Double]("echoDouble", 3.14159, 3.14159)
  }

  // ---------------------------------------------------------------------------
  // Tests: Option types
  // ---------------------------------------------------------------------------

  test("Option[String] Some roundtrips through binding") {
    roundtrip[Option[String], Option[String]]("echoOptionSome", Some("present"), Some("present"))
  }

  test("Option[String] None roundtrips through binding") {
    roundtrip[Option[String], Option[String]]("echoOptionNone", None, None)
  }

  // ---------------------------------------------------------------------------
  // Tests: List types
  // ---------------------------------------------------------------------------

  test("List[Int] non-empty roundtrips through binding") {
    roundtrip[List[Int], List[Int]]("echoList", List(1, 2, 3), List(1, 2, 3))
  }

  test("List[Int] empty roundtrips through binding") {
    roundtrip[List[Int], List[Int]]("echoListEmpty", Nil, Nil)
  }

  // ---------------------------------------------------------------------------
  // Tests: Nested case class
  // ---------------------------------------------------------------------------

  test("nested case class roundtrips through binding") {
    val input = Outer("root", DeepNested("child", List(10, 20)))
    roundtrip[Outer, Outer]("echoNested", input, input)
  }

  // ---------------------------------------------------------------------------
  // Tests: Multi-param method
  // ---------------------------------------------------------------------------

  test("multi-parameter method roundtrips through binding") {
    val b = binding("multiParam", broadDefn)
    for {
      payload <- liftEither(HostPayload.encode[(String, Int)](("hello", 42)))
      raw     <- FutureInterop.fromPromise(b.invoke(broadImpl, payload))
      decoded <- liftEither(HostPayload.decode[String](raw))
    } yield assert(decoded == "hello-42")
  }

  // ---------------------------------------------------------------------------
  // Tests: Unit return
  // ---------------------------------------------------------------------------

  test("Future[Unit] return roundtrips through binding") {
    val b = binding("asyncVoid", broadDefn)
    for {
      payload <- liftEither(HostPayload.encode[String]("ignored"))
      raw     <- FutureInterop.fromPromise(b.invoke(broadImpl, payload))
      decoded <- liftEither(HostPayload.decode[Unit](raw))
    } yield assert(decoded == ())
  }
}
