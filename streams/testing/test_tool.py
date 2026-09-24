import json, sys, tempfile, unittest
from pathlib import Path

# Make the tests runnable both as `python test_tool.py` and from the repository
# root via unittest discovery.
sys.path.insert(0, str(Path(__file__).resolve().parent))
import tool

class ToolTest(unittest.TestCase):
    def census(self): return tool.load(tool.HERE/"census.json")
    def test_inventory_happy_and_final(self):
        c=self.census(); self.assertEqual(tool.validate_census(c),[])
        self.assertEqual(tool.validate_census(c,evidence={"executedTestIds":c["reservedIds"]},final=True),[])
    def test_inventory_rejects_duplicate_stale_na_reserved_and_unresolved(self):
        c=self.census(); c["entries"][1]["id"]=c["entries"][0]["id"]; c["entries"][1]["source"]="missing"; c["entries"][1]["benchmark"]="N/A"; c["entries"][1]["obligations"]=["NOT-RESERVED"]; c["reservedIds"].append(c["reservedIds"][0])
        text="\n".join(tool.validate_census(c,evidence={},final=True))
        for expected in ("duplicate","stale source","unexplained N/A","unreserved","stale reserved","unresolved"): self.assertIn(expected,text)
    def test_inventory_rejects_operation_matrix_gaps_and_stale_tests(self):
        c=self.census(); c["operationMatrices"][0]["first"]=1; c["operationMatrices"][1]["test"]="missing matrix test"
        text="\n".join(tool.validate_census(c))
        self.assertIn("every declared tag",text); self.assertIn("stale operation matrix test",text)
    def test_replay_all_branches(self):
        value=tool.load(tool.HERE/"replay-corpus/v1-smoke.json"); self.assertEqual(tool.validate_replay(value),[])
        self.assertEqual(tool.load_replay(json.dumps(value)),value)
        del value["seed"]; value["schema"]=2; value["schedule"]="bad"
        self.assertEqual(len(tool.validate_replay(value)),3)
        self.assertEqual(tool.validate_replay_corpus(),[])
    def test_campaign_floors_and_cells(self):
        campaigns=tool.load(tool.HERE/"campaigns.json"); self.assertEqual(tool.validate_campaigns(campaigns),[])
        campaigns["pr"]["samplesPerCell"]=9999; campaigns["cells"]=[]; campaigns["nightly"]["tags"]=[]
        errors="\n".join(tool.validate_campaigns(campaigns))
        self.assertIn("immutable floor",errors); self.assertIn("supported cells",errors); self.assertIn("no assigned tags",errors)
    def coverage_fixture(self, root):
        (root/"A.scala").write_text("object AsyncA {\n  def runAsync = 1\n}\n")
        config={"regions":[{"id":"a","source":"A.scala","startAnchor":"object AsyncA {","endAnchor":"}","rationale":"test"}],"classifications":[]}
        statement={"source":"A.scala","class":"AsyncA","method":"runAsync","line":2,"start":20,"end":30,"hit":1,"count":1,"branch":False}
        return config,statement
    def test_coverage_region_happy_and_uncovered_diagnostic(self):
        with tempfile.TemporaryDirectory() as td:
            config,statement=self.coverage_fixture(Path(td))
            self.assertEqual(tool.check_coverage({"statements":[statement]},config,Path(td)),[])
            statement["hit"]=0
            text="\n".join(tool.check_coverage({"statements":[statement]},config,Path(td)))
            for expected in ("uncovered statement","class=AsyncA","method=runAsync","line=2","span=20-30","hit=0/1"): self.assertIn(expected,text)
    def test_coverage_exact_record_classification_excludes_only_one_record(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); config,statement=self.coverage_fixture(root); statement["hit"]=0
            config["classifications"]=[{"id":"artifact","source":"A.scala","anchor":"  def runAsync = 1",
                "classification":"unreachable-scoverage-record","rationale":"Compiler-generated record with no source path.",
                "proof":"The fixture models a branch that cannot be entered from source.",
                "record":{"class":"AsyncA","method":"runAsync","line":2,"start":20,"end":30,"branch":False}}]
            neighbor={**statement,"start":31,"end":32}
            self.assertEqual(tool.check_coverage({"statements":[statement]},config,root),[])
            text="\n".join(tool.check_coverage({"statements":[statement,neighbor]},config,root))
            self.assertIn("span=31-32",text)
    def test_coverage_exact_record_line_disambiguates_repeated_anchor(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); config,statement=self.coverage_fixture(root); statement["hit"]=0
            (root/"A.scala").write_text("object AsyncA {\n  def runAsync = 1\n  def runAsync = 1\n}\n")
            config["classifications"]=[{"id":"artifact","source":"A.scala","anchor":"  def runAsync = 1",
                "classification":"unreachable-scoverage-record","rationale":"Compiler-generated record with no source path.",
                "proof":"The exact report record and source line identify this occurrence.",
                "record":{"class":"AsyncA","method":"runAsync","line":2,"start":20,"end":30,"branch":False}}]
            self.assertEqual(tool.check_coverage({"statements":[statement]},config,root),[])
            config["classifications"][0]["record"]["line"]=40
            self.assertIn("exact record anchor line mismatch", "\n".join(tool.check_coverage({"statements":[statement]},config,root)))
    def test_coverage_exact_record_classification_rejects_stale_ambiguous_and_broad_metadata(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); config,statement=self.coverage_fixture(root)
            classification={"id":"artifact","source":"A.scala","anchor":"  def runAsync = 1",
                "classification":"unreachable-scoverage-record","rationale":"Compiler-generated record with no source path.",
                "proof":"The fixture models a branch that cannot be entered from source.",
                "record":{"class":"AsyncA","method":"runAsync","line":2,"start":20,"end":30,"branch":False}}
            config["classifications"]=[classification]
            text="\n".join(tool.check_coverage({"statements":[]},config,root))
            self.assertIn("exact coverage record is stale, covered, or ambiguous",text)
            text="\n".join(tool.check_coverage({"statements":[statement,statement]},config,root))
            self.assertIn("exact coverage record is stale, covered, or ambiguous",text)
            classification["record"]={"line":2}
            text="\n".join(tool.check_coverage({"statements":[statement]},config,root))
            self.assertIn("invalid exact record classification",text)
    def test_coverage_grouped_exact_records_remain_individually_strict(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); config,statement=self.coverage_fixture(root); statement["hit"]=0
            config["recordClassifications"]=[{"id":"artifact-group","source":"A.scala","rationale":"compiler artifact",
                "proof":"The observable enclosing expression is covered.",
                "records":[["AsyncA","runAsync",2,20,30,False]]}]
            self.assertEqual(tool.check_coverage({"statements":[statement]},config,root),[])
            neighbor={**statement,"start":31,"end":32}
            text="\n".join(tool.check_coverage({"statements":[statement,neighbor]},config,root))
            self.assertIn("span=31-32",text)
            config["recordClassifications"][0]["records"][0][3]=19
            self.assertIn("grouped exact coverage record is stale", "\n".join(tool.check_coverage({"statements":[statement]},config,root)))
    def test_syntax_regions_exclude_sync_between_async_and_select_multiline_overload(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); source=root/"A.scala"
            source.write_text("object Owner {\n  def firstAsync = {\n    1\n  }\n  def sync = {\n    2\n  }\n  def drain(\n    reader: Reader.AsyncReader[_]\n  ): Unit = {\n    ()\n  }\n}\n")
            config={"sources":[{"source":"A.scala"}],"classifications":[]}
            regions,errors=tool.coverage_regions(config,root)
            self.assertEqual(errors,[])
            self.assertEqual([(r["name"],r["start"],r["end"]) for r in regions],[("firstAsync",2,4),("drain",8,12)])
            selected="\n".join(r["anchor"] for r in regions)
            self.assertNotIn("def sync",selected); self.assertIn("reader: Reader.AsyncReader[_]",selected)
    def test_explicit_coverage_selection_and_overall_floors(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); source=root/"A.scala"
            source.write_text("object Owner {\n  def firstAsync = 1\n  def critical = 2\n}\n")
            config={"sources":[{"source":"A.scala","explicitOnly":True,"helpers":["critical"]}],
                    "enforceCandidateMapping":False,"statementMinimum":50,"branchMinimum":50,"classifications":[]}
            regions,errors=tool.coverage_regions(config,root)
            self.assertEqual(errors,[])
            self.assertEqual([(r["name"],r["start"],r["end"]) for r in regions],[("critical",3,3)])
            statements=[
                {"source":"A.scala","class":"Owner","method":"firstAsync","line":2,"start":1,"end":2,"hit":0,"count":1,"branch":False},
                {"source":"A.scala","class":"Owner","method":"critical","line":3,"start":3,"end":4,"hit":1,"count":1,"branch":True},
            ]
            self.assertEqual(tool.check_coverage({"statements":statements},config,root),[])
            config["statementMinimum"]=51
            self.assertIn("statement coverage 50.00 below minimum 51.00",tool.check_coverage({"statements":statements},config,root))
            config["statementMinimum"]=50; config["branchMinimum"]=101
            self.assertIn("branch coverage 100.00 below minimum 101.00",tool.check_coverage({"statements":statements},config,root))
    def test_production_coverage_config_excludes_generic_pipeline_orchestration(self):
        config=tool.load(tool.HERE/"critical-coverage.json")
        selected=tool.selected_declarations(config)
        pipeline="streams/shared/src/main/scala/zio/blocks/streams/Pipeline.scala"
        self.assertFalse(any(declaration["source"] == pipeline for declaration in selected))
    def test_syntax_regions_reject_stale_unbalanced_and_empty(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            regions,errors=tool.coverage_regions({"sources":[{"source":"missing.scala"}]},root)
            self.assertEqual(regions,[]); self.assertIn("stale",errors[0])
            (root/"A.scala").write_text("def brokenAsync = {\n")
            regions,errors=tool.coverage_regions({"sources":[{"source":"A.scala"}]},root)
            self.assertEqual(regions,[]); self.assertIn("unbalanced",errors[0])
            (root/"A.scala").write_text("def sync = 1\n")
            regions,errors=tool.coverage_regions({"sources":[{"source":"A.scala"}]},root)
            self.assertEqual(regions,[]); self.assertIn("empty",errors[0])
    def test_coverage_rejects_stale_ambiguous_empty_overlap_and_unmapped(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); config,statement=self.coverage_fixture(root)
            config["regions"] += [
                {"id":"a","source":"A.scala","startAnchor":"missing","endAnchor":"}","rationale":"x"},
                {"id":"overlap","source":"A.scala","startAnchor":"  def runAsync = 1","endAnchor":"}","rationale":"x"}]
            text="\n".join(tool.check_coverage({"statements":[]},config,root))
            for expected in ("duplicate","missing","overlapping","empty coverage region"): self.assertIn(expected,text)
            config={"regions":[{"id":"tiny","source":"A.scala","startAnchor":"object AsyncA {","endAnchor":"object AsyncA {","rationale":"x"}],"classifications":[]}
            self.assertIn("unmapped async candidate","\n".join(tool.check_coverage({"statements":[{**statement,"line":1}]},config,root)))
    def test_scoverage_xml_preserves_exact_statement_metadata_and_zero_hits(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); source=root/"A.scala"; source.write_text("x")
            report=root/"scoverage.xml"
            report.write_text('<scoverage><statements>'
                f'<statement source="{source}" class="C" method="m" line="7" start="2" end="3" invocation-count="0" branch="true" ignored="false"/>'
                '</statements></scoverage>')
            old=tool.ROOT; tool.ROOT=root
            try:
                self.assertEqual(tool.load_coverage(report)["statements"],[{"source":"A.scala","class":"C","method":"m","line":7,"start":2,"end":3,"hit":0,"count":1,"branch":True}])
            finally: tool.ROOT=old
    def test_manifest_has_digest_and_hash(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/"out"; p.write_text("x"); m=tool.manifest("true",1,0,[p])
            self.assertEqual(m["exitStatus"],0); self.assertEqual(len(m["candidateDigest"]),64); self.assertIn(str(p),m["outputs"])
    def test_mutation_anchor_and_valid_kill(self):
        with tempfile.TemporaryDirectory() as td:
            target=Path(td)/"x"; target.write_text("anchor")
            old=tool.ROOT; tool.ROOT=Path(td)
            try:
                corpus={"mutations":[{"id":"M","target":"x","anchor":"anchor","replacement":"changed","killingTests":["T"],"equivalent":False}]}
                command="if grep -q changed x; then echo ASYNC_KILL:M:T; exit 1; else exit 0; fi"
                out=Path(td)/"result.json"; self.assertEqual(tool.run_mutations(corpus,command,out),0)
                target.write_text("anchor anchor"); self.assertEqual(tool.run_mutations(corpus,"false",out),1)
            finally: tool.ROOT=old

if __name__ == "__main__": unittest.main()
