---
id: roll-out-feature-gradually
title: "How to Roll Out a Feature Gradually with Flags"
description: "Ship a feature to 10% of readers, target one region, widen the rollout at runtime, and test the code that reads the flag."
keywords:
  - "Feature Flags"
  - "Percentage Rollout"
  - "StaticFlag"
  - "DynamicFlag"
  - "Rollout"
  - "FlagSource"
---

In this guide, we roll out two changes to a Scala bookstore with ZIO Blocks flags: a lower free-shipping threshold that only changes on restart, and a new recommendations panel that reaches 10% of readers first, then half of the readers in the `eu` region, then more, without a redeploy. By the end, you will have flags that give each reader the same answer on every request, an expression you can widen while the service runs, a startup report that catches misspelled overrides, and tests that keep the global flag registry clean.

We start with a restart-only knob, turn the panel on for a percentage of readers, narrow it to one region, change it at runtime, check expressions before they ship, print a startup report, and finish with tests:

- [A Restart-Only Knob: The Free-Shipping Threshold](#a-restart-only-knob-the-free-shipping-threshold)
- [Turning the Panel On for 10% of Readers](#turning-the-panel-on-for-10-of-readers)
- [Targeting One Region](#targeting-one-region)
- [Changing the Rollout While the Service Runs](#changing-the-rollout-while-the-service-runs)
- [Checking an Expression Before You Ship It](#checking-an-expression-before-you-ship-it)
- [Catching Typos at Startup](#catching-typos-at-startup)
- [Testing Code That Uses Flags](#testing-code-that-uses-flags)

## The Problem

A gradual rollout needs three things a hand-rolled boolean does not give you: the same reader must get the same answer on every request, the share must be adjustable without a deploy, and a mistyped override must be noticed instead of silently ignored.

Without a flag library, the recommendations panel usually starts like this:

```scala mdoc:compile-only
import scala.util.Random

object Recommendations {
  // Read once at startup: changing the share needs a restart.
  private val share: Double =
    sys.env.get("RECOMMENDATIONS_SHARE").flatMap(_.toDoubleOption).getOrElse(0.0)

  // Re-rolled on every call: a reader sees the panel, reloads the page, and it is gone.
  def showPanel(readerId: String, region: String): Boolean =
    region == "eu" && Random.nextDouble() < share
}
```

The coin flip makes the panel flicker for every reader, a typo such as `RECOMENDATIONS_SHARE` falls back to `0.0` without a word, and the region rule is code, so every change to it is a deploy. This guide replaces the coin flip with sticky percentage bucketing and the hard-coded rule with a rollout expression that can be replaced at runtime.

## Prerequisites

Add the config module to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-config" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-config" % "@VERSION@"
```

This guide assumes you have loaded configuration with ZIO Blocks before; [Build a Typed Configuration Loader](./typed-config-loader.md) covers sources and provenance. Most snippets below use one import:

```scala mdoc:silent
import zio.blocks.config._
```

```scala mdoc:invisible
// mdoc runs every page in one JVM, so start from empty registries.
FlagSource.Registry.clear()
Flag.registry.clear()
```

Flags are Scala `object`s, and each flag's name is the object's fully-qualified name. On this page, mdoc nests every object inside `repl.MdocSession.MdocApp`, so that prefix appears in the names below; in your application, the prefix is your package and enclosing objects, as the [companion examples](#running-the-examples) show.

## A Restart-Only Knob: The Free-Shipping Threshold

The free-shipping threshold is a setting the bookstore changes rarely and only on restart, so it is a [`StaticFlag`](../reference/config/flags.md). A `StaticFlag[A]` takes a default and resolves its value once, the first time anything touches the object.

Its value comes from the first place that has one: a source registered in `FlagSource.Registry`, then a system property with the flag's name, then an environment variable with the dots replaced by underscores and uppercased, then the default. Here, an operator lowers the threshold from $35 to $25 through a map source:

```scala mdoc:silent
FlagSource.Registry.register(
  FlagSource.fromMap(
    Map("repl.MdocSession.MdocApp.FreeShippingThreshold" -> "25"),
    "ops-overrides"
  )
)

object FreeShippingThreshold extends StaticFlag[Int](35)
```

Touching the flag resolves it. `StaticFlag#source` tells you which step answered:

```scala mdoc
println(FreeShippingThreshold.name)
println(FreeShippingThreshold())
println(FreeShippingThreshold.source)
```

:::warning[Register sources before the first touch]
A `StaticFlag` reads the registry once, when its object initializes. A source registered after that is never consulted for that flag, so register every source at the top of `main`, before any code reads a flag.
:::

A value that does not parse stops the flag at first touch instead of handing out a plausible wrong value. The object initializer throws `ExceptionInInitializerError` whose cause is a `FlagException.FlagValueParseException`:

```scala mdoc:silent
FlagSource.Registry.register(
  FlagSource.fromMap(
    Map("repl.MdocSession.MdocApp.MaxBooksPerOrder" -> "ten"),
    "bad-overrides"
  )
)

object MaxBooksPerOrder extends StaticFlag[Int](20)
```

Touching the flag now fails at initialization:

```scala mdoc
try println(MaxBooksPerOrder())
catch {
  case e: ExceptionInInitializerError => println(e.getCause.getMessage)
}
```

For a top-level object, the JVM does not retry a failed initializer: every later touch throws `NoClassDefFoundError: Could not initialize class`. Let the error stop the service at startup rather than catching it and carrying on.

## Turning the Panel On for 10% of Readers

The recommendations panel needs a decision per reader that can change while the service runs, so it is a `DynamicFlag`. A `DynamicFlag[A]` takes a default value and a default rollout expression. The expression `true@*/10%; false` reads as two choices, tried left to right: `true` for 10% of any key, otherwise `false`:

```scala mdoc:silent
object RecommendationsPanel extends DynamicFlag[Boolean](false, "true@*/10%; false")
```

Call the flag with the reader's id. The id is the bucketing key: [`Rollout.bucketFor`](../reference/config/rollout.md) hashes it with MurmurHash3 into a bucket from 0 to 99, and `10%` matches buckets 0 to 9. The hash depends only on the id, so the answer for a reader never changes between calls, requests or restarts:

```scala mdoc
println(Rollout.bucketFor("reader-42"))
println((1 to 3).map(_ => RecommendationsPanel("reader-42")))
```

Across 1000 synthetic reader ids, the share that sees the panel is close to the 10% the expression names:

```scala mdoc:silent
val readers = (1 to 1000).map(i => s"reader-$i")
val panelCohort = readers.filter(RecommendationsPanel.evaluate(_))
```

Printing the cohort size and its first five readers shows the share:

```scala mdoc
println(s"${panelCohort.size} of ${readers.size} readers see the panel")
println(panelCohort.take(5))
```

`DynamicFlag#apply` also counts each call per key for the [evaluation counters](#changing-the-rollout-while-the-service-runs); `DynamicFlag#evaluate` returns the same answer without counting, which is what a batch like this one should use.

:::warning[Every rollout buckets the same readers]
The bucket depends on the key alone, not on the flag. Two features both at `10%` and keyed by the plain reader id reach exactly the same readers, so a reader in bucket 3 gets every early rollout at once. Prefix the key with the feature's name when rollouts should be independent:

```scala mdoc:silent
object WishlistSharing extends DynamicFlag[Boolean](false, "true@*/10%; false")

val wishlistPlain = readers.filter(WishlistSharing.evaluate(_))
val panelSalted = readers.filter(id => RecommendationsPanel.evaluate(s"recommendations:$id"))
val wishlistSalted = readers.filter(id => WishlistSharing.evaluate(s"wishlist:$id"))
```

Comparing the cohorts shows how many readers appear in both rollouts:

```scala mdoc
println(s"in both rollouts, plain key:  ${panelCohort.intersect(wishlistPlain).size}")
println(s"in both rollouts, salted key: ${panelSalted.intersect(wishlistSalted).size}")
```

Use the same prefix at every call site of a flag, or the reader moves to another bucket.
:::

## Targeting One Region

To offer the panel to half of the readers in the `eu` region and to nobody else, pass the region as an attribute after the reader id. The flag joins the key and the attributes with slashes into a path such as `reader-42/eu`, and a selector must match that path segment by segment. `*` matches the reader id, `eu` must match the region literally, and `50%` applies to whatever matched:

```scala mdoc:silent
object RegionalPanel extends DynamicFlag[Boolean](false, "true@*/eu/50%; false")
```

Counting the readers each region sees shows the targeting:

```scala mdoc
println(readers.count(id => RegionalPanel.evaluate(id, "eu")))
println(readers.count(id => RegionalPanel.evaluate(id, "us")))
```

Readers outside `eu` fall through to the catch-all `false`. Rules for more regions are more choices, tried in order: `true@*/eu/100%; true@*/us/25%; false` gives the panel to every `eu` reader and a quarter of the `us` readers.

**The segment count must match exactly.** A selector with two segments never matches a path with one or three. A call site that forgets the region builds the one-segment path `reader-42`, which `*/eu/50%` never matches, so the flag silently answers with the catch-all:

```scala mdoc
println(readers.count(id => RegionalPanel.evaluate(id)))
```

The full matching rules are in [Rollout](../reference/config/rollout.md).

## Changing the Rollout While the Service Runs

A rollout widens in steps: 10%, then 25%, then 50%. `DynamicFlag#update` replaces the expression from code and returns `Right(())` when the new expression parses:

```scala mdoc
println(RecommendationsPanel.update("true@*/25%; false"))
```

Widening keeps everyone who already had the panel. A reader in bucket 7 matches `bucket < 10` and still matches `bucket < 25`, so nobody loses the panel when the share grows:

```scala mdoc:silent
val widened = readers.filter(RecommendationsPanel.evaluate(_))
```

Comparing the two cohorts shows that nobody lost the panel:

```scala mdoc
println(s"${widened.size} readers, original cohort kept: ${panelCohort.forall(widened.contains)}")
```

An expression that does not parse comes back as `Left(ConfigError)`, and the current expression stays in place:

```scala mdoc
println(RecommendationsPanel.update("true@*/150%; false"))
println(RecommendationsPanel.expression)
```

**Parsing does not check the values.** `DynamicFlag#update` checks the grammar, not whether each value parses as the flag's type. An expression whose values the flag cannot read is accepted, every matching call answers with the default, and `DynamicFlag#parseErrorCount` counts the fallbacks; see [Checking an Expression Before You Ship It](#checking-an-expression-before-you-ship-it). `DynamicFlag#update` also accepts an empty or blank expression as a no-op and returns `Right(())` without changing anything.

### Reloading From a Source

In production the expression lives in a flag service or a config file, not in code. `DynamicFlag#reload` reads the flag's name from the registered sources and reports the outcome as a `Flag.ReloadResult`. With no source providing the flag, nothing changes:

```scala mdoc
println(RecommendationsPanel.reload())
```

Once a source provides an expression, `DynamicFlag#reload` applies it, and reloading the same expression again is a no-op. Because the flag is already initialized, `RecommendationsPanel.name` is safe to use as the key:

```scala mdoc:silent
FlagSource.Registry.register(
  FlagSource.fromMap(Map(RecommendationsPanel.name -> "true@*/50%; false"), "flag-service")
)
```

The first reload applies the source's expression, and the second finds nothing to change:

```scala mdoc
println(RecommendationsPanel.reload())
println(RecommendationsPanel.reload())
```

A bad push to the flag service comes back as `Failed`, and the flag keeps its current expression instead of dropping to the default. Registering a source under an existing `FlagSource#sourceId` replaces it in place, which is how this snippet simulates a new push:

```scala mdoc:silent
FlagSource.Registry.register(
  FlagSource.fromMap(Map(RecommendationsPanel.name -> "true@*/fifty%"), "flag-service")
)
```

Reloading again rejects the bad push and keeps the current expression:

```scala mdoc
println(RecommendationsPanel.reload())
println(RecommendationsPanel.expression)
```

### Auditing Changes

Every successful `DynamicFlag#update` or `DynamicFlag#reload` adds a `DynamicFlag.UpdateRecord` to `DynamicFlag#updateHistory`, most recent first and capped at the last ten. Each record also carries a `DynamicFlag.UpdateRecord#timestampMillis`:

```scala mdoc
RecommendationsPanel.updateHistory.foreach(r => println(s"${r.oldExpression}  ->  ${r.newExpression}"))
```

`DynamicFlag#counters` shows how often `DynamicFlag#apply` saw each key; `DynamicFlag#evaluate` calls are not counted. After three more calls, the snapshot holds them next to the three calls for `reader-42` from earlier. The counters stop adding keys at 100 distinct ones and count the rest under `"other"`:

```scala mdoc:silent
RecommendationsPanel("reader-1")
RecommendationsPanel("reader-1")
RecommendationsPanel("reader-2")
```

Printing the snapshot shows each key's count:

```scala mdoc
println(RecommendationsPanel.counters.toList.sorted)
```

`DynamicFlag#parseErrorCount` counts calls whose matched value could not be parsed as the flag's type. The expression `maybe` parses as a catch-all, but `maybe` is not a `Boolean`, so each call answers with the default `false` and the count grows:

```scala mdoc
println(RecommendationsPanel.update("maybe"))
println(RecommendationsPanel("reader-1"))
println(RecommendationsPanel.parseErrorCount)
```

A non-zero `DynamicFlag#parseErrorCount` always means a misconfigured expression, so it is worth exporting as a metric.

### Reloading on a Schedule

Reload is manual. The library does not poll a source, watch a file or start a thread, so call `DynamicFlag#reload` from a scheduler your service already has. A plain `ScheduledExecutorService` is enough:

```scala mdoc:compile-only
import java.util.concurrent.{Executors, TimeUnit}

val scheduler = Executors.newSingleThreadScheduledExecutor()

scheduler.scheduleAtFixedRate(
  () =>
    RecommendationsPanel.reload() match {
      case Flag.ReloadResult.Updated(from, to) => println(s"rollout changed: $from -> $to")
      case Flag.ReloadResult.Failed(error)     => println(s"rollout rejected: ${error.message}")
      case Flag.ReloadResult.Unchanged         => ()
      case Flag.ReloadResult.NoSource          => println("no source provides the rollout")
    },
  0,
  30,
  TimeUnit.SECONDS
)
```

Shut the scheduler down when the service stops. For a source that fetches from a remote service, refresh the source's contents on the same schedule before calling `DynamicFlag#reload`.

## Checking an Expression Before You Ship It

`Rollout.validate` parses an expression and returns warnings for rules that parse but are almost certainly wrong. Run it in the admin endpoint or deploy check that accepts expressions from operators; flags do not run it themselves. A catch-all before targeted choices makes them unreachable:

```scala mdoc
println(Rollout.validate("false; true@*/eu/50%"))
```

Percentages for one pattern that add up to more than 100% are flagged too:

```scala mdoc
println(Rollout.validate("true@*/eu/60%; true@*/eu/50%; false"))
```

An expression that does not parse comes back as `Left`, and a clean one as an empty list:

```scala mdoc
println(Rollout.validate("true@*/eu/150%; false"))
println(Rollout.validate("true@*/eu/50%; false"))
```

:::warning[Percentages are thresholds, not slices]
Each choice matches `bucket < percentage` on its own, and the first match wins. `a@*/10%; b@*/20%; c` gives `a` to buckets 0–9 and `b` only to buckets 10–19, so `b` reaches 10% of keys, not 20%. A later choice with a lower percentage on the same pattern is never reached, and `Rollout.validate` does not warn about it:

```scala mdoc
println(Rollout.validate("true@*/eu/30%; false@*/eu/20%; false"))
```

To split readers between variants, give each choice the cumulative upper bound of its slice: `a@*/10%; b@*/30%; c` is 10% `a`, 20% `b` and 70% `c`.
:::

Validation checks the grammar, so a value of the wrong type still slips through, as the `maybe` example above showed. To catch that too, parse each value with the flag's reader before shipping:

```scala mdoc:silent
def valuesParseAsBoolean(expression: String): Boolean =
  Rollout.parseChoices(expression).exists { choices =>
    choices.entries.forall {
      case Rollout.Choice.Targeted(value, _) => Flag.Reader.booleanReader.parse("check", value).isRight
      case Rollout.Choice.CatchAll(value)    => Flag.Reader.booleanReader.parse("check", value).isRight
    }
  }
```

Checking one valid and one invalid expression shows the difference:

```scala mdoc
println(valuesParseAsBoolean("true@*/eu/50%; false"))
println(valuesParseAsBoolean("maybe"))
```

## Catching Typos at Startup

An override whose key is spelled differently from the flag's name is ignored, and the flag quietly uses its default. `Flag.nearMissWarnings` looks in three places. In the registered sources it looks for keys that differ from the flag's name in case or in dots versus underscores. In system properties and environment variables it looks only for case differences (for an environment variable, against the flag's name with dots replaced by underscores and uppercased):

```scala mdoc:silent
FlagSource.Registry.register(
  FlagSource.fromMap(
    Map("repl.mdocsession.mdocapp.newcheckout" -> "true@*/5%; false"),
    "typo-overrides"
  )
)

object NewCheckout extends DynamicFlag[Boolean](false, "false")
```

Printing the expression shows the default, and the warning names the misspelled key:

```scala mdoc
println(NewCheckout.expression)
Flag.nearMissWarnings(NewCheckout.name).foreach(println)
```

`Flag.dump()` prints every flag that has been touched, with its value or expression and where the value came from. A flag whose object has not been touched yet is missing from the table, so print the report after startup has read every flag:

```scala mdoc
println(Flag.dump())
```

:::warning[Use `Secret` for credentials in flags]
`Flag.dump()` masks values by type, not by name. A `StaticFlag[Secret]` prints as `<secret>`, but a `StaticFlag[String]` prints its value even when the flag is called `PaymentToken`. Give credentials the type `Secret`. The dump reads only the flags themselves; it does not print the environment or the contents of sources.
:::

## Testing Code That Uses Flags

Flags read two global singletons: `FlagSource.Registry`, which holds the sources, and the flag objects themselves. Tests that register sources therefore share state, so follow three rules:

1. **Clear the registry in setup and in teardown.** A source left behind by one test changes what the next test's flags see.
2. **Run flag tests sequentially.** Two tests registering sources at the same time race on the registry. The config module's own tests turn off parallel execution for this reason.
3. **Register before first touch, then reload.** A `StaticFlag` resolves once per JVM, so it cannot take a different value per test; keep restart-only values out of logic you want to test with several values. A `DynamicFlag` can be reset in each test with `DynamicFlag#reload` or `DynamicFlag#update`.

A small helper keeps the first rule in one place:

```scala mdoc:silent
def withFlagSources[A](sources: FlagSource*)(body: => A): A = {
  FlagSource.Registry.clear()
  sources.foreach(FlagSource.Registry.register)
  try body
  finally FlagSource.Registry.clear()
}

def showPanel(readerId: String, region: String): Boolean =
  RegionalPanel(readerId, region)
```

Each test registers the expression it needs and reloads the flag before calling the code under test:

```scala mdoc
println(withFlagSources(FlagSource.fromMap(Map(RegionalPanel.name -> "true@*/eu; false"))) {
  RegionalPanel.reload()
  (showPanel("reader-42", "eu"), showPanel("reader-42", "us"))
})

println(FlagSource.Registry.all.size)
```

With ZIO Test, wrap each test body in the helper and mark the suite `TestAspect.sequential`. In sbt, also set `Test / parallelExecution := false` when several specs touch flags:

```scala
import zio.test._

object RecommendationsSpec extends ZIOSpecDefault {
  def spec = suite("recommendations panel")(
    test("eu readers get the panel") {
      withFlagSources(FlagSource.fromMap(Map(RegionalPanel.name -> "true@*/eu; false"))) {
        RegionalPanel.reload()
        assertTrue(showPanel("reader-42", "eu"), !showPanel("reader-42", "us"))
      }
    },
    test("nobody gets the panel when it is off") {
      withFlagSources(FlagSource.fromMap(Map(RegionalPanel.name -> "false"))) {
        RegionalPanel.reload()
        assertTrue(!showPanel("reader-42", "eu"))
      }
    }
  ) @@ TestAspect.sequential
}
```

## Putting It Together

The complete example declares the bookstore's flags, registers a stand-in for a flag service before touching them, prints the startup report, serves three readers, validates a wider rollout, and picks it up with a scheduled reload:

```scala mdoc:embed:config-examples/src/main/scala/rolloutfeaturegradually/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `config-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/config-examples
```

Each file below matches one section of this guide.

### A Restart-Only Knob

<details>
  <summary>config-examples/src/main/scala/rolloutfeaturegradually/Step1StaticFlag.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/rolloutfeaturegradually/Step1StaticFlag.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain rolloutfeaturegradually.Step1StaticFlag"
```

### Turning the Panel On for 10% of Readers

<details>
  <summary>config-examples/src/main/scala/rolloutfeaturegradually/Step2PercentageRollout.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/rolloutfeaturegradually/Step2PercentageRollout.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain rolloutfeaturegradually.Step2PercentageRollout"
```

### Targeting One Region

<details>
  <summary>config-examples/src/main/scala/rolloutfeaturegradually/Step3RegionTargeting.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/rolloutfeaturegradually/Step3RegionTargeting.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain rolloutfeaturegradually.Step3RegionTargeting"
```

### Changing the Rollout While the Service Runs

<details>
  <summary>config-examples/src/main/scala/rolloutfeaturegradually/Step4RuntimeChanges.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/rolloutfeaturegradually/Step4RuntimeChanges.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain rolloutfeaturegradually.Step4RuntimeChanges"
```

### Checking an Expression Before You Ship It

<details>
  <summary>config-examples/src/main/scala/rolloutfeaturegradually/Step5Validation.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/rolloutfeaturegradually/Step5Validation.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain rolloutfeaturegradually.Step5Validation"
```

### Catching Typos at Startup

<details>
  <summary>config-examples/src/main/scala/rolloutfeaturegradually/Step6StartupReport.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/rolloutfeaturegradually/Step6StartupReport.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain rolloutfeaturegradually.Step6StartupReport"
```

### Testing Code That Uses Flags

<details>
  <summary>config-examples/src/main/scala/rolloutfeaturegradually/Step7Testing.scala</summary>

```scala mdoc:embed:config-examples/src/main/scala/rolloutfeaturegradually/Step7Testing.scala:show-line-numbers
```

</details>

Run it from the `config-examples` directory:

```bash
sbt "runMain rolloutfeaturegradually.Step7Testing"
```

### The Complete Example

Run the complete example from the `config-examples` directory:

```bash
sbt "runMain rolloutfeaturegradually.CompleteExample"
```

To check that every example compiles without running it, use:

```bash
sbt compile
```

## Going Further

- [Flags](../reference/config/flags.md) covers every member of `StaticFlag`, `DynamicFlag`, `Flag.Reader` and `FlagException`, including custom readers for your own types.
- [Rollout](../reference/config/rollout.md) covers the expression grammar, path matching and the parsed form an admin tool can render.
- [ConfigSource](../reference/config/config-source.md) explains `Secret`; any `ConfigSource` is also a `FlagSource`, so a YAML file or the environment can feed flags directly.
- [Build a Typed Configuration Loader](./typed-config-loader.md) loads the rest of the bookstore's settings.
