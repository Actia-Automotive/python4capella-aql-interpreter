/*******************************************************************************
 * Copyright (c) 2026 ACTIA Automotive SA.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.actia.capella.core.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcoreFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of {@link InterpreterModule} logic, isolated from the real Sirius
 * interpreters through {@link FakeInterpreter}.
 */
class InterpreterModuleTest {

    private FakeInterpreter fake;
    private InterpreterModule module;
    private EObject self;
    private EObject other;

    @BeforeEach
    void setUp() {
        fake = new FakeInterpreter();
        module = new InterpreterModule((target, expression) -> fake.asInterpreter());
        self = EcoreFactory.eINSTANCE.createEPackage();
        other = EcoreFactory.eINSTANCE.createEClass();
    }

    // -------------------------------------------------------------------------
    // Expression prefix
    // -------------------------------------------------------------------------

    @Test
    void evaluateAQL_withoutPrefix_addsAqlPrefix() {
        module.evaluateAQL(self, "self.name");

        assertEquals(List.of("aql:self.name"), fake.evaluatedExpressions);
    }

    @Test
    void evaluateAQL_withAqlPrefix_keepsExpressionUnchanged() {
        module.evaluateAQL(self, "aql:self.name");

        assertEquals(List.of("aql:self.name"), fake.evaluatedExpressions);
    }

    @Test
    void evaluateAQL_withFeaturePrefix_keepsExpressionUnchanged() {
        module.evaluateAQL(self, "feature:name");

        assertEquals(List.of("feature:name"), fake.evaluatedExpressions);
    }

    @Test
    void evaluateAQL_withCapellaPrefix_keepsExpressionUnchanged() {
        module.evaluateAQL(self, "capella:someCategory");

        assertEquals(List.of("capella:someCategory"), fake.evaluatedExpressions);
    }

    // -------------------------------------------------------------------------
    // evaluateAQL — single element form
    // -------------------------------------------------------------------------

    @Test
    void evaluateAQL_withElement_evaluatesAgainstElement() {
        module.evaluateAQL(self, "self.name");

        assertEquals(1, fake.evaluatedTargets.size());
        assertSame(self, fake.evaluatedTargets.get(0));
    }

    @Test
    void evaluateAQL_withElement_returnsRawInterpreterResult() {
        Object expected = new Object();
        fake.returning(expected);

        Object result = module.evaluateAQL(self, "self.name");

        assertSame(expected, result);
    }

    @Test
    void evaluateAQL_onEvaluationError_returnsNull() {
        fake.failing();

        assertNull(module.evaluateAQL(self, "self.name"));
    }

    // -------------------------------------------------------------------------
    // evaluateAQL — variables form
    // -------------------------------------------------------------------------

    @Test
    void evaluateAQL_withVariables_evaluatesAgainstSelfEntry() {
        module.evaluateAQL(Map.of("self", self, "other", other), "self.name");

        assertEquals(1, fake.evaluatedTargets.size());
        assertSame(self, fake.evaluatedTargets.get(0));
    }

    @Test
    void evaluateAQL_withVariables_bindsAllVariablesDuringEvaluation() {
        module.evaluateAQL(Map.of("self", self, "other", other), "self.name");

        assertEquals(Map.of("self", self, "other", other), fake.variablesAtEvaluation);
    }

    @Test
    void evaluateAQL_withVariables_unbindsVariablesAfterEvaluation() {
        module.evaluateAQL(Map.of("self", self, "other", other), "self.name");

        assertTrue(fake.boundVariables.isEmpty(), "variables left bound: " + fake.boundVariables);
    }

    @Test
    void evaluateAQL_withVariables_unbindsVariablesEvenOnError() {
        fake.failing();

        module.evaluateAQL(Map.of("self", self, "other", other), "self.name");

        assertTrue(fake.boundVariables.isEmpty(), "variables left bound: " + fake.boundVariables);
    }

    @Test
    void evaluateAQL_withVariablesWithoutSelf_returnsNullWithoutEvaluating() {
        fake.returning("unexpected");

        Object result = module.evaluateAQL(Map.of("other", other), "self.name");

        assertNull(result);
        assertTrue(fake.evaluatedExpressions.isEmpty());
    }

    // -------------------------------------------------------------------------
    // evaluateAQLAsString
    // -------------------------------------------------------------------------

    @Test
    void evaluateAQLAsString_withCollectionResult_joinsWithCommaSeparator() {
        fake.returning(List.of("Book", "Author"));

        assertEquals("Book, Author", module.evaluateAQLAsString(self, "self.eClassifiers.name"));
    }

    @Test
    void evaluateAQLAsString_withScalarResult_returnsItsStringValue() {
        fake.returning(42);

        assertEquals("42", module.evaluateAQLAsString(self, "self.eClassifiers->size()"));
    }

    @Test
    void evaluateAQLAsString_onEvaluationError_returnsNull() {
        fake.failing();

        assertNull(module.evaluateAQLAsString(self, "self.name"));
    }

    @Test
    void evaluateAQLAsString_withVariablesWithoutSelf_returnsNull() {
        assertNull(module.evaluateAQLAsString(Map.of("other", other), "self.name"));
    }

    // -------------------------------------------------------------------------
    // evaluateAQLAsCollection
    // -------------------------------------------------------------------------

    @Test
    void evaluateAQLAsCollection_withScalarResult_wrapsItInSingleElementList() {
        fake.returning("library");

        assertEquals(List.of("library"), module.evaluateAQLAsCollection(self, "self.name"));
    }

    @Test
    void evaluateAQLAsCollection_withCollectionResult_returnsElementsInOrder() {
        fake.returning(new LinkedHashSet<>(List.of("Book", "Author")));

        assertEquals(List.of("Book", "Author"), module.evaluateAQLAsCollection(self, "self.eClassifiers.name"));
    }

    @Test
    void evaluateAQLAsCollection_withNullResult_returnsEmptyList() {
        fake.returning(null);

        assertEquals(List.of(), module.evaluateAQLAsCollection(self, "self.nsURI"));
    }

    @Test
    void evaluateAQLAsCollection_onEvaluationError_returnsEmptyList() {
        fake.failing();

        assertEquals(List.of(), module.evaluateAQLAsCollection(self, "self.name"));
    }

    @Test
    void evaluateAQLAsCollection_withVariablesWithoutSelf_returnsEmptyList() {
        assertEquals(List.of(), module.evaluateAQLAsCollection(Map.of("other", other), "self.name"));
    }
}
