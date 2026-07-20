/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package matinilad.jmultidiskzip.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 *
 * @author Cien
 */
public class ArchivePathStream {

    private static final boolean LINUX_OS;

    static {
        String osName = System.getProperty("os.name");
        if (osName != null) {
            osName = osName.toLowerCase();
            LINUX_OS = osName.contains("nix") || osName.contains("nux") || osName.contains("aix");
        } else {
            LINUX_OS = false;
        }
    }

    public static class Entry {

        private final Path root;
        private final Path path;
        private final IOException error;
        
        public Entry(Path root, Path path, IOException error) {
            this.root = root;
            this.path = path;
            this.error = error;
        }

        public Path getRoot() {
            return root;
        }

        public Path getPath() {
            return path;
        }

        public IOException getError() {
            return error;
        }
    }

    private final Path[] inputs;

    public ArchivePathStream(Path[] input) {
        this.inputs = Objects.requireNonNull(input, "inputs is null").clone();
        for (int i = 0; i < this.inputs.length; i++) {
            Objects.requireNonNull(this.inputs[i], "input at index " + i + " is null");
        }
    }

    public Path[] getInputs() {
        return inputs.clone();
    }

    private void process(Set<String> names, Consumer<Entry> consumer, Path root, Path path) {
        if (root == null) {
            try {
                path = path.toRealPath();
            } catch (IOException ex) {
                consumer.accept(new Entry(root, path, ex));
                return;
            }

            root = path.getParent();
            if (root == null) {
                Stream<Path> stream;
                try {
                    stream = Files.list(path);
                } catch (IOException ex) {
                    consumer.accept(new Entry(root, path, ex));
                    return;
                }

                final Path finalRoot = path;
                stream.forEach((e) -> {
                    String name = e.getFileName().toString();
                    if (!LINUX_OS) {
                        name = name.toLowerCase();
                    }
                    if (!names.add(name)) {
                        consumer.accept(new Entry(finalRoot, e, new IOException("duplicated filename")));
                        return;
                    }
                    process(names, consumer, finalRoot, e);
                });
                return;
            }

            String name = path.getFileName().toString();
            if (!LINUX_OS) {
                name = name.toLowerCase();
            }
            if (!names.add(name)) {
                consumer.accept(new Entry(root, path, new IOException("duplicated filename")));
                return;
            }
        }

        if (Files.isDirectory(path)) {
            Stream<Path> stream;
            try {
                stream = Files.list(path);
            } catch (IOException ex) {
                consumer.accept(new Entry(root, path, ex));
                return;
            }

            final Path finalRoot = root;
            stream.forEach((e) -> {
                process(names, consumer, finalRoot, e);
            });
        }

        consumer.accept(new Entry(root, path, null));
    }

    public void stream(Consumer<Entry> consumer) {
        Set<String> names = new HashSet<>();
        for (Path e : this.inputs) {
            process(names, consumer, null, e);
        }
    }
}
