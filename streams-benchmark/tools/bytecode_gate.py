#!/usr/bin/env python3
"""Deterministic structural bytecode comparison; no runtime review escape hatch."""
import argparse,json,re
from pathlib import Path

DEFAULT=[r"\bnew\b",r"anewarray",r"newarray",r"scala/runtime/BoxesRunTime\.boxTo",r"invokeinterface",r"invokevirtual"]
def normalized(text,renames):
 for pair in renames: text=text.replace(pair["new"],pair["old"])
 return "\n".join(line.strip() for line in text.splitlines() if line.strip() and not line.lstrip().startswith(("Compiled from","SHA256")))
def hot_path(text):
 """Exclude exception handlers from the synchronous steady-state loop budget."""
 lines=text.splitlines(); handler_offsets=set(); in_table=False
 for line in lines:
  stripped=line.strip()
  if stripped=="Exception table:": in_table=True; continue
  if in_table:
   match=re.match(r"\d+\s+\d+\s+(\d+)\s+",stripped)
   if match: handler_offsets.add(int(match.group(1)))
 if not handler_offsets:return text
 out=[]; skipping=False
 for line in lines:
  match=re.match(r"\s*(\d+):\s+(.*)",line)
  if match and int(match.group(1)) in handler_offsets: skipping=True
  if not skipping:out.append(line)
  elif match and re.match(r"(?:a|i|l|f|d)?return|athrow\b",match.group(2)):
   skipping=False
 return "\n".join(out)
def methods(text,names):
 if not names:return text
 lines=text.splitlines(); out=[]; keep=False
 for line in lines:
  if line.startswith("  ") and not line.startswith("    ") and line.rstrip().endswith(";"):
   keep=any(name in line for name in names)
  if keep:out.append(line)
 return "\n".join(out)
def main():
 p=argparse.ArgumentParser();p.add_argument("baseline");p.add_argument("candidate");p.add_argument("--allowlist",required=True);p.add_argument("--patterns");p.add_argument("--predicate-manifest");p.add_argument("--method",action="append",default=[]);a=p.parse_args()
 allow=json.loads(Path(a.allowlist).read_text())
 if set(allow)!={"oldToNew","allowedPatterns"}: raise ValueError("allowlist requires exactly oldToNew and allowedPatterns")
 for pair in allow["oldToNew"]:
  if set(pair)!={"old","new"}: raise ValueError("rename entries require exactly old/new")
 patterns=json.loads(Path(a.patterns).read_text()) if a.patterns else DEFAULT
 baseline_text=Path(a.baseline).read_text(); candidate_text=Path(a.candidate).read_text()
 selected=a.method or [None]; allowed=set(allow["allowedPatterns"]); hits=[]; identical=True
 for method in selected:
  names=[] if method is None else [method]
  base=normalized(methods(baseline_text,names),allow["oldToNew"])
  cand=normalized(methods(candidate_text,names),allow["oldToNew"])
  identical=identical and base==cand
  for pattern in patterns:
   delta=len(re.findall(pattern,hot_path(cand)))-len(re.findall(pattern,hot_path(base)))
   if delta>0 and pattern not in allowed:hits.append({"method":method or "<all>","pattern":pattern,"additional":delta})
 candidate_selected=normalized(methods(candidate_text,a.method),allow["oldToNew"])
 if a.predicate_manifest:
  manifest=json.loads(Path(a.predicate_manifest).read_text())
  for row in manifest.get("micro",[]):
   for symbol in row.get("budget",{}).get("prohibited",[]):
    if symbol and symbol in candidate_selected:hits.append({"row":row["id"],"prohibited":symbol})
 verdict="pass" if not hits else "regression"
 print(json.dumps({"baseline":a.baseline,"candidate":a.candidate,"identical":identical,"hits":hits,"verdict":verdict},indent=2))
 raise SystemExit(verdict!="pass")
if __name__=="__main__":main()
