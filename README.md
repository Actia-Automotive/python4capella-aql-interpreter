# Plugin `com.actia.capella.core.interpreter`

EASE module to evaluate **AQL** (Acceleo Query Language) expressions directly from **Python4Capella** scripts.

**Compatibility:** tested with Capella 7.0.1 and Capella Studio 7.0.0 (Sirius 7.4, EASE 0.8, Python4Capella) and Java 17.

> This project is developed by ACTIA Automotive SA. It is not affiliated with or endorsed by the Eclipse Foundation, the Eclipse Capella project or the Python4Capella project.

---

## Prerequisites

- Capella Studio with **Eclipse EASE** and **Python4Capella** installed
- PyDev configured in the Capella preferences
- Plugin installed in the target platform or in the `dropins/` folder

---

## Loading the module

```python
loadModule('/Capella/Interpreter')
```

Or through the provided Python stub (`python/interpreter.py`), which exposes the same functions in snake_case and also accepts Python4Capella simplified API objects (converted to their Java object).
Copy this file into the `simplified_api/` folder of the `Python4Capella` project in your workspace, then:

```python
include('workspace://Python4Capella/simplified_api/interpreter.py')
```

---

## API

Each method accepts two forms for its first parameter:

| Form | Description |
|---|---|
| `EObject` | The object is bound to `self` in the expression |
| `dict` { name → EObject } | All entries are injected as variables; the `"self"` key is mandatory and is used as the evaluation context |

The `aql:` prefix is added automatically when missing.

**Arguments must be Java objects (EMF `EObject`).** Python4Capella simplified API objects (e.g. the result of `model.get_system_engineering()`) are not accepted by Py4J: pass their `get_java_object()` instead, or use the snake_case stub functions (`evaluate_aql*`, see below), which do this conversion automatically. Results are Java objects too (e.g. `getName()` rather than `get_name()`).

---

### `evaluateAQL(element_or_variables, expression) → Object`

Returns the raw evaluation result (scalar, collection, or `None` on error).

```python
# Simple form
result = evaluateAQL(element, "aql:self.name")

# Form with variables
result = evaluateAQL({"self": aCapability, "constraint": aConstraint},
                     "aql:self.eAllContents()->select(e | e = constraint)")
```

---

### `evaluateAQLAsString(element_or_variables, expression) → String`

Returns the result as a string. Collections are joined with `", "`.

```python
# Simple form
name = evaluateAQLAsString(element, "aql:self.name")

# Form with variables
label = evaluateAQLAsString({"self": aComponent, "ref": aPort},
                             "aql:self.name + ' -> ' + ref.name")
```

---

### `evaluateAQLAsCollection(element_or_variables, expression) → List`

Always returns a list. A scalar is wrapped in a single-element list. Returns an empty list on error.

```python
# Simple form
children = evaluateAQLAsCollection(element, "aql:self.eContents()")

# Form with variables
matches = evaluateAQLAsCollection({"self": aPackage, "target": aClass},
                                   "aql:self.eContents()->select(e | e = target)")
```

---

## Examples

### Example 1 — Read the name of an element

```python
include('workspace://Python4Capella/simplified_api/capella.py')
loadModule('/Capella/Interpreter')

model = Model('MyProject/MyProject.aird')
se = model.get_system_engineering().get_java_object()

name = evaluateAQLAsString(se, "aql:self.name")
print("SystemEngineering name:", name)
```

---

### Example 2 — Iterate over direct children

```python
include('workspace://Python4Capella/simplified_api/capella.py')
loadModule('/Capella/Interpreter')

model = Model('MyProject/MyProject.aird')
se = model.get_system_engineering().get_java_object()

children = evaluateAQLAsCollection(se, "aql:self.eContents()")
print("Number of children:", len(children))
for child in children:
    print(" -", evaluateAQLAsString(child, "aql:self.name"))
```

---

### Example 3 — Query logical components

```python
include('workspace://Python4Capella/simplified_api/capella.py')
loadModule('/Capella/Interpreter')

model = Model('MyProject/MyProject.aird')
se = model.get_system_engineering().get_java_object()

components = evaluateAQLAsCollection(
    se,
    "aql:self.eAllContents(la::LogicalComponent)"
)
print("Logical components:", len(components))
for lc in components:
    print(" -", evaluateAQLAsString(lc, "aql:self.name"))
```

