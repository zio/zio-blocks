// @ts-ignore Scala.js bundle does not ship TypeScript declarations
import * as scalaExports from "./scala-examples.js";

import { BaseAgent, agent } from "@golemcloud/golem-ts-sdk";

const scalaAgents: any = (scalaExports as any).__golemInternalScalaAgents ?? (globalThis as any).__golemInternalScalaAgents;

const __golemOpt = <T>(v: T | undefined | null): T | null => (v === undefined ? null : v);
const __golemUndef = <T>(v: T | null | undefined): T | undefined => (v === null ? undefined : v);

@agent({ name: "worker" })
class ScalaWorker extends BaseAgent {
  private readonly impl: any;

  constructor(arg0: string, arg1: number) {
    super();
    this.impl = scalaAgents.newWorker(arg0, arg1);
  }

  async reverse(arg0: string): Promise<string> {
    return await this.impl.reverse(arg0);
  }

  async handle(arg0: { name: string; count: number; note: string | null; flags: string[]; nested: { x: number; tags: string[] } }): Promise<{ shardName: string; shardIndex: number; reversed: string; payload: { name: string; count: number; note: string | null; flags: string[]; nested: { x: number; tags: string[] } } }> {
    return await this.impl.handle(arg0);
  }
}

@agent({ name: "coordinator" })
class ScalaCoordinator extends BaseAgent {
  private readonly impl: any;

  constructor() {
    super();
    this.impl = scalaAgents.newCoordinator();
  }

  async routeTyped(arg0: string, arg1: number, arg2: { name: string; count: number; note: string | null; flags: string[]; nested: { x: number; tags: string[] } }): Promise<{ shardName: string; shardIndex: number; reversed: string; payload: { name: string; count: number; note: string | null; flags: string[]; nested: { x: number; tags: string[] } } }> {
    return await this.impl.routeTyped(arg0, arg1, arg2);
  }

  async route(arg0: string, arg1: number, arg2: string): Promise<string> {
    return await this.impl.route(arg0, arg1, arg2);
  }
}
