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

package zio.blocks.config

import zio.test._

object SensitiveSpec extends ZIOSpecDefault {

  def spec = suite("Sensitive")(
    test("flags every documented marker") {
      val sensitive = List(
        "db.secret",
        "db.password",
        "svc.passwd",
        "db.pwd",
        "user.passphrase",
        "service.auth",
        "auth.token",
        "api.token",
        "x.apiKey",
        "x.api_key",
        "svc.accessKey",
        "svc.access_key",
        "m.privateKey",
        "m.private_key",
        "svc.credential",
        "svc.credentials"
      )
      assertTrue(sensitive.forall(Sensitive.isSensitive))
    },
    test("leaves plain keys visible") {
      val plain = List("app.port", "host", "service.timeout", "db.name")
      assertTrue(plain.forall(p => !Sensitive.isSensitive(p)))
    },
    test("dash-separated keys match after normalization") {
      assertTrue(Sensitive.isSensitive("db-access-key"))
    },
    test("documents the substring trade-off: tokenizer redacts") {
      // Substring matching errs toward redaction: an ordinary word containing
      // a marker redacts too. Pinned here so the trade-off stays deliberate.
      assertTrue(Sensitive.isSensitive("tokenizer"))
    },
    test("new markers redact through ConfigError messages") {
      val error = ConfigError.InvalidValue("service.auth", "hunter2", "Int", "env")
      assertTrue(
        error.message.contains("<secret>"),
        !error.message.contains("hunter2")
      )
    }
  )
}