---

### Example 4 — Multiple variables: filter by constraint

```python
include('workspace://Python4Capella/simplified_api/capella.py')
loadModule('/Capella/Interpreter')

model = Model('MyProject/MyProject.aird')
se = model.get_system_engineering().get_java_object()

# Get an existing capability and constraint
capabilities = evaluateAQLAsCollection(se, "aql:self.eAllContents(oa::OperationalCapability)")
aCapability = capabilities[0]

constraints = evaluateAQLAsCollection(se, "aql:self.eAllContents(capella::Constraint)")
aConstraint = constraints[0]

# Check whether the constraint is contained in the capability
results = evaluateAQLAsCollection(
    {"self": aCapability, "constraint": aConstraint},
    "aql:self.eAllContents()->select(e | e = constraint)"
)
print("Constraint found:", len(results) > 0)
```

---

### Example 5 — Multiple variables: navigate between two related elements

```python
include('workspace://Python4Capella/simplified_api/capella.py')
loadModule('/Capella/Interpreter')

model = Model('MyProject/MyProject.aird')
se = model.get_system_engineering().get_java_object()

components = evaluateAQLAsCollection(se, "aql:self.eAllContents(la::LogicalComponent)")
source = components[0]
target = components[1]

# Expression using two named variables to compare properties
label = evaluateAQLAsString(
    {"self": source, "other": target},
    "aql:self.name + ' vs ' + other.name"
)
print(label)
```

---

### Example 6 — Raw result with `evaluateAQL`

```python
include('workspace://Python4Capella/simplified_api/capella.py')
loadModule('/Capella/Interpreter')

model = Model('MyProject/MyProject.aird')
se = model.get_system_engineering().get_java_object()

descriptor = ...  # DRepresentationDescriptor obtained from the session
result = evaluateAQL(descriptor, "aql:self.getLastModificationDate()")
if result is None:
    print("Evaluation error")
else:
    print("Last modification:", result)
```

---

### Example 7 — Using the `interpreter.py` stub

```python
include('workspace://Python4Capella/simplified_api/capella.py')
include('workspace://Python4Capella/simplified_api/interpreter.py')

model = Model('MyProject/MyProject.aird')
se = model.get_system_engineering().get_java_object()

# Simple form (snake_case)
name = evaluate_aql_as_string(se, "aql:self.name")

# Form with variables (snake_case)
capabilities = evaluate_aql_as_collection(se, "aql:self.eAllContents(oa::OperationalCapability)")
constraints  = evaluate_aql_as_collection(se, "aql:self.eAllContents(capella::Constraint)")

matches = evaluate_aql_as_collection(
    {"self": capabilities[0], "constraint": constraints[0]},
    "aql:self.eAllContents()->select(e | e = constraint)"
)
print("Found:", len(matches))
```

---

### Example 8 — Find OperationalActivities not `involved` in any OperationalCapability

```python
# se: Java SystemEngineering, obtained as in Example 1
allOperationalActivity = evaluateAQL(se, "aql:self.eAllContents(oa::OperationalActivity)")
print("count allOperationalActivity", len(allOperationalActivity))
for anOperationalActivity in allOperationalActivity:
    result = evaluateAQL(
        {"self":se, "a":anOperationalActivity},
        "aql:self.eAllContents(oa::OperationalCapability).eAllContents().involved->select(e|e=a)")
    if len(result) > 0:
        print("anOperationalActivity", len(result), anOperationalActivity.getName())
    else:
        print("anOperationalActivity not involved", anOperationalActivity.getName())
```

---

## Error handling

| Situation | `evaluateAQL` | `evaluateAQLAsString` | `evaluateAQLAsCollection` |
|---|---|---|---|
| AQL evaluation error | `None` | `None` | `[]` |
| `"self"` key missing from the dict | `None` | `None` | `[]` |

---

## Variables and lifecycle

When using the dictionary form:

1. All dict entries are injected into the interpreter through `setVariable()`.
2. The expression is evaluated with the `"self"` value as context.
3. All variables are released through `unSetVariable()` in a `finally` block, even on error.

Variables defined this way are visible in the AQL expression under their exact name (no prefix).

---

