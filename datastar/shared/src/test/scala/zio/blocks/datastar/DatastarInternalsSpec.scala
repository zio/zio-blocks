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

package zio.http.datastar

import zio.blocks.chunk.Chunk
import zio.http.html.{CssSelector, Dom, ToJs, _}
import zio.blocks.maybe.Maybe
import zio.blocks.schema.Schema
import zio.test._

object DatastarInternalsSpec extends ZIOSpecDefault {

  private object attrs extends DatastarAttributes

  private def renderAttr(attribute: Dom.Attribute): String =
    div(attribute).render

  def spec = suite("DatastarInternals")(
    suite("Signal internals")(
      test("isValidName accepts dotted JavaScript identifiers") {
        assertTrue(Signal.isValidName("user.profile.count"))
      },
      test("isValidName rejects invalid forms") {
        assertTrue(!Signal.isValidName("")) &&
        assertTrue(!Signal.isValidName("foo__bar")) &&
        assertTrue(!Signal.isValidName("foo..bar")) &&
        assertTrue(!Signal.isValidName("1count")) &&
        assertTrue(!Signal.isValidName("count-value"))
      },
      test("requireValidName throws with detailed message") {
        val thrown = scala.util.Try(Signal.requireValidName("bad-name")).failed.toOption
        assertTrue(thrown.exists(_.getMessage == Signal.invalidNameMessage("bad-name")))
      },
      test("dynamic accepts valid names and ref stringifies correctly") {
        val signal = Signal.dynamic[Int]("profile.count")
        assertTrue(signal.name == "profile.count") &&
        assertTrue(signal.ref.signalName == "profile.count") &&
        assertTrue(signal.ref.value == "$profile.count") &&
        assertTrue(signal.ref.toString == "$profile.count")
      },
      test("DatastarRef ToJs renders raw reference") {
        val ref = Signal[Int]("count").ref
        assertTrue(ToJs[DatastarRef].toJs(ref) == "$count")
      }
    ),
    suite("ToDatastarExpr")(
      test("reuses ToJs instances for Js, Signal, SignalUpdate, and DatastarRef") {
        val signal = Signal[Int]("count")
        val update = signal := 42
        val ref    = signal.ref
        assertTrue(ToDatastarExpr[Js].toDatastarExpr(js"count + 1") == "count + 1") &&
        assertTrue(ToDatastarExpr[Signal[Int]].toDatastarExpr(signal) == "$count") &&
        assertTrue(ToDatastarExpr[SignalUpdate[Int]].toDatastarExpr(update) == "{\"count\": 42}") &&
        assertTrue(ToDatastarExpr[DatastarRef].toDatastarExpr(ref) == "$count")
      },
      test("raw String is rejected in Datastar expression positions") {
        typeCheck("""
          import zio.http.datastar._
          ToDatastarExpr[String]
        """).map { result =>
          assertTrue(result.isLeft) &&
          assertTrue(result.swap.toOption.get.contains("No ToDatastarExpr instance found for type String"))
        }
      }
    ),
    suite("DatastarStringEscape")(
      test("quotedString escapes control and HTML-sensitive characters") {
        val value = "\"\\\b\f\n\r\t<>&\u2028\u2029\u0001"
        assertTrue(
          DatastarStringEscape.quotedString(value) ==
            "\"\\\"\\\\\\b\\f\\n\\r\\t\\u003c\\u003e\\u0026\\u2028\\u2029\\u0001\""
        )
      },
      test("appendQuotedString appends to an existing builder") {
        val sb = new java.lang.StringBuilder("prefix:")
        DatastarStringEscape.appendQuotedString(sb, "a<b")
        assertTrue(sb.toString == "prefix:\"a\\u003cb\"")
      }
    ),
    suite("EventModifier")(
      test("render covers all event modifier variants") {
        assertTrue(EventModifier.Debounce(10, false).render == "__debounce.10ms") &&
        assertTrue(EventModifier.Debounce(10, true).render == "__debounce.10ms.leading") &&
        assertTrue(EventModifier.Throttle(20, false).render == "__throttle.20ms") &&
        assertTrue(EventModifier.Throttle(20, true).render == "__throttle.20ms.leading") &&
        assertTrue(EventModifier.Delay(30).render == "__delay.30ms") &&
        assertTrue(EventModifier.Once.render == "__once") &&
        assertTrue(EventModifier.Passive.render == "__passive") &&
        assertTrue(EventModifier.Capture.render == "__capture") &&
        assertTrue(EventModifier.Stop.render == "__stop") &&
        assertTrue(EventModifier.Prevent.render == "__prevent") &&
        assertTrue(EventModifier.Outside.render == "__outside") &&
        assertTrue(EventModifier.Window.render == "__window") &&
        assertTrue(EventModifier.Document.render == "__document") &&
        assertTrue(EventModifier.ViewTransition.render == "__viewTransition") &&
        assertTrue(EventModifier.And(EventModifier.Once, EventModifier.Prevent).render == "__once__prevent")
      },
      test("normalize flattens nested modifiers and keeps effective ones") {
        val existing = Maybe.present(
          EventModifier.And(
            EventModifier.And(EventModifier.Window, EventModifier.Debounce(10, false)),
            EventModifier.Prevent
          )
        )
        val normalized = EventModifier.normalize(existing, EventModifier.Document)
        assertTrue(normalized.map(_.render).contains("__debounce.10ms__prevent__document"))
      },
      test("normalize deduplicates flags and replaces timing modifiers") {
        val normalized = EventModifier
          .normalize(Maybe.absent, EventModifier.Once)
          .flatMap(m => EventModifier.normalize(Maybe.present(m), EventModifier.Once))
          .flatMap(m => EventModifier.normalize(Maybe.present(m), EventModifier.Delay(1)))
          .flatMap(m => EventModifier.normalize(Maybe.present(m), EventModifier.Delay(2)))
          .flatMap(m => EventModifier.normalize(Maybe.present(m), EventModifier.Throttle(3, false)))
        assertTrue(normalized.map(_.render).contains("__once__delay.2ms__throttle.3ms"))
      },
      test("normalize handles single and replacement branches for global target and throttle") {
        val single   = EventModifier.normalize(Maybe.absent, EventModifier.Window)
        val replaced = EventModifier
          .normalize(
            Maybe.present(EventModifier.And(EventModifier.Document, EventModifier.Prevent)),
            EventModifier.Window
          )
          .flatMap(m => EventModifier.normalize(Maybe.present(m), EventModifier.Throttle(1, false)))
          .flatMap(m => EventModifier.normalize(Maybe.present(m), EventModifier.Throttle(2, true)))
        assertTrue(single.contains(EventModifier.Window)) &&
        assertTrue(replaced.map(_.render).contains("__prevent__window__throttle.2ms.leading"))
      }
    ),
    suite("InitModifier")(
      test("render and normalize cover delay, viewTransition, and composition") {
        val normalized = InitModifier
          .normalize(Maybe.absent, InitModifier.Delay(100))
          .flatMap(m => InitModifier.normalize(Maybe.present(m), InitModifier.ViewTransition))
          .flatMap(m => InitModifier.normalize(Maybe.present(m), InitModifier.Delay(200)))
        assertTrue(InitModifier.Delay(1).render == "__delay.1ms") &&
        assertTrue(InitModifier.ViewTransition.render == "__viewTransition") &&
        assertTrue(
          InitModifier.And(InitModifier.Delay(1), InitModifier.ViewTransition).render == "__delay.1ms__viewTransition"
        ) &&
        assertTrue(
          normalized.map(_.render).contains("__viewTransition__delay.200ms") || normalized
            .map(_.render)
            .contains("__delay.200ms__viewTransition")
        )
      },
      test("normalize returns a single modifier when no composition is needed") {
        assertTrue(
          InitModifier.normalize(Maybe.absent, InitModifier.ViewTransition).contains(InitModifier.ViewTransition)
        )
      }
    ),
    suite("IntersectModifier")(
      test("render covers all intersect modifier variants") {
        assertTrue(IntersectModifier.Once.render == "__once") &&
        assertTrue(IntersectModifier.Half.render == "__half") &&
        assertTrue(IntersectModifier.Full.render == "__full") &&
        assertTrue(IntersectModifier.Exit.render == "__exit") &&
        assertTrue(IntersectModifier.Threshold(0.25).render == "__threshold.0.25") &&
        assertTrue(IntersectModifier.Delay(10).render == "__delay.10ms") &&
        assertTrue(IntersectModifier.Debounce(20).render == "__debounce.20ms") &&
        assertTrue(IntersectModifier.Throttle(30).render == "__throttle.30ms") &&
        assertTrue(IntersectModifier.ViewTransition.render == "__viewTransition")
      },
      test("normalize replaces conflicting visibility and timing modifiers") {
        val normalized = IntersectModifier
          .normalize(Maybe.absent, IntersectModifier.Half)
          .flatMap(m => IntersectModifier.normalize(Maybe.present(m), IntersectModifier.Threshold(0.75)))
          .flatMap(m => IntersectModifier.normalize(Maybe.present(m), IntersectModifier.Full))
          .flatMap(m => IntersectModifier.normalize(Maybe.present(m), IntersectModifier.Delay(10)))
          .flatMap(m => IntersectModifier.normalize(Maybe.present(m), IntersectModifier.Delay(20)))
          .flatMap(m => IntersectModifier.normalize(Maybe.present(m), IntersectModifier.Debounce(30)))
          .flatMap(m => IntersectModifier.normalize(Maybe.present(m), IntersectModifier.Throttle(40)))
          .flatMap(m => IntersectModifier.normalize(Maybe.present(m), IntersectModifier.ViewTransition))
        assertTrue(
          normalized.map(_.render).contains("__full__delay.20ms__debounce.30ms__throttle.40ms__viewTransition")
        )
      },
      test("normalize handles single, duplicate, and replacement visibility branches") {
        val single     = IntersectModifier.normalize(Maybe.absent, IntersectModifier.Once)
        val normalized = IntersectModifier
          .normalize(
            Maybe.present(IntersectModifier.And(IntersectModifier.Threshold(0.25), IntersectModifier.Once)),
            IntersectModifier.Half
          )
          .flatMap(m => IntersectModifier.normalize(Maybe.present(m), IntersectModifier.ViewTransition))
          .flatMap(m => IntersectModifier.normalize(Maybe.present(m), IntersectModifier.ViewTransition))
          .flatMap(m => IntersectModifier.normalize(Maybe.present(m), IntersectModifier.Full))
        assertTrue(single.contains(IntersectModifier.Once)) &&
        assertTrue(
          normalized.map(_.render).contains("__once__viewTransition__full") || normalized
            .map(_.render)
            .contains("__once__full__viewTransition")
        )
      }
    ),
    suite("OnIntervalModifier")(
      test("render and normalize cover duration variants and viewTransition") {
        val normalized = OnIntervalModifier
          .normalize(Maybe.absent, OnIntervalModifier.Duration(100, false))
          .flatMap(m => OnIntervalModifier.normalize(Maybe.present(m), OnIntervalModifier.ViewTransition))
          .flatMap(m => OnIntervalModifier.normalize(Maybe.present(m), OnIntervalModifier.Duration(200, true)))
        assertTrue(OnIntervalModifier.Duration(100, false).render == "__duration.100ms") &&
        assertTrue(OnIntervalModifier.Duration(200, true).render == "__duration.200ms.leading") &&
        assertTrue(OnIntervalModifier.ViewTransition.render == "__viewTransition") &&
        assertTrue(
          normalized.map(_.render).contains("__viewTransition__duration.200ms.leading") ||
            normalized.map(_.render).contains("__duration.200ms.leading__viewTransition")
        )
      },
      test("normalize handles single and duplicate viewTransition branches") {
        val single     = OnIntervalModifier.normalize(Maybe.absent, OnIntervalModifier.ViewTransition)
        val normalized = OnIntervalModifier
          .normalize(Maybe.present(OnIntervalModifier.ViewTransition), OnIntervalModifier.ViewTransition)
          .flatMap(m => OnIntervalModifier.normalize(Maybe.present(m), OnIntervalModifier.Duration(50, false)))
        assertTrue(single.contains(OnIntervalModifier.ViewTransition)) &&
        assertTrue(
          normalized.map(_.render).contains("__viewTransition__duration.50ms") || normalized
            .map(_.render)
            .contains("__duration.50ms__viewTransition")
        )
      }
    ),
    suite("OnSignalPatchModifier")(
      test("render and normalize cover timing families") {
        val normalized = OnSignalPatchModifier
          .normalize(Maybe.absent, OnSignalPatchModifier.Delay(10))
          .flatMap(m => OnSignalPatchModifier.normalize(Maybe.present(m), OnSignalPatchModifier.Delay(20)))
          .flatMap(m => OnSignalPatchModifier.normalize(Maybe.present(m), OnSignalPatchModifier.Debounce(30)))
          .flatMap(m => OnSignalPatchModifier.normalize(Maybe.present(m), OnSignalPatchModifier.Throttle(40)))
        assertTrue(OnSignalPatchModifier.Delay(1).render == "__delay.1ms") &&
        assertTrue(OnSignalPatchModifier.Debounce(2).render == "__debounce.2ms") &&
        assertTrue(OnSignalPatchModifier.Throttle(3).render == "__throttle.3ms") &&
        assertTrue(
          normalized.map(_.render).contains("__delay.20ms__debounce.30ms__throttle.40ms")
        )
      },
      test("normalize handles single and replacement timing branches") {
        val single     = OnSignalPatchModifier.normalize(Maybe.absent, OnSignalPatchModifier.Delay(5))
        val normalized = OnSignalPatchModifier
          .normalize(
            Maybe.present(OnSignalPatchModifier.And(OnSignalPatchModifier.Delay(1), OnSignalPatchModifier.Throttle(2))),
            OnSignalPatchModifier.Delay(3)
          )
          .flatMap(m => OnSignalPatchModifier.normalize(Maybe.present(m), OnSignalPatchModifier.Throttle(4)))
        assertTrue(single.contains(OnSignalPatchModifier.Delay(5))) &&
        assertTrue(normalized.map(_.render).contains("__delay.3ms__throttle.4ms"))
      }
    ),
    suite("Direct builders")(
      test("DataOn builds attributes directly from constructor") {
        val attr = new DataOn("customEvent", Maybe.absent, CaseModifier.Kebab).capture.stop.prevent.pascal := js"save()"
        assertTrue(
          renderAttr(attr) == """<div data-on:custom-event__capture__stop__prevent__case.pascal="save()"></div>"""
        )
      },
      test("DataInit builds attributes directly from constructor") {
        val attr = new DataInit(Maybe.absent).delay(100).viewTransition := js"boot()"
        assertTrue(renderAttr(attr) == """<div data-init__delay.100ms__viewTransition="boot()"></div>""")
      },
      test("DataOnIntersect builds attributes directly from constructor") {
        val attr = new DataOnIntersect(Maybe.absent).once.exit
          .threshold(0.25)
          .delay(10)
          .debounce(20)
          .throttle(30)
          .viewTransition := js"observe()"
        assertTrue(
          renderAttr(attr) ==
            """<div data-on-intersect__once__exit__threshold.0.25__delay.10ms__debounce.20ms__throttle.30ms__viewTransition="observe()"></div>"""
        )
      },
      test("DataOnInterval builds attributes directly from constructor") {
        val attr = new DataOnInterval(Maybe.absent).duration(100).durationLeading(200).viewTransition := js"tick()"
        assertTrue(
          renderAttr(attr) == """<div data-on-interval__duration.200ms.leading__viewTransition="tick()"></div>"""
        )
      },
      test("DataOnSignalPatch builds attributes directly from constructor") {
        val attr = new DataOnSignalPatch(Maybe.absent).delay(10).debounce(20).throttle(30) := js"patch()"
        assertTrue(
          renderAttr(attr) ==
            """<div data-on-signal-patch__delay.10ms__debounce.20ms__throttle.30ms="patch()"></div>"""
        )
      },
      test("DataSignalsBuilder applies explicit case modifiers") {
        val attr = new DataSignalsBuilder("userName", CaseModifier.Camel).snake := js"state.userName"
        assertTrue(renderAttr(attr) == """<div data-signals:user-name__case.snake="state.userName"></div>""")
      },
      test("DatastarAttrKey builds attributes directly") {
        val attr = DatastarAttrKey("data-custom") := js"enabled"
        assertTrue(renderAttr(attr) == """<div data-custom="enabled"></div>""")
      }
    ),
    suite("DatastarAttributes trait methods")(
      test("trait helpers render their concrete attributes") {
        val count = Signal[Int]("count")
        assertTrue(renderAttr(attrs.dataBind(count)) == """<div data-bind:count></div>""") &&
        assertTrue(renderAttr(attrs.dataIndicator(count)) == """<div data-indicator:count></div>""") &&
        assertTrue(renderAttr(attrs.dataRef("userName")) == """<div data-ref:user-name></div>""") &&
        assertTrue(renderAttr(attrs.dataIgnore) == """<div data-ignore></div>""") &&
        assertTrue(renderAttr(attrs.dataIgnoreSelf) == """<div data-ignore__self></div>""") &&
        assertTrue(renderAttr(attrs.dataIgnoreMorph) == """<div data-ignore-morph></div>""") &&
        assertTrue(
          renderAttr(attrs.dataPreserveAttr("class", "style")) == """<div data-preserve-attr="class style"></div>"""
        )
      },
      test("trait builder entry points expose uncovered concrete methods") {
        val visible = Signal[Boolean]("visible")
        assertTrue(renderAttr(attrs.dataOn.mouseleave := js"bye()") == """<div data-on:mouseleave="bye()"></div>""") &&
        assertTrue(renderAttr(attrs.dataOn.resize := js"resize()") == """<div data-on:resize="resize()"></div>""") &&
        assertTrue(renderAttr(attrs.dataOn.load := js"load()") == """<div data-on:load="load()"></div>""") &&
        assertTrue(
          renderAttr(
            attrs.dataComputed(Signal[Int]("total")) := js"a + b"
          ) == """<div data-computed:total="a + b"></div>"""
        ) &&
        assertTrue(
          renderAttr(attrs.dataClass("active") := js"isActive") == """<div data-class:active="isActive"></div>"""
        ) &&
        assertTrue(
          renderAttr(attrs.dataStyle("color") := js"theme.color") == """<div data-style:color="theme.color"></div>"""
        ) &&
        assertTrue(
          renderAttr(attrs.dataAttr("disabled") := js"isDisabled") == """<div data-attr:disabled="isDisabled"></div>"""
        ) &&
        assertTrue(
          renderAttr(
            attrs.dataOnSignalPatchFilter := js"$visible"
          ) == """<div data-on-signal-patch-filter="$visible"></div>"""
        ) &&
        assertTrue(renderAttr(attrs.dataJsonSignals := js"true") == """<div data-json-signals="true"></div>""")
      }
    ),
    suite("DatastarEvent builders")(
      test("patchElements builder methods compose into SSE output") {
        val dom    = Dom.Element.Generic("section", Chunk.empty, Chunk(Dom.Text("Hi")))
        val result = DatastarEvent
          .patchElements(dom)
          .selector(CssSelector.`class`("dashboard"))
          .mode(ElementPatchMode.Replace)
          .viewTransition
          .namespace("svg")
          .eventId("evt-9")
          .retry(2500L)
          .renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-elements\n" +
            "id: evt-9\n" +
            "retry: 2500\n" +
            "data: selector .dashboard\n" +
            "data: mode replace\n" +
            "data: useViewTransition true\n" +
            "data: namespace svg\n" +
            "data: elements <section>Hi</section>\n" +
            "\n"
        )
      },
      test("patchSignals builder methods compose into SSE output") {
        val result = DatastarEvent
          .patchSignalsRaw("{\"ready\":true}")
          .onlyIfMissing
          .eventId("sig-9")
          .retry(1500L)
          .renderSSE
        assertTrue(
          result ==
            "event: datastar-patch-signals\n" +
            "id: sig-9\n" +
            "retry: 1500\n" +
            "data: onlyIfMissing true\n" +
            "data: signals {\"ready\":true}\n" +
            "\n"
        )
      },
      test("executeScript and removeElements still use their default builder wiring") {
        val scriptResult = DatastarEvent.executeScript(js"console.log('x')").renderSSE
        val removeResult = DatastarEvent.removeElements(CssSelector.element("aside")).renderSSE
        assertTrue(
          scriptResult ==
            "event: datastar-patch-elements\n" +
            "data: selector body\n" +
            "data: mode append\n" +
            "data: elements <script data-effect=\"el.remove()\">console.log('x')</script>\n" +
            "\n"
        ) &&
        assertTrue(
          removeResult ==
            "event: datastar-patch-elements\n" +
            "data: selector aside\n" +
            "data: mode remove\n" +
            "data: elements \n" +
            "\n"
        )
      }
    )
  )
}
