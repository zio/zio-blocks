package golem.runtime

import scala.concurrent.Future

/**
 * Payload returned by a snapshot save operation.
 *
 * @param bytes
 *   The serialized state
 * @param mimeType
 *   The MIME type of the serialized data (e.g. "application/json" or
 *   "application/octet-stream")
 */
final case class SnapshotPayload(bytes: Array[Byte], mimeType: String)

/**
 * Snapshot save/load handlers for an agent instance.
 *
 * @tparam Instance
 *   The agent trait type
 * @param save
 *   Serializes the current agent state into a [[SnapshotPayload]]
 * @param load
 *   Deserializes bytes into state and applies them to the agent instance. Takes
 *   the current instance and snapshot bytes, returns the (possibly new)
 *   instance to use going forward.
 */
final case class SnapshotHandlers[Instance](
  save: Instance => Future[SnapshotPayload],
  load: (Instance, Array[Byte]) => Future[Instance]
)

object SnapshotHandlers {

  /**
   * Wraps a raw `Instance => Future[Array[Byte]]` save function into the
   * `Instance => Future[SnapshotPayload]` form expected by
   * [[SnapshotHandlers]].
   */
  def wrapSave[Instance](
    raw: Instance => Future[Array[Byte]]
  ): Instance => Future[SnapshotPayload] =
    (instance: Instance) =>
      raw(instance).map(bytes => SnapshotPayload(bytes, "application/octet-stream"))(
        scala.concurrent.ExecutionContext.parasitic
      )

  /**
   * Wraps a raw `(Instance, Array[Byte]) => Future[Unit]` load function into
   * the `(Instance, Array[Byte]) => Future[Instance]` form expected by
   * [[SnapshotHandlers]].
   */
  def wrapLoad[Instance](
    raw: (Instance, Array[Byte]) => Future[Unit]
  ): (Instance, Array[Byte]) => Future[Instance] =
    (instance: Instance, bytes: Array[Byte]) =>
      raw(instance, bytes).map(_ => instance)(scala.concurrent.ExecutionContext.parasitic)
}
