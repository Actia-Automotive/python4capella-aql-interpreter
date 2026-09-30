# -*- coding: utf-8 -*-
# name                 : Test AQL interpreter module
# script-type          : Python
# description          : Integration tests of the /Capella/Interpreter EASE module on the selected Capella model
# popup                : enableFor(org.polarsys.capella.core.data.capellacore.CapellaElement)
#
# Copyright (c) 2026 ACTIA Automotive SA.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
'''
Integration tests of the com.actia.capella.core.interpreter plugin, run from
Python4Capella on a real Capella model.

Complements the JUnit tests of the plugin by covering what they cannot:
  - calls from Python through Py4J (EObject and dict argument forms,
    conversion of results to Python values),
  - a real Capella model opened in a Sirius session (session interpreter),
  - Capella metamodel qualifiers in AQL expressions (e.g. la::LogicalArchitecture),
  - release of named variables on the shared session interpreter,
  - the snake_case stub (python/interpreter.py), which also accepts
    Python4Capella wrapper objects; its tests are skipped if it is not installed.

Tests only rely on invariants of any Capella project (SystemEngineering
structure), so they can run on any model.

Usage: select any element of a Capella model, then right-click > Test AQL
interpreter module (or run from the Script Console). The .aird path can also
be given as first script argument (headless execution). Results are printed
to the console; the script raises an error if any test fails.

Requires only Python4Capella and the plugin; see the "Python integration
tests (EASE)" section of the plugin README.
'''

import sys
import unittest

if False:
    from simplified_api.capella import *
    from utilities.CapellaPlatform import *
    from simplified_api.interpreter import *

include('workspace://Python4Capella/simplified_api/capella.py')
include('workspace://Python4Capella/utilities/CapellaPlatform.py')
loadModule('/Capella/Interpreter')

# Optional snake_case stub of the plugin, tested by TestPythonWrapperForm when installed
_STUB_PATH = 'workspace://Python4Capella/simplified_api/interpreter.py'
try:
    include(_STUB_PATH)
    _stub_available = True
except Exception:
    _stub_available = False

# Java SystemEngineering under test, set by run_aql_interpreter_tests()
_se = None
# Python4Capella wrapper of the same SystemEngineering (simplified API object)
_se_wrapper = None


class TestEObjectForm(unittest.TestCase):
    """Single EObject argument form: the element is bound to self."""

    def test_evaluate_aql_as_string_returns_name(self):
        self.assertEqual(_se.getName(), evaluateAQLAsString(_se, "aql:self.name"))

    def test_expression_without_prefix_is_evaluated_as_aql(self):
        self.assertEqual(_se.getName(), evaluateAQLAsString(_se, "self.name"))

    def test_evaluate_aql_returns_python_string_for_attribute(self):
        result = evaluateAQL(_se, "aql:self.name")

        self.assertIsInstance(result, str)
        self.assertEqual(_se.getName(), result)

    def test_evaluate_aql_as_collection_returns_all_children(self):
        result = evaluateAQLAsCollection(_se, "aql:self.eContents()")

        self.assertEqual(_se.eContents().size(), len(result))

    def test_capella_metaclass_qualifier_selects_logical_architecture(self):
        result = evaluateAQLAsCollection(_se, "aql:self.eContents(la::LogicalArchitecture)")

        expected = _se.getContainedLogicalArchitectures()
        self.assertEqual(expected.size(), len(result))
        self.assertEqual(expected.get(0), result[0])

    def test_evaluate_aql_as_collection_wraps_scalar_in_list(self):
        result = evaluateAQLAsCollection(_se, "aql:self.name")

        self.assertEqual([_se.getName()], list(result))

    def test_evaluate_aql_as_string_joins_collection_with_comma(self):
        names = [child.getName() for child in _se.getContainedLogicalArchitectures()]

        result = evaluateAQLAsString(_se, "aql:self.eContents(la::LogicalArchitecture).name")

        self.assertEqual(", ".join(names), result)

    def test_feature_prefix_reads_attribute(self):
        self.assertEqual(_se.getName(), evaluateAQL(_se, "feature:name"))


