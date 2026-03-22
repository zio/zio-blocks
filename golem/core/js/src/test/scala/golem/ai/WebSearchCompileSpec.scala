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

package golem.ai

import org.scalatest.funsuite.AnyFunSuite

final class WebSearchCompileSpec extends AnyFunSuite {
  import WebSearch._

  test("SafeSearchLevel — all 3 variants and fromTag roundtrip") {
    val levels = List(SafeSearchLevel.Off, SafeSearchLevel.Medium, SafeSearchLevel.High)
    assert(levels.size == 3)
    levels.foreach(l => assert(SafeSearchLevel.fromTag(l.tag) eq l))
  }

  test("TimeRange — all 4 variants and fromTag roundtrip") {
    val ranges = List(TimeRange.Day, TimeRange.Week, TimeRange.Month, TimeRange.Year)
    assert(ranges.size == 4)
    ranges.foreach(r => assert(TimeRange.fromTag(r.tag) eq r))
  }

  test("SearchError — all 4 variants") {
    val errors: List[SearchError] = List(
      SearchError.InvalidQuery,
      SearchError.RateLimited(100),
      SearchError.UnsupportedFeature("no images"),
      SearchError.BackendError("timeout")
    )
    assert(errors.size == 4)
  }

  test("SearchError pattern match compiles") {
    def describe(e: SearchError): String = e match {
      case SearchError.InvalidQuery            => "invalid"
      case SearchError.RateLimited(limit)      => s"rate($limit)"
      case SearchError.UnsupportedFeature(msg) => s"unsupported($msg)"
      case SearchError.BackendError(msg)       => s"backend($msg)"
    }
    assert(describe(SearchError.RateLimited(50)) == "rate(50)")
  }

  test("SearchResult construction with all fields") {
    val result = SearchResult(
      title = "Example",
      url = "https://example.com",
      snippet = "An example page",
      displayUrl = Some("example.com"),
      source = None,
      score = Some(0.95),
      htmlSnippet = None,
      datePublished = Some("2025-01-01"),
      images = Some(List(ImageResult("http://img.png", Some("pic")))),
      contentChunks = None
    )
    assert(result.title == "Example")
    assert(result.score.contains(0.95))
    assert(result.images.exists(_.nonEmpty))
  }

  test("ImageResult construction") {
    val img = ImageResult(url = "https://img.png", description = Some("a picture"))
    assert(img.description.contains("a picture"))
  }

  test("RateLimitInfo construction") {
    val info = RateLimitInfo(limit = 100, remaining = 50, resetTimestamp = BigInt(1700000000))
    assert(info.limit == 100)
    assert(info.remaining == 50)
  }

  test("SearchParams construction with all fields") {
    val params = SearchParams(
      query = "scala programming",
      maxResults = Some(10),
      safeSearch = Some(SafeSearchLevel.Medium),
      language = Some("en"),
      region = Some("US"),
      timeRange = Some(TimeRange.Month),
      excludeDomains = Some(List("spam.com")),
      includeDomains = Some(List("docs.scala-lang.org")),
      includeImages = Some(true),
      includeHtml = None,
      advancedAnswer = None
    )
    assert(params.query == "scala programming")
    assert(params.maxResults.contains(10))
  }

  test("SearchMetadata construction with all fields") {
    val meta = SearchMetadata(
      query = "test",
      totalResults = Some(BigInt(1000)),
      searchTimeMs = Some(42.0),
      safeSearch = Some(SafeSearchLevel.Off),
      language = Some("en"),
      region = Some("US"),
      nextPageToken = None,
      rateLimits = Some(RateLimitInfo(100, 99, BigInt(0))),
      currentPage = 1
    )
    assert(meta.totalResults.contains(BigInt(1000)))
    assert(meta.currentPage == 1)
  }
}
