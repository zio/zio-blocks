package golem

import org.scalatest.funsuite.AnyFunSuite

final class GuardsCompileSpec extends AnyFunSuite {

  test("Guard subtypes compile and extend AutoCloseable") {
    val _: AutoCloseable = null.asInstanceOf[Guards.PersistenceLevelGuard]
    val _: AutoCloseable = null.asInstanceOf[Guards.RetryPolicyGuard]
    val _: AutoCloseable = null.asInstanceOf[Guards.IdempotenceModeGuard]
    val _: AutoCloseable = null.asInstanceOf[Guards.AtomicOperationGuard]
    assert(true)
  }

  test("all Guard subtypes extend sealed Guard base") {
    val _: Guards.Guard = null.asInstanceOf[Guards.PersistenceLevelGuard]
    val _: Guards.Guard = null.asInstanceOf[Guards.RetryPolicyGuard]
    val _: Guards.Guard = null.asInstanceOf[Guards.IdempotenceModeGuard]
    val _: Guards.Guard = null.asInstanceOf[Guards.AtomicOperationGuard]
    assert(true)
  }

  test("PersistenceLevelGuard type is distinct") {
    assert(!classOf[Guards.PersistenceLevelGuard].isAssignableFrom(classOf[Guards.RetryPolicyGuard]))
  }

  test("RetryPolicyGuard type is distinct") {
    assert(!classOf[Guards.RetryPolicyGuard].isAssignableFrom(classOf[Guards.AtomicOperationGuard]))
  }

  test("IdempotenceModeGuard type is distinct") {
    assert(!classOf[Guards.IdempotenceModeGuard].isAssignableFrom(classOf[Guards.PersistenceLevelGuard]))
  }

  test("AtomicOperationGuard type is distinct") {
    assert(!classOf[Guards.AtomicOperationGuard].isAssignableFrom(classOf[Guards.IdempotenceModeGuard]))
  }
}
