package diffandpatch

import zio.blocks.schema._
import zio.blocks.schema.patch._
import Books._

/**
 * Diff and Patch: Keep an Edit History, Replay It and Undo
 *
 * Records two edits with a forward and a backward patch each, stores the
 * history as JSON, replays it from the first edition, undoes the last edit, and
 * squashes the history into one patch.
 *
 * Run with: cd schema-examples && sbt "runMain diffandpatch.EditHistoryExample"
 */
object EditHistoryExample extends App {

  def record(author: String, before: Book, after: Book): Edit =
    Edit(author, Book.schema.diff(before, after).dynamicPatch, Book.schema.diff(after, before).dynamicPatch)

  val history: List[Edit] = List(
    record("alice", zionomicon, secondEdition),
    record("bob", secondEdition, withSubtitle)
  )

  val historyCodec        = Schema[List[Edit]].jsonCodec
  val historyText: String = historyCodec.encodeToString(history)
  println(historyText)
  println(historyCodec.decode(historyText) == Right(history))

  def replay(base: Book, edits: List[Edit]): Either[SchemaError, Book] =
    edits.foldLeft[Either[SchemaError, Book]](Right(base)) { (book, edit) =>
      book.flatMap(PatchHelpers.applyStored(Book.schema, _, edit.forward))
    }

  println(replay(zionomicon, history))
  println(replay(zionomicon, history.take(1)))

  // Undo the last edit.
  println(PatchHelpers.applyStored(Book.schema, withSubtitle, history.last.backward))

  // A history replays only from the base it was recorded against.
  println(replay(zionomicon.copy(title = "ZIO"), history))

  // Squash the history into one patch.
  println(history.map(_.forward).foldLeft(DynamicPatch.empty)(_ ++ _))
}
