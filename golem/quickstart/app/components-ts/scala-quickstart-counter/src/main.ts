// @ts-ignore Scala.js bundle does not ship TypeScript declarations
import * as scalaExports from "./scala-quickstart.js";

import { BaseAgent, agent } from "@golemcloud/golem-ts-sdk";

const scalaAgents: any = (scalaExports as any).__golemInternalScalaAgents ?? (globalThis as any).__golemInternalScalaAgents;

const __golemOpt = <T>(v: T | undefined | null): T | null => (v === undefined ? null : v);
const __golemUndef = <T>(v: T | null | undefined): T | undefined => (v === null ? undefined : v);

@agent({ name: "shard-agent" })
class ScalaShardAgent extends BaseAgent {
  private readonly impl: any;

  constructor(arg0: string, arg1: number) {
    super();
    this.impl = scalaAgents.newShardAgent(arg0, arg1);
  }

  async get(arg0: string): Promise<string | null> {
    return await this.impl.get(arg0);
  }

  async id(): Promise<number> {
    return await this.impl.id();
  }

  async set(arg0: string, arg1: string): Promise<void> {
    return await this.impl.set(arg0, arg1);
  }
}

@agent({ name: "counter-agent" })
class ScalaCounterAgent extends BaseAgent {
  private readonly impl: any;

  constructor(input: string) {
    super();
    this.impl = scalaAgents.newCounterAgent(input);
  }

  async increment(): Promise<number> {
    return await this.impl.increment();
  }
}

