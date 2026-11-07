package readandwriteyaml

import zio.blocks.schema._
import zio.blocks.schema.yaml._

/**
 * How to Read and Write YAML Documents — Write a CI Workflow as YAML
 *
 * Writes a GitHub Actions workflow from case classes with the derived codec,
 * then reads the text back and compares it with the original value.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwriteyaml.Step3WriteWorkflowExample"
 */
object Step3WriteWorkflowExample {

  case class Branches(branches: List[String])

  object Branches {
    implicit val schema: Schema[Branches] = Schema.derived
  }

  case class Triggers(push: Branches, pullRequest: Branches)

  object Triggers {
    implicit val schema: Schema[Triggers] = Schema.derived
  }

  case class Step(name: String, uses: Option[String] = None, run: Option[String] = None)

  object Step {
    implicit val schema: Schema[Step] = Schema.derived
  }

  case class Job(runsOn: String, timeoutMinutes: Int, steps: List[Step])

  object Job {
    implicit val schema: Schema[Job] = Schema.derived
  }

  case class Workflow(name: String, on: Triggers, jobs: Map[String, Job])

  object Workflow {
    implicit val schema: Schema[Workflow] = Schema.derived
  }

  val workflowCodec: YamlCodec[Workflow] = Workflow.schema.derive(YamlFormat)

  val ciWorkflow: Workflow = Workflow(
    name = "CI",
    on = Triggers(push = Branches(List("main")), pullRequest = Branches(List("main"))),
    jobs = Map(
      "test" -> Job(
        runsOn = "ubuntu-latest",
        timeoutMinutes = 30,
        steps = List(
          Step("Check out", uses = Some("actions/checkout@v4")),
          Step("Set up sbt", uses = Some("sbt/setup-sbt@v1")),
          Step("Test", run = Some("sbt +test"))
        )
      )
    )
  )

  def main(args: Array[String]): Unit = {
    val text = workflowCodec.encodeToString(ciWorkflow)
    println(text)
    println(workflowCodec.decode(text) == Right(ciWorkflow))
  }
}
