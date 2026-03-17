/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.otel

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

sealed trait ContextStorage[A] {
  def get(): A
  def set(value: A): Unit
  def scoped[B](value: A)(f: => B): B
}

object ContextStorage {

  val hasLoom: Boolean =
    try {
      Class.forName("java.lang.ScopedValue")
      true
    } catch {
      case _: ClassNotFoundException => false
    }

  val implementationName: String =
    if (hasLoom) "ScopedValue" else "ThreadLocal"

  def create[A](initial: A): ContextStorage[A] =
    if (hasLoom) new ScopedValueStorage[A](initial)
    else new ThreadLocalStorage[A](initial)

  private final class ThreadLocalStorage[A](initial: A) extends ContextStorage[A] {
    private val threadLocal: ThreadLocal[A] = new ThreadLocal[A] {
      override def initialValue(): A = initial
    }

    def get(): A = threadLocal.get()

    def set(value: A): Unit = threadLocal.set(value)

    def scoped[B](value: A)(f: => B): B = {
      val prev = threadLocal.get()
      threadLocal.set(value)
      try f
      finally threadLocal.set(prev)
    }
  }

  /**
   * ScopedValue-based storage using reflection for JDK 21+ compatibility.
   *
   * All access to java.lang.ScopedValue is via MethodHandle to avoid
   * compile-time JDK 21 requirement.
   *
   * ScopedValue does not support set() — context changes only via scoped(). To
   * provide get() outside a scoped block, we also maintain a ThreadLocal
   * fallback for set() calls and as default when no ScopedValue binding exists.
   */
  private final class ScopedValueStorage[A](initial: A) extends ContextStorage[A] {
    // Fallback ThreadLocal for set() support and default get()
    private val fallback: ThreadLocal[A] = new ThreadLocal[A] {
      override def initialValue(): A = initial
    }

    // ScopedValue instance (java.lang.ScopedValue)
    private val scopedValue: AnyRef = ScopedValueStorage.newInstance()

    // MethodHandles for ScopedValue operations
    private val getHandle: MethodHandle        = ScopedValueStorage.getHandle
    private val isBoundHandle: MethodHandle    = ScopedValueStorage.isBoundHandle
    private val whereHandle: MethodHandle      = ScopedValueStorage.whereHandle
    private val carrierRunHandle: MethodHandle = ScopedValueStorage.carrierRunHandle

    def get(): A = {
      val bound = isBoundHandle.invoke(scopedValue).asInstanceOf[Boolean]
      if (bound) getHandle.invoke(scopedValue).asInstanceOf[A]
      else fallback.get()
    }

    def set(value: A): Unit =
      fallback.set(value)

    def scoped[B](value: A)(f: => B): B = {
      // ScopedValue.where(scopedValue, value).run(() => { result = f })
      val carrier           = whereHandle.invoke(scopedValue, value.asInstanceOf[AnyRef])
      var result: B         = null.asInstanceOf[B]
      var thrown: Throwable = null
      carrierRunHandle.invoke(
        carrier,
        new Runnable {
          def run(): Unit =
            try result = f
            catch { case t: Throwable => thrown = t }
        }
      )
      if (thrown != null) throw thrown
      result
    }
  }

  private object ScopedValueStorage {
    private val lookup: MethodHandles.Lookup = MethodHandles.lookup()
    private val svClass: Class[_]            = Class.forName("java.lang.ScopedValue")

    // ScopedValue.newInstance() — static factory
    private val newInstanceHandle: MethodHandle = {
      val m = svClass.getMethod("newInstance")
      lookup.unreflect(m)
    }

    // ScopedValue.get() — instance method
    val getHandle: MethodHandle = {
      val m = svClass.getMethod("get")
      lookup.unreflect(m)
    }

    // ScopedValue.isBound() — instance method
    val isBoundHandle: MethodHandle = {
      val m = svClass.getMethod("isBound")
      lookup.unreflect(m)
    }

    // ScopedValue.where(ScopedValue, Object) — static method returning Carrier
    val whereHandle: MethodHandle = {
      val m = svClass.getMethod("where", svClass, classOf[Object])
      lookup.unreflect(m)
    }

    // Carrier.run(Runnable) — stable across JDK 21-25
    val carrierRunHandle: MethodHandle = {
      val carrierClass = Class.forName("java.lang.ScopedValue$Carrier")
      val m            = carrierClass.getMethod("run", classOf[Runnable])
      lookup.unreflect(m)
    }

    def newInstance(): AnyRef =
      newInstanceHandle.invoke().asInstanceOf[AnyRef]
  }
}
