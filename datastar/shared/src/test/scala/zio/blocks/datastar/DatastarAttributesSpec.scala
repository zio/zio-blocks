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

package zio.blocks.datastar

import zio.blocks.html._
import zio.blocks.schema.Schema
import zio.test._

object DatastarAttributesSpec extends ZIOSpecDefault {

  val attrs = new DatastarAttributes {}

  import attrs._

  val count    = Signal[Int]("count")
  val foo      = Signal[Int]("foo")
  val bar      = Signal[Int]("bar")
  val isActive = Signal[Boolean]("isActive")

  def spec = suite("DatastarAttributes")(
    suite("dataSignals (suffixed)")(
      test("dataSignals(signal) := js value produces correct attribute") {
        val result = div(dataSignals(count) := js"0").render
        assertTrue(result == """<div data-signals:count="0"></div>""")
      },
      test("dataSignals(signal).kebab produces correct attribute") {
        val result = div(dataSignals(count).kebab := js"0").render
        assertTrue(result == """<div data-signals:count__case.kebab="0"></div>""")
      },
      test("dataSignals(signal).camel produces correct attribute (default, no suffix)") {
        val result = div(dataSignals(count).camel := js"0").render
        assertTrue(result == """<div data-signals:count="0"></div>""")
      },
      test("dataSignals(signal).snake produces correct attribute") {
        val result = div(dataSignals(count).snake := js"0").render
        assertTrue(result == """<div data-signals:count__case.snake="0"></div>""")
      },
      test("dataSignals(signal).pascal produces correct attribute") {
        val result = div(dataSignals(count).pascal := js"0").render
        assertTrue(result == """<div data-signals:count__case.pascal="0"></div>""")
      }
    ),
    suite("dataSignals (unsuffixed)")(
      test("dataSignals := SignalUpdate produces correct attribute") {
        val result = div(dataSignals := (count := 0)).render
        assertTrue(result == """<div data-signals="{count: 0}"></div>""")
      }
    ),
    suite("dataBind")(
      test("dataBind(signal) produces boolean attribute") {
        val result = div(dataBind(count)).render
        assertTrue(result == """<div data-bind:count></div>""")
      }
    ),
    suite("dataText")(
      test("dataText := signal produces correct attribute") {
        val result = div(dataText := count).render
        assertTrue(result == """<div data-text="$count"></div>""")
      },
      test("dataText := js expression produces correct attribute") {
        val result = div(dataText := js"$foo + $bar").render
        assertTrue(result == """<div data-text="$foo + $bar"></div>""")
      }
    ),
    suite("dataShow")(
      test("dataShow := signal produces correct attribute") {
        val result = div(dataShow := count).render
        assertTrue(result == """<div data-show="$count"></div>""")
      }
    ),
    suite("dataClass")(
      test("dataClass(className) := js expression produces correct attribute") {
        val result = div(dataClass("active") := js"$isActive").render
        assertTrue(result == """<div data-class:active="$isActive"></div>""")
      },
      test("dataClass unsuffixed := js expression produces correct attribute") {
        val result = div(dataClass := js"{'active': $isActive}").render
        assertTrue(result == """<div data-class="{&#x27;active&#x27;: $isActive}"></div>""")
      }
    ),
    suite("dataStyle")(
      test("dataStyle(styleName) := js expression produces correct attribute") {
        val textColor = Signal[String]("textColor")
        val result    = div(dataStyle("color") := js"$textColor").render
        assertTrue(result == """<div data-style:color="$textColor"></div>""")
      },
      test("dataStyle unsuffixed := js expression produces correct attribute") {
        val result = div(dataStyle := js"color: red").render
        assertTrue(result == """<div data-style="color: red"></div>""")
      }
    ),
    suite("dataAttr")(
      test("dataAttr(attrName) := js expression produces correct attribute") {
        val isDisabled = Signal[Boolean]("isDisabled")
        val result     = div(dataAttr("disabled") := js"$isDisabled").render
        assertTrue(result == """<div data-attr:disabled="$isDisabled"></div>""")
      },
      test("dataAttr unsuffixed := js expression produces correct attribute") {
        val result = div(dataAttr := js"disabled").render
        assertTrue(result == """<div data-attr="disabled"></div>""")
      }
    ),
    suite("dataOn")(
      test("dataOn.click := js expression produces correct attribute") {
        val result = div(dataOn.click := js"${count}++").render
        assertTrue(result == """<div data-on:click="$count++"></div>""")
      },
      test("dataOn.click.debounce produces correct attribute") {
        val result = div(dataOn.click.debounce(300) := js"@get('/search')").render
        assertTrue(result == """<div data-on:click__debounce.300ms="@get(&#x27;/search&#x27;)"></div>""")
      },
      test("dataOn.submit.prevent produces correct attribute") {
        val result = div(dataOn.submit.prevent := js"@post('/form')").render
        assertTrue(result == """<div data-on:submit__prevent="@post(&#x27;/form&#x27;)"></div>""")
      },
      test("dataOn with custom event name") {
        val result = div(dataOn("custom-event") := js"handler()").render
        assertTrue(result == """<div data-on:custom-event="handler()"></div>""")
      },
      test("dataOn.click with chained debounce and once") {
        val result = div(dataOn.click.debounce(500).once := js"doIt()").render
        assertTrue(result == """<div data-on:click__debounce.500ms__once="doIt()"></div>""")
      },
      test("dataOn.click with multiple modifiers chained") {
        val result = div(dataOn.click.throttle(200).passive.capture := js"handler()").render
        assertTrue(result == """<div data-on:click__throttle.200ms__passive__capture="handler()"></div>""")
      },
      test("dataOn.keydown produces correct attribute") {
        val result = div(dataOn.keydown := js"handleKey()").render
        assertTrue(result == """<div data-on:keydown="handleKey()"></div>""")
      },
      test("dataOn.click.once produces correct attribute") {
        val result = div(dataOn.click.once := js"init()").render
        assertTrue(result == """<div data-on:click__once="init()"></div>""")
      },
      test("dataOn.click.window produces correct attribute") {
        val result = div(dataOn.click.window := js"handler()").render
        assertTrue(result == """<div data-on:click__window="handler()"></div>""")
      },
      test("dataOn.click.document produces correct attribute") {
        val result = div(dataOn.click.document := js"handler()").render
        assertTrue(result == """<div data-on:click__document="handler()"></div>""")
      },
      test("dataOn.click.outside produces correct attribute") {
        val result = div(dataOn.click.outside := js"close()").render
        assertTrue(result == """<div data-on:click__outside="close()"></div>""")
      },
      test("dataOn.click.stop.prevent produces correct attribute") {
        val result = div(dataOn.click.stop.prevent := js"handler()").render
        assertTrue(result == """<div data-on:click__stop__prevent="handler()"></div>""")
      },
      test("dataOn.click.delay produces correct attribute") {
        val result = div(dataOn.click.delay(1000) := js"handler()").render
        assertTrue(result == """<div data-on:click__delay.1000ms="handler()"></div>""")
      },
      test("dataOn.click.debounceLeading produces correct attribute") {
        val result = div(dataOn.click.debounceLeading(300) := js"handler()").render
        assertTrue(result == """<div data-on:click__debounce.300ms.leading="handler()"></div>""")
      },
      test("dataOn.click.throttleLeading produces correct attribute") {
        val result = div(dataOn.click.throttleLeading(200) := js"handler()").render
        assertTrue(result == """<div data-on:click__throttle.200ms.leading="handler()"></div>""")
      },
      test("dataOn.click.viewTransition produces correct attribute") {
        val result = div(dataOn.click.viewTransition := js"navigate()").render
        assertTrue(result == """<div data-on:click__viewTransition="navigate()"></div>""")
      },
      test("dataOn.input produces correct attribute") {
        val result = div(dataOn.input.debounce(300) := js"search()").render
        assertTrue(result == """<div data-on:input__debounce.300ms="search()"></div>""")
      },
      test("dataOn with case modifier camel (default, no suffix)") {
        val result = div(dataOn.click.camel := js"handler()").render
        assertTrue(result == """<div data-on:click="handler()"></div>""")
      },
      test("dataOn with case modifier kebab") {
        val result = div(dataOn.click.kebab := js"handler()").render
        assertTrue(result == """<div data-on:click__case.kebab="handler()"></div>""")
      },
      test("dataOn with modifier and case modifier") {
        val result = div(dataOn.click.debounce(300).kebab := js"handler()").render
        assertTrue(result == """<div data-on:click__debounce.300ms__case.kebab="handler()"></div>""")
      }
    ),
    suite("dataComputed")(
      test("dataComputed(signal) := js expression produces correct attribute") {
        val result = div(dataComputed(count) := js"a + b").render
        assertTrue(result == """<div data-computed:count="a + b"></div>""")
      }
    ),
    suite("dataEffect")(
      test("dataEffect := js expression produces correct attribute") {
        val result = div(dataEffect := js"console.log($count)").render
        assertTrue(result == """<div data-effect="console.log($count)"></div>""")
      }
    ),
    suite("dataIndicator")(
      test("dataIndicator(signal) produces boolean attribute") {
        val loading = Signal[Boolean]("loading")
        val result  = div(dataIndicator(loading)).render
        assertTrue(result == """<div data-indicator:loading></div>""")
      }
    ),
    suite("dataRef")(
      test("dataRef(name) produces boolean attribute") {
        val result = div(dataRef("myEl")).render
        assertTrue(result == """<div data-ref:myEl></div>""")
      }
    ),
    suite("dataInit")(
      test("dataInit := js expression produces correct attribute") {
        val result = div(dataInit := js"@get('/data')").render
        assertTrue(result == """<div data-init="@get(&#x27;/data&#x27;)"></div>""")
      },
      test("dataInit.delay(500) := js expression produces correct attribute") {
        val result = div(dataInit.delay(500) := js"@get('/data')").render
        assertTrue(result == """<div data-init__delay.500ms="@get(&#x27;/data&#x27;)"></div>""")
      },
      test("dataInit.delay(500).viewTransition := js expression produces correct attribute") {
        val result = div(dataInit.delay(500).viewTransition := js"@get('/data')").render
        assertTrue(result == """<div data-init__delay.500ms__viewTransition="@get(&#x27;/data&#x27;)"></div>""")
      }
    ),
    suite("dataIgnore")(
      test("dataIgnore produces boolean attribute") {
        val result = div(dataIgnore).render
        assertTrue(result == """<div data-ignore></div>""")
      },
      test("dataIgnoreSelf produces boolean attribute") {
        val result = div(dataIgnoreSelf).render
        assertTrue(result == """<div data-ignore__self></div>""")
      },
      test("dataIgnoreMorph produces boolean attribute") {
        val result = div(dataIgnoreMorph).render
        assertTrue(result == """<div data-ignore-morph></div>""")
      }
    ),
    suite("DatastarAttrKey")(
      test(":= with Signal value uses ToJs[Signal]") {
        val key    = DatastarAttrKey("data-text")
        val result = div(key := count).render
        assertTrue(result == """<div data-text="$count"></div>""")
      },
      test(":= with Js value uses ToJs[Js]") {
        val key    = DatastarAttrKey("data-show")
        val result = div(key := js"true").render
        assertTrue(result == """<div data-show="true"></div>""")
      },
      test(":= with SignalUpdate value uses ToJs[SignalUpdate]") {
        val key    = DatastarAttrKey("data-signals")
        val result = div(key := (count := 0)).render
        assertTrue(result == """<div data-signals="{count: 0}"></div>""")
      }
    ),
    suite("dataOnIntersect")(
      test("dataOnIntersect.once.half := js expression produces correct attribute") {
        val result = div(dataOnIntersect.once.half := js"@get('/lazy')").render
        assertTrue(result == """<div data-on-intersect__once__half="@get(&#x27;/lazy&#x27;)"></div>""")
      },
      test("dataOnIntersect.full := js expression produces correct attribute") {
        val result = div(dataOnIntersect.full := js"handler()").render
        assertTrue(result == """<div data-on-intersect__full="handler()"></div>""")
      },
      test("dataOnIntersect.exit := js expression produces correct attribute") {
        val result = div(dataOnIntersect.exit := js"handler()").render
        assertTrue(result == """<div data-on-intersect__exit="handler()"></div>""")
      },
      test("dataOnIntersect.threshold(0.5) := js expression produces correct attribute") {
        val result = div(dataOnIntersect.threshold(0.5) := js"handler()").render
        assertTrue(result == """<div data-on-intersect__threshold.0.5="handler()"></div>""")
      },
      test("dataOnIntersect.delay(500) := js expression produces correct attribute") {
        val result = div(dataOnIntersect.delay(500) := js"handler()").render
        assertTrue(result == """<div data-on-intersect__delay.500ms="handler()"></div>""")
      },
      test("dataOnIntersect.debounce(300) := js expression produces correct attribute") {
        val result = div(dataOnIntersect.debounce(300) := js"handler()").render
        assertTrue(result == """<div data-on-intersect__debounce.300ms="handler()"></div>""")
      },
      test("dataOnIntersect.throttle(200) := js expression produces correct attribute") {
        val result = div(dataOnIntersect.throttle(200) := js"handler()").render
        assertTrue(result == """<div data-on-intersect__throttle.200ms="handler()"></div>""")
      },
      test("dataOnIntersect.viewTransition := js expression produces correct attribute") {
        val result = div(dataOnIntersect.viewTransition := js"handler()").render
        assertTrue(result == """<div data-on-intersect__viewTransition="handler()"></div>""")
      },
      test("dataOnIntersect.once.threshold(0.75).viewTransition produces correct chained modifiers") {
        val result = div(dataOnIntersect.once.threshold(0.75).viewTransition := js"handler()").render
        assertTrue(
          result == """<div data-on-intersect__once__threshold.0.75__viewTransition="handler()"></div>"""
        )
      },
      test("dataOnIntersect without modifiers produces correct attribute") {
        val result = div(dataOnIntersect := js"handler()").render
        assertTrue(result == """<div data-on-intersect="handler()"></div>""")
      }
    ),
    suite("dataOnInterval")(
      test("dataOnInterval.duration(1000) := js expression produces correct attribute") {
        val result = div(dataOnInterval.duration(1000) := js"@get('/poll')").render
        assertTrue(result == """<div data-on-interval__duration.1000ms="@get(&#x27;/poll&#x27;)"></div>""")
      },
      test("dataOnInterval.durationLeading(2000) := js expression produces correct attribute") {
        val result = div(dataOnInterval.durationLeading(2000) := js"handler()").render
        assertTrue(result == """<div data-on-interval__duration.2000ms.leading="handler()"></div>""")
      },
      test("dataOnInterval.viewTransition := js expression produces correct attribute") {
        val result = div(dataOnInterval.viewTransition := js"handler()").render
        assertTrue(result == """<div data-on-interval__viewTransition="handler()"></div>""")
      },
      test("dataOnInterval.duration(500).viewTransition produces correct chained modifiers") {
        val result = div(dataOnInterval.duration(500).viewTransition := js"handler()").render
        assertTrue(result == """<div data-on-interval__duration.500ms__viewTransition="handler()"></div>""")
      },
      test("dataOnInterval without modifiers produces correct attribute") {
        val result = div(dataOnInterval := js"handler()").render
        assertTrue(result == """<div data-on-interval="handler()"></div>""")
      }
    ),
    suite("dataOnSignalPatch")(
      test("dataOnSignalPatch.debounce(200) := js expression produces correct attribute") {
        val result = div(dataOnSignalPatch.debounce(200) := js"handler()").render
        assertTrue(result == """<div data-on-signal-patch__debounce.200ms="handler()"></div>""")
      },
      test("dataOnSignalPatch.delay(100) := js expression produces correct attribute") {
        val result = div(dataOnSignalPatch.delay(100) := js"handler()").render
        assertTrue(result == """<div data-on-signal-patch__delay.100ms="handler()"></div>""")
      },
      test("dataOnSignalPatch.throttle(300) := js expression produces correct attribute") {
        val result = div(dataOnSignalPatch.throttle(300) := js"handler()").render
        assertTrue(result == """<div data-on-signal-patch__throttle.300ms="handler()"></div>""")
      },
      test("dataOnSignalPatch.delay(100).debounce(200).throttle(300) produces correct chained modifiers") {
        val result = div(dataOnSignalPatch.delay(100).debounce(200).throttle(300) := js"handler()").render
        assertTrue(
          result == """<div data-on-signal-patch__delay.100ms__debounce.200ms__throttle.300ms="handler()"></div>"""
        )
      },
      test("dataOnSignalPatch without modifiers produces correct attribute") {
        val result = div(dataOnSignalPatch := js"handler()").render
        assertTrue(result == """<div data-on-signal-patch="handler()"></div>""")
      }
    ),
    suite("dataOnSignalPatchFilter")(
      test("dataOnSignalPatchFilter := js expression produces correct attribute") {
        val visible = Signal[Boolean]("visible")
        val result  = div(dataOnSignalPatchFilter := js"$visible").render
        assertTrue(result == """<div data-on-signal-patch-filter="$visible"></div>""")
      }
    ),
    suite("dataJsonSignals")(
      test("dataJsonSignals := js expression produces correct attribute") {
        val result = div(dataJsonSignals := js"true").render
        assertTrue(result == """<div data-json-signals="true"></div>""")
      }
    ),
    suite("dataPreserveAttr")(
      test("dataPreserveAttr with multiple attributes produces space-separated value") {
        val result = div(dataPreserveAttr("class", "style")).render
        assertTrue(result == """<div data-preserve-attr="class style"></div>""")
      },
      test("dataPreserveAttr with single attribute produces correct attribute") {
        val result = div(dataPreserveAttr("id")).render
        assertTrue(result == """<div data-preserve-attr="id"></div>""")
      }
    ),
    suite("package object integration")(
      test("import zio.blocks.datastar._ provides all attribute methods") {
        import zio.blocks.datastar._
        val result = div(dataSignals(count) := js"0", dataBind(count), dataText := count).render
        assertTrue(
          result == """<div data-signals:count="0" data-bind:count data-text="$count"></div>"""
        )
      }
    )
  )
}
