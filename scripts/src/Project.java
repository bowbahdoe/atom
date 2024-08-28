import dev.mccue.tools.Tool;
import dev.mccue.tools.googlejavaformat.GoogleJavaFormat;
import dev.mccue.tools.jstage.JStage;
import dev.mccue.tools.jar.Jar;
import dev.mccue.tools.javac.Javac;
import dev.mccue.tools.javadoc.Javadoc;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.io.File;

@CommandLine.Command(name = "project")
public class Project {
    static final Path GOOGLE_JAVA_FORMAT_JAR
            = Path.of("tools/google-java-format.jar");

    @CommandLine.Command(name = "install_tools")
    public void installTools() throws Exception {
        GoogleJavaFormat.downloadVersion("1.23.0", GOOGLE_JAVA_FORMAT_JAR);
    }

    @CommandLine.Command(name = "clean")
    public void clean() throws IOException {
        try (Stream<Path> pathStream = Files.walk(Path.of("build"))) {
            pathStream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (NoSuchFileException e) {}
    }

    @CommandLine.Command(name = "compile")
    public void compile() throws Exception {
        clean();
        Javac.run(arguments -> {
            arguments
                    .__module_source_path("./modules/*/src")
                    .__module("dev.mccue.atom")
                    .__module_version("2024.08.27")
                    .__release(21)
                    ._g()
                    ._Werror()
                    ._d(Path.of("build/javac"));
        });
    }

    @CommandLine.Command(name = "package")
    public void package_() throws Exception {
        compile();
        Jar.run(arguments -> {
            arguments
                    .__create()
                    .__file(Path.of("build/jar/dev.mccue.atom.jar"))
                    ._C(Path.of("build/javac/dev.mccue.atom"), ".");
        });
    }

    @CommandLine.Command(name = "document")
    public void document() throws Exception {
        Javadoc.run(arguments -> {
            arguments
                    .__module_source_path("./modules/*/src")
                    .__module("dev.mccue.atom")
                    ._Werror()
                    ._quiet()
                    ._d(Path.of("build/javadoc"));
        });
    }

    @CommandLine.Command(name = "stage")
    public void stage() throws Exception {
        package_();
        document();

        var output = Path.of("build/jstage");
        var pom = Path.of("modules/dev.mccue.atom/pom.xml");

        JStage.run(arguments -> {
            arguments
                    .__output(output)
                    .__pom(pom)
                    .__artifact(Path.of("build/jar/dev.mccue.atom.jar"));
        });

        JStage.run(arguments -> {
            arguments
                    .__output(output)
                    .__pom(pom)
                    .__artifact(Path.of("build/javadoc"))
                    .__classifier("javadoc");
        });

        JStage.run(arguments -> {
            arguments
                    .__output(output)
                    .__pom(pom)
                    .__artifact(Path.of("modules/dev.mccue.atom/src"))
                    .__classifier("sources");
        });
    }

    @CommandLine.Command(name = "deploy")
    public void deploy() throws Exception {
        stage();
        Tool.ofSubprocess("jreleaser")
                .run("deploy", "--output-directory", "build");
    }

    @CommandLine.Command(name = "format")
    public void format() throws Exception {
        GoogleJavaFormat.run(
                GOOGLE_JAVA_FORMAT_JAR,
                arguments -> {
                    arguments
                            .__dry_run()
                            .files(Path.of("modules/dev.mccue.atom/src/dev/mccue/atom/Atom.java"));
                }
        );
    }

    public static void main(String[] args) {
        new CommandLine(new Project()).execute(args);
    }
}
