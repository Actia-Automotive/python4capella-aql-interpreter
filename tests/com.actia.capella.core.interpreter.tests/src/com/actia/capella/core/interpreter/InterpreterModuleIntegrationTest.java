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

import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests of {@link InterpreterModule} with the real Sirius interpreters
 * (AQL, feature). Must run as a JUnit Plug-in Test: interpreters are discovered
 * through the Eclipse extension registry.
 *
 * <p>The model is a small in-memory Ecore package, outside any Sirius session, so
 * the module falls back to {@code CompoundInterpreter}.
 */
class InterpreterModuleIntegrationTest {

    private InterpreterModule module;
    private EPackage library;
    private EClass book;
    private EClass author;

    @BeforeEach
    void setUp() {
        module = new InterpreterModule();

        library = EcoreFactory.eINSTANCE.createEPackage();
        library.setName("library");
        book = EcoreFactory.eINSTANCE.createEClass();
        book.setName("Book");
        author = EcoreFactory.eINSTANCE.createEClass();
        author.setName("Author");
        library.getEClassifiers().addAll(List.of(book, author));
    }

    @Test
    void evaluateAQLAsString_selfName_returnsName() {
        assertEquals("library", module.evaluateAQLAsString(library, "aql:self.name"));
    }

    @Test
    void evaluateAQLAsString_withoutPrefix_evaluatesAsAql() {
        assertEquals("library", module.evaluateAQLAsString(library, "self.name"));
    }

    @Test
    void evaluateAQLAsCollection_containmentReference_returnsChildrenInOrder() {
        assertEquals(List.of(book, author), module.evaluateAQLAsCollection(library, "aql:self.eClassifiers"));
    }

    @Test
    void evaluateAQLAsString_collectionOfNames_joinsWithCommaSeparator() {
        assertEquals("Book, Author", module.evaluateAQLAsString(library, "aql:self.eClassifiers.name"));
    }

    @Test
    void evaluateAQLAsString_withNamedVariable_resolvesVariable() {
        String result = module.evaluateAQLAsString(Map.of("self", library, "other", author),
                "aql:self.name + '/' + other.name");

        assertEquals("library/Author", result);
    }

    @Test
    void evaluateAQLAsCollection_selectOnNamedVariable_returnsMatchingElement() {
        List<Object> result = module.evaluateAQLAsCollection(Map.of("self", library, "target", author),
                "aql:self.eClassifiers->select(c | c = target)");

        assertEquals(List.of(author), result);
    }

    @Test
    void evaluateAQL_featurePrefix_readsAttributeValue() {
        assertEquals("library", module.evaluateAQL(library, "feature:name"));
    }

    @Test
    void evaluateAQL_syntaxError_returnsNull() {
        assertNull(module.evaluateAQL(library, "aql:self."));
    }

    @Test
    void evaluateAQLAsCollection_syntaxError_returnsEmptyList() {
        assertEquals(List.of(), module.evaluateAQLAsCollection(library, "aql:self."));
    }
}
