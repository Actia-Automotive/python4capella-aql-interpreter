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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.sirius.common.tools.api.interpreter.EvaluationException;
import org.eclipse.sirius.common.tools.api.interpreter.IInterpreter;

/**
 * Test double for {@link IInterpreter}: records evaluations and variable bindings,
 * and returns a configured result or throws an {@link EvaluationException}.
 *
 * <p>Only {@code evaluate}, {@code setVariable} and {@code unSetVariable} are supported;
 * any other interpreter method fails the test with {@link UnsupportedOperationException}.
 */
final class FakeInterpreter implements InvocationHandler {

    /** Expressions received by {@code evaluate}, in call order. */
    final List<String> evaluatedExpressions = new ArrayList<>();

    /** Targets received by {@code evaluate}, in call order. */
    final List<EObject> evaluatedTargets = new ArrayList<>();

    /** Variables currently bound in the interpreter. */
    final Map<String, Object> boundVariables = new LinkedHashMap<>();

    /** Snapshot of the bound variables taken at the last {@code evaluate} call. */
    Map<String, Object> variablesAtEvaluation;

    private Object result;
    private boolean failing;

    /** Makes {@code evaluate} return {@code value}. */
    FakeInterpreter returning(Object value) {
        this.result = value;
        this.failing = false;
        return this;
    }

    /** Makes {@code evaluate} throw an {@link EvaluationException}. */
    FakeInterpreter failing() {
        this.failing = true;
        return this;
    }

    /**
     * Returns an {@link IInterpreter} backed by this fake.
     *
     * <p>The proxy is defined in the class loader of {@link IInterpreter}'s bundle: under OSGi,
     * only that loader sees every type used in the interface signatures (e.g. {@code ModelAccessor}).
     */
    IInterpreter asInterpreter() {
        return (IInterpreter) Proxy.newProxyInstance(IInterpreter.class.getClassLoader(),
                new Class<?>[] { IInterpreter.class }, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        switch (method.getName()) {
        case "evaluate": //$NON-NLS-1$
            evaluatedTargets.add((EObject) args[0]);
            evaluatedExpressions.add((String) args[1]);
            variablesAtEvaluation = new LinkedHashMap<>(boundVariables);
            if (failing) {
                throw new EvaluationException("simulated evaluation error"); //$NON-NLS-1$
            }
            return result;
        case "setVariable": //$NON-NLS-1$
            boundVariables.put((String) args[0], args[1]);
            return null;
        case "unSetVariable": //$NON-NLS-1$
            boundVariables.remove(args[0]);
            return null;
        default:
            throw new UnsupportedOperationException(method.getName());
        }
    }
}
