package app.veil.design;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads the module's own source tree; Gradle runs unit tests with the module directory as cwd. */
final class Sources {

    static final Path MAIN = Paths.get("src", "main");
    static final Path RES = MAIN.resolve("res");

    private Sources() {
    }

    static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    static List<Path> filesUnder(Path dir, String suffix) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(suffix)).sorted().forEach(out::add);
        }
        return out;
    }

    /** The name attributes of every &lt;color&gt; in a values file, in file order. */
    static List<String> colorNames(Path file) throws IOException {
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("<color\\s+name=\"([^\"]+)\"").matcher(read(file));
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}
