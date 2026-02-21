package example.templates

import golem.*
import golem.runtime.annotations.agentImplementation
import golem.runtime.snapshot.SnapshotExports

import scala.annotation.unused
import scala.concurrent.Future
import scala.language.reflectiveCalls
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, DataView, Uint8Array}

@agentImplementation()
final class CounterImpl(@unused private val name: String) extends Counter {
  private var value: Int = 0

  override def increment(): Future[Int] =
    Future.successful {
      value += 1
      value
    }
}

@agentImplementation()
final class RpcClientImpl(@unused private val name: String) extends RpcClient {
  override def callCounter(counterId: String): Future[Int] =
    Counter.get(counterId).increment()
}

@agentImplementation()
final class TasksImpl(@unused private val name: String) extends Tasks {
  private var nextId: Int       = 1
  private var tasks: List[Task] = Nil

  override def createTask(request: CreateTaskRequest): Future[Task] =
    Future.successful {
      val task = Task(
        id = nextId,
        title = request.title,
        completed = false,
        createdAt = new js.Date().toISOString()
      )
      nextId += 1
      tasks = tasks :+ task
      task
    }

  override def getTasks(): Future[List[Task]] =
    Future.successful(tasks)

  override def completeTask(id: Int): Future[Option[Task]] =
    Future.successful {
      tasks.find(_.id == id).map { t =>
        val updated = t.copy(completed = true)
        tasks = tasks.map(curr => if (curr.id == id) updated else curr)
        updated
      }
    }
}

@agentImplementation()
final class SnapshotCounterImpl(@unused private val name: String) extends SnapshotCounter {
  private var value: Int = 0

  // Install component-level snapshot hooks, closing over this agent instance.
  SnapshotExports.configure(
    save = () => Future.successful(encodeU32(value)),
    load = bytes =>
      Future.successful {
        value = decodeU32(bytes)
        ()
      }
  )

  override def increment(): Future[Int] =
    Future.successful {
      value += 1
      value
    }

  private def encodeU32(i: Int): Uint8Array = {
    val buf  = new ArrayBuffer(4)
    val view = new DataView(buf)
    view.setUint32(0, i, false)
    new Uint8Array(buf)
  }

  private def decodeU32(bytes: Uint8Array): Int = {
    val view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
    view.getUint32(0, false).toInt
  }
}

@agentImplementation()
final class ApprovalWorkflowImpl(private val workflowId: String) extends ApprovalWorkflow {
  private var promiseId: Option[HostApi.PromiseId] = None
  private var decided: Option[String]              = None

  override def begin(): Future[String] =
    Future.successful {
      promiseId match {
        case Some(_) =>
          s"workflow $workflowId already pending"
        case None =>
          promiseId = Some(HostApi.createPromise())
          decided = None
          s"workflow $workflowId pending"
      }
    }

  override def awaitOutcome(): Future[String] =
    decided match {
      case Some(value) =>
        Future.successful(value)
      case None =>
        promiseId match {
          case None =>
            Future.successful("no pending approval")
          case Some(p) =>
            HostApi.awaitPromise(p).map { bytes =>
              val v = Utf8.decodeBytes(bytes)
              decided = Some(v)
              v
            }
        }
    }

  override def complete(decision: String): Future[Boolean] =
    promiseId match {
      case None =>
        Future.successful(false)
      case Some(p) =>
        Future.successful {
          decided = Some(decision)
          val ok = HostApi.completePromise(p, Utf8.encodeBytes(decision))
          ok
        }
    }
}

@agentImplementation()
final class HumanAgentImpl(private val username: String) extends HumanAgent {
  override def decide(workflowId: String, decision: String): Future[String] =
    ApprovalWorkflow
      .get(workflowId)
      .complete(decision)
      .map { ok =>
        if (ok) s"$username decided $decision for $workflowId"
        else s"$username failed to decide for $workflowId"
      }
}