## Notes on AQL expressions

- The `aql:` prefix is optional: `"self.name"` and `"aql:self.name"` are equivalent. It is only added when the expression does not already start with a known prefix (`aql:`, `feature:`, `var:`, `service:`, `capella:`).
- The interpreter is taken from the Sirius session when available, otherwise from `CompoundInterpreter`.
- The `feature:` and `capella:` prefixes are also supported, but named variables only work with `aql:`.

| Prefix | Example | Description |
|---|---|---|
| `aql:` | `aql:self.name` | Acceleo Query Language — supports named variables |
| `feature:` | `feature:name` | Direct reference to an EMF attribute |
| `capella:` | `capella:someCategory` | Capella Semantic Browser query |

---

## Building the plugin

The `releng/build.sh` script builds the plugin jar without Maven, from a local Capella or Capella Studio installation:

```bash
releng/build.sh /path/to/capella
# or
CAPELLA_HOME=/path/to/capella releng/build.sh
```

- **Prerequisites:** bash (Git Bash on Windows) and Java 17+ on the `PATH`. The compiler is the one shipped with Capella (`org.eclipse.jdt.core.compiler.batch`); no JDK is required.
- **Classpath:** resolved from the `Require-Bundle` header of `MANIFEST.MF`, in the Capella `plugins/` folder (latest version of each bundle).
- **Version:** the `qualifier` of `Bundle-Version` is replaced by a UTC timestamp (e.g. `1.0.0.v20260929-0846`).
- **Content:** compiled classes and the files listed in `bin.includes` of `build.properties`.
- **Output:** `build/com.actia.capella.core.interpreter_<version>.jar`, to be copied into `<CAPELLA_HOME>/dropins/` before restarting Capella.

---

## Tests

Tests live in the `tests/com.actia.capella.core.interpreter.tests` fragment (JUnit 5):

| Class | Level | What is verified |
|---|---|---|
| `InterpreterModuleTest` | Unit | Module logic isolated from Sirius through `FakeInterpreter`: `aql:` prefix, String/list conversions, error handling, variable binding and release |
| `InterpreterModuleIntegrationTest` | Integration | Real Sirius interpreters (`aql:`, `feature:`) on an in-memory Ecore model |

**Running** (Capella Studio / Eclipse PDE, with a Capella target platform active):

1. Import both projects into the workspace: **File → Import… → Existing Projects into Workspace**, root = repository folder, with **Search for nested projects** checked and *Copy projects into workspace* unchecked.
2. Right-click `tests/com.actia.capella.core.interpreter.tests/InterpreterTests.launch` → **Run As → InterpreterTests**.

Tests must be run as a **JUnit Plug-in Test** (not a plain JUnit test): Sirius interpreters are discovered through the Eclipse extension registry.

**Launch configuration:** `InterpreterTests.launch` only starts the two repository projects and the target plug-ins they require (list computed for **Capella 7.0**), plus `org.eclipse.sirius.common.acceleo.aql`, which provides the `aql:` interpreter. With another Capella version, if the launch reports missing plug-ins:

1. **Run → Run Configurations… → InterpreterTests →** **Plug-ins** tab.
2. **Deselect All**, then check both workspace projects and, under *Target Platform*, `org.eclipse.sirius.common.acceleo.aql` (it is only declared through an extension point, so it is not detected automatically).
3. **Add Required Plug-ins**, then **Validate Plug-ins**, then **Apply**.

Keep *Include new workspace plug-ins automatically* unchecked: otherwise every workspace plug-in (including those of other projects) is launched and may break validation.

**Conventions:**
- Naming `method_condition_expectedResult` (e.g. `evaluateAQL_withVariablesWithoutSelf_returnsNull`).
- Arrange / Act / Assert structure; each test verifies a single behavior and must be able to fail.
- The package-private constructor `InterpreterModule(BiFunction<EObject, String, IInterpreter>)` allows injecting a test interpreter; the public constructor (used by EASE) keeps the default resolution.

---

## Python integration tests (EASE)

`tests/ease/test_aql_interpreter.py` is an EASE script that tests the installed plugin from Python4Capella, on a real Capella model. It covers what the JUnit tests cannot:

