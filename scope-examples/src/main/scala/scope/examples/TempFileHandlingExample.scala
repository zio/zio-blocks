package scope.examples

import zio.blocks.scope._

/**
 * Demonstrates `scope.defer(...)` for registering manual cleanup actions.
 *
 * This example shows how to create temporary files during processing and ensure
 * they are deleted when the scope exits—even if processing fails. Deferred
 * cleanup actions run in LIFO (last-in-first-out) order.
 */

/** Represents a temporary file with basic read/write operations. */
case class TempFile(path: String) {
  private var content: String = ""

  def write(data: String): Unit = content = data
  def read(): String            = content
  def delete(): Boolean         = { println(s"  Deleting: $path"); true }
}

/** Result of processing temporary files. */
case class ProcessingResult(processedCount: Int, totalBytes: Long, errors: List[String])

/** Processes a list of temporary files and aggregates results. */
object FileProcessor {
  def process(files: List[TempFile]): ProcessingResult = {
    val totalBytes = files.map(_.read().length.toLong).sum
    ProcessingResult(processedCount = files.size, totalBytes = totalBytes, errors = Nil)
  }
}

@main def tempFileHandlingExample(): Unit = {
  println("=== Temp File Handling Example ===\n")
  println("Demonstrating scope.defer() for manual cleanup registration.\n")

  val result = Scope.global.scoped { scope =>
    // Create temp files and register cleanup via defer.
    // Cleanup runs in LIFO order: file3, file2, file1.

    val file1 = createTempFile(scope, "/tmp/data-001.tmp", "First file content")
    val file2 = createTempFile(scope, "/tmp/data-002.tmp", "Second file - more data here")
    val file3 = createTempFile(scope, "/tmp/data-003.tmp", "Third file with the most content of all")

    println("\nProcessing files...")
    val processingResult = FileProcessor.process(List(file1, file2, file3))
    println(s"Processed ${processingResult.processedCount} files, ${processingResult.totalBytes} bytes\n")

    println("Exiting scope - cleanup runs in LIFO order:")
    processingResult
  }

  println(s"\nFinal result: $result")
}

/**
 * Creates a temporary file and registers its cleanup with the scope.
 *
 * The cleanup action is registered via `scope.defer(...)`, ensuring the file is
 * deleted when the scope closes—regardless of whether processing succeeds.
 *
 * @param scope
 *   the scope to register cleanup with
 * @param path
 *   the file path
 * @param content
 *   initial content to write
 * @return
 *   the created TempFile
 */
private def createTempFile(scope: Scope[?, ?], path: String, content: String): TempFile = {
  val file = TempFile(path)
  file.write(content)
  println(s"Created: $path (${content.length} bytes)")

  // Register cleanup - will run when scope exits, in LIFO order
  scope.defer {
    file.delete()
  }

  file
}
