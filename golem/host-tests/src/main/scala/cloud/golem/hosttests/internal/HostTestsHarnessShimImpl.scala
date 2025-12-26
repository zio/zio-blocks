package cloud.golem.hosttests.internal

import cloud.golem.hosttests.HostTestSuite
import cloud.golem.runtime.util.FutureInterop

import scala.concurrent.Future
import scala.scalajs.js
import scala.util.control.NonFatal

/**
 * Internal shim implementation used by the plugin-generated Scala agents shim.
 *
 * The TS bridge calls this via the generated shim object; we keep all JS-facing
 * decoding localized here.
 */
final class HostTestsHarnessShimImpl() {
  def runtests(input: js.Dynamic): Future[String] = {
    val selection            = selectionFromInput(input)
    val (reportJson, failed) = render(selection)
    if (failed) FutureInterop.failed(reportJson)
    else Future.successful(reportJson)
  }

  private def render(selection: Option[Set[String]]): (String, Boolean) =
    try {
      val report = HostTestSuite.run(selection)
      val json   = js.JSON.stringify(report.toJs)
      (json, report.failed > 0)
    } catch {
      case NonFatal(err) =>
        val json = encodeUnexpectedError(err.getMessage)
        (json, true)
    }

  private def encodeUnexpectedError(message: String): String = {
    val outcome = HostTestSuite.TestOutcome(
      name = "harness-error",
      success = false,
      durationMs = 0.0,
      detail = Some(message)
    )
    val report = HostTestSuite.TestReport(
      total = 1,
      passed = 0,
      failed = 1,
      durationMs = 0.0,
      outcomes = List(outcome)
    )
    js.JSON.stringify(report.toJs)
  }

  private def selectionFromInput(input: js.Dynamic): Option[Set[String]] =
    if (js.isUndefined(input) || input == null) None
    else {
      val testsField = input.selectDynamic("tests")
      if (js.isUndefined(testsField) || testsField == null) None
      else {
        val arr = testsField.asInstanceOf[js.Array[String]]
        Some(arr.toSet)
      }
    }
}