- calls from Python through Py4J (EObject and dict argument forms, conversion of results to Python values);
- a Capella model opened in a Sirius session (session interpreter);
- Capella metamodel qualifiers in AQL expressions (e.g. `la::LogicalArchitecture`);
- release of named variables on the shared session interpreter;
- the snake_case stub (`python/interpreter.py`), including Python4Capella wrapper objects.

| Test class | Tests | What is verified |
|---|---|---|
| `TestEObjectForm` | 8 | `self.name`, expression without prefix, Python `str` result, `eContents()`, `la::` qualifier, scalar wrapped in a list, `", "` join, `feature:` prefix |
| `TestDictForm` | 5 | Python dict passed through Py4J, named variables, `select` on a variable, variable released after evaluation, missing `"self"` key |
| `TestErrorHandling` | 4 | Syntax error and unknown variable return `None` / empty list, not an exception |
| `TestPythonWrapperForm` | 4 | Stub functions with Python4Capella wrappers (alone and in a dict), Java objects passed unchanged. **Skipped** if the stub is not installed |

Expected values are read from the model itself (Java API), so the script runs on **any Capella project** having a Logical Architecture (every project created with the Capella wizard has one), e.g. the *In-Flight Entertainment System* sample.

**Prerequisites** in the Capella instance running the script:

1. The plugin installed (jar built by `releng/build.sh` copied into `dropins/`, or a runtime instance launched from Capella Studio).
2. The `Python4Capella` project in the workspace.
3. Optional: the stub copied into `Python4Capella/simplified_api/interpreter.py` (otherwise the 4 stub tests are skipped).

**Running:**

1. Copy `tests/ease/test_aql_interpreter.py` into any project of the Capella workspace (or import this repository as a project).
2. Select any element of the Capella model to test in the Project Explorer or Model Explorer.
3. Right-click the script → **Run As → EASE Script**, or right-click the model element → **Test AQL interpreter module** (popup menu declared in the script header, available once the script folder is registered in *Preferences → Scripting → Script Locations*).
4. Headless alternative: pass the workspace-relative `.aird` path as first script argument (e.g. `/MyProject/MyProject.aird`).

Results are printed to the console by `unittest` (one line per test, then a summary). The script ends with an `AssertionError` if any test fails or errors, so that failures are detected in headless runs:

```
test_named_variable_is_released_after_evaluation (__main__.TestDictForm...) ... ok
...
Ran 21 tests in 0.060s

OK
All 21 tests passed
```

---

## Repository structure

```
├── plugins/com.actia.capella.core.interpreter/
│   ├── META-INF/MANIFEST.MF
│   ├── src/com/actia/capella/core/interpreter/
│   │   ├── Activator.java              ← OSGi lifecycle
│   │   └── InterpreterModule.java      ← EASE module (@WrapToScript)
│   ├── plugin.xml                      ← EASE extension registration
│   ├── build.properties
│   ├── plugin.properties
│   └── about.html
├── tests/com.actia.capella.core.interpreter.tests/
│   ├── META-INF/MANIFEST.MF            ← fragment (Fragment-Host)
│   ├── src/com/actia/capella/core/interpreter/
│   │   ├── FakeInterpreter.java        ← IInterpreter test double
│   │   ├── InterpreterModuleTest.java
│   │   └── InterpreterModuleIntegrationTest.java
│   └── InterpreterTests.launch         ← JUnit Plug-in Test configuration
├── tests/ease/
│   └── test_aql_interpreter.py         ← Python integration tests (EASE script)
├── python/
│   └── interpreter.py                  ← Python stub (snake_case API)
├── releng/
│   ├── build.sh                        ← plugin jar build
│   └── JarPacker.java                  ← packaging helper used by build.sh
├── LICENSE                             ← Eclipse Public License 2.0
└── README.md                           ← this file
```

Associated Python stub: `python/interpreter.py` (to be copied into `Python4Capella/simplified_api/`)

---

## Contributing

Bug reports and pull requests are welcome through the issue tracker and pull requests of this repository. Please run the JUnit tests (and, when possible, the EASE integration tests) before submitting a change.

---

## License

Copyright (c) 2026 ACTIA Automotive SA.

This program and the accompanying materials are made available under the terms of the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0) (see [LICENSE](LICENSE)).

SPDX-License-Identifier: EPL-2.0
