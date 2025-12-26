// @ts-ignore Scala.js bundle does not ship TypeScript declarations
import * as scalaExports from "./scala.js";

import { BaseAgent, agent } from "@golemcloud/golem-ts-sdk";

import "./user";

const scalaAgents: any = (scalaExports as any).scalaAgents ?? (globalThis as any).scalaAgents;

const __golemOpt = <T>(v: T | undefined | null): T | null => (v === undefined ? null : v);
const __golemUndef = <T>(v: T | null | undefined): T | undefined => (v === null ? undefined : v);


