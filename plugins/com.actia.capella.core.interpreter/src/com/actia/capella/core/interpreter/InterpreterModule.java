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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.eclipse.ease.modules.WrapToScript;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.business.api.session.Session;
import org.eclipse.sirius.common.tools.api.interpreter.CompoundInterpreter;
import org.eclipse.sirius.common.tools.api.interpreter.EvaluationException;
import org.eclipse.sirius.common.tools.api.interpreter.IInterpreter;

/**
 * EASE module that exposes AQL expression evaluation to Python4Capella scripts.
 *
 * <p>Load in Python with:
 * <pre>
 * loadModule('/Capella/Interpreter')
 * </pre>
 *
 * <p>Both forms are accepted for the context parameter:
 * <ul>
 *   <li>A single {@link EObject} — bound to {@code self} in the expression.</li>
 *   <li>A {@link Map}&lt;String, EObject&gt; of named variables — the entry keyed
 *       {@code "self"} is the evaluation context; all entries are injected into the
 *       interpreter before evaluation and cleaned up afterwards.</li>
 * </ul>
 */
public class InterpreterModule {

    private static final String AQL_PREFIX = "aql:"; //$NON-NLS-1$
    private static final String SELF = "self"; //$NON-NLS-1$

    /** Expression prefixes handled by Sirius/Capella interpreters; left untouched by {@link #ensureAqlPrefix}. */
    private static final List<String> KNOWN_PREFIXES = List.of(
            AQL_PREFIX, "feature:", "var:", "service:", "capella:"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    private final BiFunction<EObject, String, IInterpreter> interpreterResolver;

    /**
     * Creates the module with the default interpreter resolution: the Sirius session
     * interpreter when the element belongs to a session, otherwise a new interpreter
     * from {@link CompoundInterpreter}.
     */
    public InterpreterModule() {
        this(InterpreterModule::getInterpreter);
    }

    /**
     * Creates the module with a custom interpreter resolution (used by tests).
     *
     * @param interpreterResolver returns the interpreter for a (self, expression) pair
     */
    InterpreterModule(BiFunction<EObject, String, IInterpreter> interpreterResolver) {
        this.interpreterResolver = interpreterResolver;
    }

    // -------------------------------------------------------------------------
    // evaluateAQL
    // -------------------------------------------------------------------------

    /**
     * Evaluates an AQL expression with {@code element} as the {@code self} context.
     * If the expression has no known prefix ({@code aql:}, {@code feature:}, {@code var:},
     * {@code service:}, {@code capella:}), the {@code "aql:"} prefix is added automatically.
     * Returns {@code null} on evaluation error.
     *
     * @param element    the EObject bound to {@code self}
     * @param expression the AQL expression (with or without the {@code "aql:"} prefix)
     * @return the raw result, or {@code null} on error
     */
    @WrapToScript
    public Object evaluateAQL(EObject element, String expression) {
        return doEvaluate(element, Collections.emptyMap(), expression);
    }

    /**
     * Evaluates an AQL expression with a set of named variables.
     *
     * <p>The map <strong>must</strong> contain a {@code "self"} key whose value is
     * the evaluation context. All entries are bound in the interpreter before
     * evaluation and released afterwards.
     *
     * <pre>
     * # Python example
     * evaluateAQL({"self": aCapability, "constraint": aConstraint},
     *             "aql:self.eAllContents()->select(e | e = constraint)")
     * </pre>
     *
     * @param variables  map of variable name → EObject; must contain {@code "self"}
     * @param expression the AQL expression (with or without the {@code "aql:"} prefix)
     * @return the raw result, or {@code null} if {@code "self"} is absent or on error
     */
    @WrapToScript
    public Object evaluateAQL(Map<String, EObject> variables, String expression) {
        EObject self = variables.get(SELF);
        if (self == null) {
            return null;
        }
        return doEvaluate(self, variables, expression);
    }

    // -------------------------------------------------------------------------
    // evaluateAQLAsString
    // -------------------------------------------------------------------------

    /**
     * Evaluates an AQL expression and returns the result as a String.
     * Collections are joined with {@code ", "}.
     *
     * @param element    the EObject bound to {@code self}
     * @param expression the AQL expression (with or without the {@code "aql:"} prefix)
     * @return the string representation of the result, or {@code null} on error
     */
    @WrapToScript
    public String evaluateAQLAsString(EObject element, String expression) {
        return toStringResult(evaluateAQL(element, expression));
    }

    /**
     * Evaluates an AQL expression with named variables and returns the result as a String.
     * Collections are joined with {@code ", "}.
     *
     * @param variables  map of variable name → EObject; must contain {@code "self"}
     * @param expression the AQL expression (with or without the {@code "aql:"} prefix)
     * @return the string representation of the result, or {@code null} on error
     */
    @WrapToScript
    public String evaluateAQLAsString(Map<String, EObject> variables, String expression) {
        return toStringResult(evaluateAQL(variables, expression));
    }

    // -------------------------------------------------------------------------
    // evaluateAQLAsCollection
    // -------------------------------------------------------------------------

    /**
     * Evaluates an AQL expression and returns the result as a List.
     * If the result is a scalar, it is wrapped in a single-element list.
     * Returns an empty list on error.
     *
     * @param element    the EObject bound to {@code self}
     * @param expression the AQL expression (with or without the {@code "aql:"} prefix)
     * @return the result as a list, never {@code null}
     */
    @WrapToScript
    public List<Object> evaluateAQLAsCollection(EObject element, String expression) {
        return toListResult(evaluateAQL(element, expression));
    }

    /**
     * Evaluates an AQL expression with named variables and returns the result as a List.
     * If the result is a scalar, it is wrapped in a single-element list.
     * Returns an empty list on error.
     *
     * @param variables  map of variable name → EObject; must contain {@code "self"}
     * @param expression the AQL expression (with or without the {@code "aql:"} prefix)
     * @return the result as a list, never {@code null}
     */
    @WrapToScript
    public List<Object> evaluateAQLAsCollection(Map<String, EObject> variables, String expression) {
        return toListResult(evaluateAQL(variables, expression));
    }

    // -------------------------------------------------------------------------
    // Core
    // -------------------------------------------------------------------------

    /**
     * Core evaluation: binds {@code variables} in the interpreter, evaluates the
     * expression against {@code self}, then releases all variables.
     */
    private Object doEvaluate(EObject self, Map<String, EObject> variables, String expression) {
        String expr = ensureAqlPrefix(expression);
        IInterpreter interpreter = interpreterResolver.apply(self, expr);
        try {
            for (Map.Entry<String, EObject> entry : variables.entrySet()) {
                interpreter.setVariable(entry.getKey(), entry.getValue());
            }
            return interpreter.evaluate(self, expr);
        } catch (EvaluationException e) {
            return null;
        } finally {
            for (String name : variables.keySet()) {
                interpreter.unSetVariable(name);
            }
        }
    }

    private static String toStringResult(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Collection) {
            return ((Collection<?>) result).stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", ")); //$NON-NLS-1$
        }
        return result.toString();
    }

    private static List<Object> toListResult(Object result) {
        if (result == null) {
            return Collections.emptyList();
        }
        if (result instanceof Collection) {
            return new ArrayList<>((Collection<?>) result);
        }
        return Collections.singletonList(result);
    }

    private static String ensureAqlPrefix(String expression) {
        if (expression == null || KNOWN_PREFIXES.stream().anyMatch(expression::startsWith)) {
            return expression;
        }
        return AQL_PREFIX + expression;
    }

    private static IInterpreter getInterpreter(EObject element, String expression) {
        return Session.of(element)
                .map(Session::getInterpreter)
                .orElseGet(() -> CompoundInterpreter.INSTANCE
                        .getProviderForExpression(expression)
                        .createInterpreter());
    }
}