class TestDictForm(unittest.TestCase):
    """Dict argument form: named variables, "self" entry as evaluation context."""

    def test_named_variable_is_resolved(self):
        la = _se.getContainedLogicalArchitectures().get(0)

        result = evaluateAQLAsString({"self": _se, "other": la}, "aql:self.name + '/' + other.name")

        self.assertEqual(_se.getName() + "/" + la.getName(), result)

    def test_select_on_named_variable_returns_matching_element(self):
        la = _se.getContainedLogicalArchitectures().get(0)

        result = evaluateAQLAsCollection({"self": _se, "target": la},
                                         "aql:self.eContents()->select(e | e = target)")

        self.assertEqual(1, len(result))
        self.assertEqual(la, result[0])

    def test_named_variable_is_released_after_evaluation(self):
        la = _se.getContainedLogicalArchitectures().get(0)
        evaluateAQL({"self": _se, "aqlTestReleasedVar": la}, "aql:aqlTestReleasedVar.name")

        # The session interpreter is shared: a leaked variable would still resolve here
        self.assertIsNone(evaluateAQL(_se, "aql:aqlTestReleasedVar.name"))

    def test_missing_self_key_returns_none(self):
        self.assertIsNone(evaluateAQL({"other": _se}, "aql:self.name"))

    def test_missing_self_key_returns_empty_collection(self):
        self.assertEqual(0, len(evaluateAQLAsCollection({"other": _se}, "aql:self.name")))


class TestErrorHandling(unittest.TestCase):
    """Evaluation errors are reported as None / empty list, not as exceptions."""

    def test_syntax_error_returns_none(self):
        self.assertIsNone(evaluateAQL(_se, "aql:self."))

    def test_syntax_error_as_string_returns_none(self):
        self.assertIsNone(evaluateAQLAsString(_se, "aql:self."))

    def test_syntax_error_as_collection_returns_empty_list(self):
        self.assertEqual(0, len(evaluateAQLAsCollection(_se, "aql:self.")))

    def test_unknown_variable_returns_none(self):
        self.assertIsNone(evaluateAQL(_se, "aql:aqlTestUndefinedVar.name"))


class TestPythonWrapperForm(unittest.TestCase):
    """snake_case stub (python/interpreter.py of the plugin): Python4Capella wrapper
    objects (e.g. model.get_system_engineering()) are accepted, alone or as dict
    values. The raw camelCase functions only accept Java objects.

    Skipped when the stub is not installed in Python4Capella/simplified_api/.
    """

    def setUp(self):
        if not _stub_available:
            self.skipTest("stub not found: " + _STUB_PATH)

    def test_wrapper_as_element_is_evaluated(self):
        self.assertEqual(_se.getName(), evaluate_aql_as_string(_se_wrapper, "aql:self.name"))

    def test_wrapper_as_dict_value_is_evaluated(self):
        la = _se.getContainedLogicalArchitectures().get(0)

        result = evaluate_aql_as_string({"self": _se_wrapper, "other": la},
                                        "aql:self.name + '/' + other.name")

        self.assertEqual(_se.getName() + "/" + la.getName(), result)

    def test_java_object_is_passed_unchanged(self):
        self.assertEqual(_se.getName(), evaluate_aql_as_string(_se, "aql:self.name"))

    def test_wrapper_in_collection_query_returns_java_objects(self):
        result = evaluate_aql_as_collection(_se_wrapper, "aql:self.eContents(la::LogicalArchitecture)")

        self.assertEqual(_se.getContainedLogicalArchitectures().get(0), result[0])


def run_aql_interpreter_tests(aird_path):
    """Run all tests on the model at aird_path; return the unittest result."""
    global _se, _se_wrapper
    model = CapellaModel()
    model.open(aird_path)
    _se_wrapper = model.get_system_engineering()
    _se = _se_wrapper.get_java_object()
    print("Testing /Capella/Interpreter on model", _se.getName(), "(" + aird_path + ")")

    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    for test_class in (TestEObjectForm, TestDictForm, TestErrorHandling, TestPythonWrapperForm):
        suite.addTests(loader.loadTestsFromTestCase(test_class))

    result = unittest.TextTestRunner(stream=sys.stdout, verbosity=2).run(suite)
    if result.wasSuccessful():
        print("All", result.testsRun, "tests passed")
    else:
        print(len(result.failures), "failure(s),", len(result.errors), "error(s) out of",
              result.testsRun, "tests")
    return result


def resolve_aird_path():
    """Return the workspace-relative .aird path to test.

    Priority: first script argument ending with .aird (headless execution),
    then the Capella model of the current selection.
    """
    try:
        if argv and len(argv) > 0 and str(argv[0]).endswith('.aird'):
            path = str(argv[0])
            return path if path.startswith('/') else '/' + path
    except NameError:
        pass
    if len(sys.argv) > 1 and sys.argv[1].endswith('.aird'):
        path = sys.argv[1]
        return path if path.startswith('/') else '/' + path
    selected = CapellaPlatform.getFirstSelectedElement()
    if selected is None:
        raise RuntimeError("Select an element of a Capella model, or pass the .aird path as script argument")
    return '/' + CapellaPlatform.getModelPath(CapellaElement(selected))


# --- Entry point --------------------------------------------------------------

_result = run_aql_interpreter_tests(resolve_aird_path())
if not _result.wasSuccessful():
    raise AssertionError("AQL interpreter integration tests failed")
