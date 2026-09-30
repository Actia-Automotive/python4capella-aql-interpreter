/*******************************************************************************
 * Copyright (c) 2026 ACTIA Automotive SA.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Minimal jar packer used by {@code build.sh}, so the build only needs a Java runtime
 * (no {@code jar} or {@code zip} tool).
 *
 * <p>Usage: {@code java JarPacker <staging-dir> <output.jar>}. The staging directory must
 * contain {@code META-INF/MANIFEST.MF}; it is written as the first jar entry, as expected by
 * {@link java.util.jar.JarInputStream}.
 */
public final class JarPacker {

    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF"; //$NON-NLS-1$

    private JarPacker() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java JarPacker <staging-dir> <output.jar>"); //$NON-NLS-1$
            System.exit(2);
        }
        Path root = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        Manifest manifest;
        try (InputStream in = Files.newInputStream(root.resolve(MANIFEST_PATH))) {
            manifest = new Manifest(in);
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> !toEntryName(root, p).equals(MANIFEST_PATH))
                    .sorted()
                    .collect(Collectors.toList());
        }

        Files.createDirectories(output.toAbsolutePath().getParent());
        try (OutputStream out = Files.newOutputStream(output);
                JarOutputStream jar = new JarOutputStream(out, manifest)) {
            for (Path file : files) {
                jar.putNextEntry(new JarEntry(toEntryName(root, file)));
                Files.copy(file, jar);
                jar.closeEntry();
            }
        }
    }

    private static String toEntryName(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
