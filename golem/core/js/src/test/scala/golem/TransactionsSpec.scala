package golem

import org.scalatest.funsuite.AnyFunSuite

final class TransactionsSpec extends AnyFunSuite {
  import Transactions._

  test("Operation.apply creates executable operation") {
    val op = Operation[Int, String, String](
      run = i => Right(s"result-$i"),
      compensateFn = (_, _) => Right(())
    )
    assert(op.execute(42) == Right("result-42"))
  }

  test("Operation.execute returns Left on failure") {
    val op = Operation[Int, String, String](
      run = _ => Left("boom"),
      compensateFn = (_, _) => Right(())
    )
    assert(op.execute(1) == Left("boom"))
  }

  test("Operation.compensate returns Left on failure") {
    val op = Operation[Int, String, String](
      run = _ => Right("ok"),
      compensateFn = (_, _) => Left("comp-fail")
    )
    assert(op.compensate(1, "ok") == Left("comp-fail"))
  }

  test("Transactions.operation convenience creates Operation") {
    val op = Transactions.operation[Int, Int, String](in => Right(in * 2))((_, _) => Right(()))
    assert(op.execute(5) == Right(10))
  }

  test("TransactionFailure.FailedAndRolledBackCompletely wraps error") {
    val failure: TransactionFailure[String] =
      TransactionFailure.FailedAndRolledBackCompletely("err")
    failure match {
      case TransactionFailure.FailedAndRolledBackCompletely(e) => assert(e == "err")
      case _                                                   => fail("wrong variant")
    }
  }

  test("TransactionFailure.FailedAndRolledBackPartially wraps both errors") {
    val failure: TransactionFailure[String] =
      TransactionFailure.FailedAndRolledBackPartially("err", "comp-err")
    failure match {
      case TransactionFailure.FailedAndRolledBackPartially(e, ce) =>
        assert(e == "err")
        assert(ce == "comp-err")
      case _ => fail("wrong variant")
    }
  }

  test("TransactionFailure pattern match is exhaustive") {
    def describe[E](f: TransactionFailure[E]): String = f match {
      case TransactionFailure.FailedAndRolledBackCompletely(e)    => s"complete($e)"
      case TransactionFailure.FailedAndRolledBackPartially(e, ce) => s"partial($e, $ce)"
    }
    val f1 = TransactionFailure.FailedAndRolledBackCompletely("a")
    val f2 = TransactionFailure.FailedAndRolledBackPartially("a", "b")
    assert(describe(f1) == "complete(a)")
    assert(describe(f2) == "partial(a, b)")
  }

  test("FallibleTransaction.execute with all-successful operations") {
    val tx = new FallibleTransaction[String]
    val op = Operation[Int, Int, String](
      run = i => Right(i + 1),
      compensateFn = (_, _) => Right(())
    )
    assert(tx.execute(op, 1) == Right(2))
    assert(tx.execute(op, 10) == Right(11))
  }

  test("FallibleTransaction.execute propagates failure") {
    val tx     = new FallibleTransaction[String]
    val failOp = Operation[Int, Int, String](
      run = _ => Left("fail"),
      compensateFn = (_, _) => Right(())
    )
    assert(tx.execute(failOp, 1) == Left("fail"))
  }

  test("FallibleTransaction.onFailure runs compensations and returns complete rollback") {
    var compensated = false
    val tx          = new FallibleTransaction[String]
    val op          = Operation[Int, Int, String](
      run = i => Right(i),
      compensateFn = (_, _) => { compensated = true; Right(()) }
    )
    tx.execute(op, 1)
    val result = tx.onFailure("err")
    assert(compensated)
    assert(result.isInstanceOf[TransactionFailure.FailedAndRolledBackCompletely[?]])
  }

