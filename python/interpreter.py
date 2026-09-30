# Copyright (c) 2026 ACTIA Automotive SA.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0

# Load the EASE module
loadModule('/Capella/Interpreter')

# Simplified API (snake_case wrappers)
#
# Unlike the raw EASE functions (evaluateAQL...), these functions also accept
# Python4Capella simplified API objects (e.g. model.get_system_engineering()),
# alone or as dict values: they are converted to their Java object before the call.


def _unwrap(value):
    """Returns the Java object of a Python4Capella wrapper, recursing into dict values."""
    if isinstance(value, dict):
        return {key: _unwrap(item) for key, item in value.items()}
    # Python4Capella wrappers (java_api.Java_API.JavaObject) store their Java object in the
    # 'java_object' instance attribute. hasattr() cannot be used: Py4J Java objects report
    # any attribute name as present, since Java methods are resolved on demand.
    if 'java_object' in getattr(value, '__dict__', ()):
        return value.get_java_object()
    return value


def evaluate_aql(element_or_variables, expression):
    """
    Evaluates an AQL expression.

    Accepted forms:
      evaluate_aql(eObject, "aql:self.name")
      evaluate_aql({"self": eObject, "other": anotherObject}, "aql:self.foo(other)")

    Elements may be Java EObjects or Python4Capella wrappers.
    Returns the raw result, or None on error.
    """
    return evaluateAQL(_unwrap(element_or_variables), expression)


def evaluate_aql_as_string(element_or_variables, expression):
    """
    Evaluates an AQL expression and returns the result as a string.
    Collections are joined with ", ".

    Accepted forms:
      evaluate_aql_as_string(eObject, "aql:self.name")
      evaluate_aql_as_string({"self": eObject, "ref": anotherObject}, "aql:self.foo(ref)")

    Elements may be Java EObjects or Python4Capella wrappers.
    Returns None on error.
    """
    return evaluateAQLAsString(_unwrap(element_or_variables), expression)


def evaluate_aql_as_collection(element_or_variables, expression):
    """
    Evaluates an AQL expression and returns the result as a list.
    A scalar is wrapped in a single-element list.
    Returns an empty list on error.

    Accepted forms:
      evaluate_aql_as_collection(eObject, "aql:self.eContents()")
      evaluate_aql_as_collection({"self": eObject, "ref": anotherObject}, "aql:self.foo(ref)")

    Elements may be Java EObjects or Python4Capella wrappers.
    """
    return evaluateAQLAsCollection(_unwrap(element_or_variables), expression)
