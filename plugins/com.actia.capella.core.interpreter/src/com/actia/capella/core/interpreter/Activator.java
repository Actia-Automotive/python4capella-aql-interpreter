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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

  public static final String PLUGIN_ID = "com.actia.capella.core.interpreter"; //$NON-NLS-1$

  private static BundleContext context;

  static BundleContext getContext() {
    return context;
  }

  @Override
  public void start(BundleContext bundleContext) throws Exception {
    Activator.context = bundleContext;
  }

  @Override
  public void stop(BundleContext bundleContext) throws Exception {
    Activator.context = null;
  }
}
