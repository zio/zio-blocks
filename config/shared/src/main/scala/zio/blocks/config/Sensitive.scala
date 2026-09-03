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

/**
 * Decides whether a config/flag key path names a secret, so error messages,
 * provenance dumps, and exporter headers can redact the value.
 *
 * Matching is case-insensitive substring matching over the path with `-`
 * normalized to `_` (so `apiKey` lowercases to `apikey` and matches). The
 * marker list below errs toward redaction:
 *
 *   - Covered: secret, password, passwd, pwd, passphrase, auth, token,
 *     apikey/api_key, accesskey/access_key, privatekey/private_key,
 *     credential(s).
 *   - Known false positives: ordinary words containing a marker (e.g.
 *     `tokenizer` contains `token`) redact too. That is the safe direction — an
 *     error string shows `<secret>` instead of a value that might be sensitive.
 *
 * `Secret` itself always redacts; this list only guards paths that carry raw
 * strings (parse errors, dumps, headers).
 */
private[config] object Sensitive {

  private val markers = List(
    "secret",
    "password",
    "passwd",
    "pwd",
    "passphrase",
    "auth",
    "token",
    "apikey",
    "api_key",
    "accesskey",
    "access_key",
    "privatekey",
    "private_key",
    "credential",
    "credentials"
  )

  def isSensitive(path: String): Boolean = {
    val normalized = path.toLowerCase.replace('-', '_')
    markers.exists(normalized.contains)
  }
}
