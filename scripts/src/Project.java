import dev.mccue.tools.Tool;
import dev.mccue.tools.googlejavaformat.GoogleJavaFormat;
import dev.mccue.tools.jar.Jar;
import dev.mccue.tools.java.Java;
import dev.mccue.tools.javac.Javac;
import dev.mccue.tools.javac.JavacArguments;
import dev.mccue.tools.javadoc.Javadoc;
import dev.mccue.tools.jresolve.JResolve;
import dev.mccue.tools.jstage.JStage;
import dev.mccue.tools.junit.JUnitArguments;
import dev.mccue.tools.pmd.PMD;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@CommandLine.Command(name = "project")
public class Project {
    static final Path TOOLS_PATH = Path.of("tools");
    static final Path GOOGLE_JAVA_FORMAT_JAR
            = TOOLS_PATH.resolve("google-java-format.jar");
    static final Path PMD_PATH
            = TOOLS_PATH.resolve("pmd");

    static final Path BUILD_PATH = Path.of("build");


    @CommandLine.Command(name = "install_tools")
    public void installTools() throws Exception {
        GoogleJavaFormat.downloadVersion("1.23.0", GOOGLE_JAVA_FORMAT_JAR);
        PMD.downloadVersion("7.4.0", PMD_PATH);
    }

    @CommandLine.Command(name = "install")
    public void install() throws Exception {
        JResolve.run(arguments -> {
            arguments.__output_directory(Path.of("modules/dev.mccue.atom.test/libs"))
                    .__purge_output_directory()
                    .__use_module_names()
                    .argumentFile(Path.of("modules/dev.mccue.atom.test/libs.txt"));
        });
    }

    private boolean cleaned = false;

    @CommandLine.Command(name = "clean")
    public void clean() throws IOException {
        if (!cleaned) {
            try (Stream<Path> pathStream = Files.walk(BUILD_PATH)) {
                pathStream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (NoSuchFileException e) {
            }
            cleaned = true;
        }
    }

    @CommandLine.Command(name = "compile")
    public void compile() throws Exception {
        clean();
        Consumer<JavacArguments> commonArgs = arguments -> {
            arguments
                    .__module_source_path("./modules/*/src")
                    .__module_version("2024.08.30")
                    .__release(21)
                    ._g()
                    ._Werror()
                    ._d(BUILD_PATH.resolve("javac"));
        };

        Javac.run(arguments -> {
            commonArgs.accept(arguments);
            arguments.__module("dev.mccue.atom");
        });


        Javac.run(arguments -> {
            commonArgs.accept(arguments);
            arguments
                    .__module("dev.mccue.atom.test")
                    .__module_path(
                            Path.of("modules/dev.mccue.atom.test/libs"),
                            Path.of("build/javac/dev.mccue.atom")
                    );
        });
    }

    @CommandLine.Command(name = "package")
    public void package_() throws Exception {
        compile();
        Jar.run(arguments -> {
            arguments
                    .__create()
                    .__file(BUILD_PATH.resolve("jar/dev.mccue.atom.jar"))
                    ._C(BUILD_PATH.resolve("javac/dev.mccue.atom"), ".");
        });

        Jar.run(arguments -> {
            arguments
                    .__create()
                    .__file(BUILD_PATH.resolve("jar/dev.mccue.atom.test.jar"))
                    ._C(BUILD_PATH.resolve("javac/dev.mccue.atom.test"), ".");
        });
    }

    @CommandLine.Command(name = "document")
    public void document() throws Exception {
        clean();
        Javadoc.run(arguments -> {
            arguments
                    .__module_source_path("./modules/*/src")
                    .__module("dev.mccue.atom")
                    ._Werror()
                    ._d(BUILD_PATH.resolve("javadoc"));
        });
    }

    @CommandLine.Command(name = "stage")
    public void stage() throws Exception {
        package_();
        document();

        var output = BUILD_PATH.resolve("jstage");
        var pom = Path.of("modules/dev.mccue.atom/pom.xml");

        JStage.run(arguments -> {
            arguments
                    .__output(output)
                    .__pom(pom)
                    .__artifact(BUILD_PATH.resolve("jar/dev.mccue.atom.jar"));
        });

        JStage.run(arguments -> {
            arguments
                    .__output(output)
                    .__pom(pom)
                    .__artifact(BUILD_PATH.resolve("javadoc"))
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
                .run("deploy", "--output-directory", BUILD_PATH.toString());
    }

    private List<Path> javaFiles() throws Exception {
        List<Path> paths;
        try (Stream<Path> pathStream = Files.walk(Path.of("modules"))) {
            paths = pathStream
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
        return paths;
    }

    @CommandLine.Command(name = "format:check")
    public void format_check() throws Exception {
        var files = javaFiles();
        GoogleJavaFormat.run(
                GOOGLE_JAVA_FORMAT_JAR,
                arguments -> {
                    arguments
                            .__dry_run()
                            .__aosp()
                            .__set_exit_if_changed()
                            .files(files);
                }
        );
    }

    @CommandLine.Command(name = "format")
    public void format() throws Exception {
        var files = javaFiles();
        GoogleJavaFormat.run(
                GOOGLE_JAVA_FORMAT_JAR,
                arguments -> {
                    arguments
                            .__aosp()
                            .__replace()
                            .files(files);
                }
        );
    }

    @CommandLine.Command(name = "lint")
    public void lint() throws Exception {
        var files = javaFiles();
        PMD.run(PMD_PATH, arguments -> {
            arguments.add("check");
            arguments.__rulesets("ruleset.xml")
                    ._r(BUILD_PATH.resolve("pmd/report.txt"))
                    .__cache(BUILD_PATH.resolve("pmd/cache"))
                    .__dir(files);
        });
    }

    @CommandLine.Command(name = "test")
    public void test() throws Exception {
        package_();
        Java.run(arguments -> {
            arguments
                    .__module_path(
                            Path.of("modules/dev.mccue.atom.test/libs"),
                            BUILD_PATH.resolve("jar")
                    )
                    .__add_modules("dev.mccue.atom.test")
                    .__module("org.junit.platform.console")
                    .addAll(
                            new JUnitArguments()
                                    .execute()
                                    .__disable_banner()
                                    .__select_module("dev.mccue.atom.test")
                                    .__reports_dir(Path.of("build/junit"))
                    );
        });
    }

    public static void main(String[] args) {
        new CommandLine(new Project()).execute(args);
    }
}