  test("FallibleTransaction.onFailure reports partial rollback on compensation failure") {
    val tx = new FallibleTransaction[String]
    val op = Operation[Int, Int, String](
      run = i => Right(i),
      compensateFn = (_, _) => Left("comp-fail")
    )
    tx.execute(op, 1)
    val result = tx.onFailure("err")
    result match {
      case TransactionFailure.FailedAndRolledBackPartially(e, ce) =>
        assert(e == "err")
        assert(ce == "comp-fail")
      case _ => fail("expected partial rollback")
    }
  }

  // ---------------------------------------------------------------------------
  // FallibleTransaction: multiple operations with mixed results
  // ---------------------------------------------------------------------------

  test("FallibleTransaction.onFailure compensates in reverse order") {
    var order = List.empty[Int]
    val tx    = new FallibleTransaction[String]
    val op1   = Operation[Int, Int, String](
      run = i => Right(i),
      compensateFn = (_, _) => { order = 1 :: order; Right(()) }
    )
    val op2 = Operation[Int, Int, String](
      run = i => Right(i),
      compensateFn = (_, _) => { order = 2 :: order; Right(()) }
    )
    val op3 = Operation[Int, Int, String](
      run = i => Right(i),
      compensateFn = (_, _) => { order = 3 :: order; Right(()) }
    )
    tx.execute(op1, 10)
    tx.execute(op2, 20)
    tx.execute(op3, 30)
    tx.onFailure("err")
    assert(order == List(1, 2, 3))
  }

  test("FallibleTransaction with no operations returns complete rollback") {
    val tx     = new FallibleTransaction[String]
    val result = tx.onFailure("err")
    assert(result.isInstanceOf[TransactionFailure.FailedAndRolledBackCompletely[?]])
  }

  // ---------------------------------------------------------------------------
  // InfallibleTransaction: pure logic (no host calls)
  // ---------------------------------------------------------------------------

  test("InfallibleTransaction.execute returns result on success") {
    val tx = new InfallibleTransaction
    val op = Operation[Int, Int, String](
      run = i => Right(i * 2),
      compensateFn = (_, _) => Right(())
    )
    assert(tx.execute(op, 5) == 10)
  }

  test("InfallibleTransaction.execute chains multiple successful operations") {
    val tx = new InfallibleTransaction
    val op = Operation[Int, Int, String](
      run = i => Right(i + 1),
      compensateFn = (_, _) => Right(())
    )
    val r1 = tx.execute(op, 1)
    val r2 = tx.execute(op, r1)
    val r3 = tx.execute(op, r2)
    assert(r3 == 4)
  }

  test("InfallibleTransaction.execute on failure runs compensations and throws") {
    var compensated = false
    val tx          = new InfallibleTransaction
    val successOp   = Operation[Int, Int, String](
      run = i => Right(i),
      compensateFn = (_, _) => { compensated = true; Right(()) }
    )
    tx.execute(successOp, 1)
    val failOp = Operation[Int, Int, String](
      run = _ => Left("fail"),
      compensateFn = (_, _) => Right(())
    )
    intercept[Throwable] {
      tx.execute(failOp, 2)
    }
    assert(compensated)
  }

  // ---------------------------------------------------------------------------
  // Operation: various input/output/error types
  // ---------------------------------------------------------------------------

  test("Operation with Unit input") {
    val op = Operation[Unit, String, String](
      run = _ => Right("done"),
      compensateFn = (_, _) => Right(())
    )
    assert(op.execute(()) == Right("done"))
  }

  test("Operation with complex error type") {
    final case class AppError(code: Int, message: String)
    val op = Operation[String, Int, AppError](
      run = _ => Left(AppError(404, "not found")),
      compensateFn = (_, _) => Right(())
    )
    op.execute("input") match {
      case Left(AppError(code, msg)) =>
        assert(code == 404)
        assert(msg == "not found")
      case _ => fail("expected Left")
    }
  }

  test("Operation.compensate succeeds") {
    val op = Operation[Int, String, String](
      run = _ => Right("ok"),
      compensateFn = (input, output) => Right(())
    )
    assert(op.compensate(42, "ok") == Right(()))
  }
}
