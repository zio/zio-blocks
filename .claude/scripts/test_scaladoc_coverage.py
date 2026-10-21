import importlib.util
import pathlib
import tempfile
import textwrap
import unittest

_spec = importlib.util.spec_from_file_location(
    "scaladoc_coverage", pathlib.Path(__file__).with_name("scaladoc-coverage.py")
)
sc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(sc)


class ScanTest(unittest.TestCase):
    def test_documented_and_undocumented_members(self):
        text = textwrap.dedent("""\
            /** A book. */
            final case class Book(title: String) {
              /** The title in upper case. */
              def loud: String = title.toUpperCase
              def quiet: String = title
            }
            """)
        self.assertEqual(
            [(d.name, d.line, d.doc is not None) for d in sc.scan(text)],
            [("class Book", 2, True), ("def loud", 4, True), ("def quiet", 5, False)],
        )

    def test_annotation_between_doc_and_declaration_counts_as_documented(self):
        text = '/** Old. */\n@deprecated("use b", "0.1")\ndef a: Int = 1\n'
        self.assertEqual([(d.name, d.doc is not None) for d in sc.scan(text)], [("def a", True)])

    def test_private_protected_override_and_nested_members_are_skipped(self):
        text = textwrap.dedent("""\
            object O {
              private[schema] def hidden = 1
              protected def prot = 1
              override def toString = "O"
              def outer = {
                  def inner = 1
                  inner
              }
            }
            """)
        self.assertEqual([d.name for d in sc.scan(text)], ["object O", "def outer"])

    def test_doc_text_is_normalised(self):
        text = "  /**\n   * Reads   a\n   * book.\n   */\n  def read = 1\n"
        self.assertEqual(sc.scan(text)[0].doc, "Reads a book.")


class PackageTest(unittest.TestCase):
    def test_package_of_drops_platform_and_version_dirs(self):
        self.assertEqual(sc.package_of("shared/src/main/scala-3/zio/blocks/schema/json/Json.scala"), "schema.json")
        self.assertEqual(sc.package_of("jvm/src/main/scala/zio/blocks/schema/Platform.scala"), "schema")


class DriftTest(unittest.TestCase):
    def test_drift_reports_copies_with_different_text(self):
        with tempfile.TemporaryDirectory() as root:
            for version, doc in (("scala-2", "Derives a schema."), ("scala-3", "Derives the schema.")):
                d = pathlib.Path(root, "shared/src/main", version, "zio/blocks/schema")
                d.mkdir(parents=True)
                (d / "X.scala").write_text(f"/** {doc} */\ndef derived = 1\n/** Same. */\ndef same = 1\n")
            self.assertEqual(
                sc.drift(root),
                ["shared/src/main/{scala-2,scala-3}/zio/blocks/schema/X.scala: def derived"],
            )

    def test_drift_compares_overloads_by_occurrence(self):
        with tempfile.TemporaryDirectory() as root:
            for version, first in (("scala-2", "One."), ("scala-3", "Uno.")):
                d = pathlib.Path(root, "shared/src/main", version, "zio/blocks/schema")
                d.mkdir(parents=True)
                (d / "Y.scala").write_text(f"/** {first} */\ndef f(a: Int) = 1\n/** Two. */\ndef f(a: String) = 2\n")
            self.assertEqual(
                sc.drift(root),
                ["shared/src/main/{scala-2,scala-3}/zio/blocks/schema/Y.scala: def f"],
            )
            for version, second in (("scala-2", "Two."), ("scala-3", "Dos.")):
                (pathlib.Path(root, "shared/src/main", version, "zio/blocks/schema") / "Y.scala").write_text(
                    f"/** Same. */\ndef f(a: Int) = 1\n/** {second} */\ndef f(a: String) = 2\n"
                )
            self.assertEqual(
                sc.drift(root),
                ["shared/src/main/{scala-2,scala-3}/zio/blocks/schema/Y.scala: def f #2"],
            )


if __name__ == "__main__":
    unittest.main()
