package com.github.crystalduke.lombok;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import static com.github.javaparser.ParserConfiguration.LanguageLevel.*;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.SourceRoot;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Lombok アノテーションを適用する.
 */
@Mojo(name = "apply", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class ApplyLombokMojo extends AbstractMojo implements SourceRoot.Callback {

    @Parameter(defaultValue = "${project.basedir}/src", property = "lombokize.sourceDirectory")
    private File sourceDirectory;
    @Parameter(property = "lombokize.encoding")
    private String encoding;
    @Parameter(property = "lombokize.languageLevel")
    private String languageLevel;
    ParserConfiguration config = new ParserConfiguration();
    Set<ParserConfiguration.LanguageLevel> unsupportedLevels
            = EnumSet.of(JAVA_1_0, JAVA_1_1, JAVA_1_2, JAVA_1_3, JAVA_1_4, JAVA_5, JAVA_6);
    boolean jdk7;

    private static ParserConfiguration.LanguageLevel toLanguageLevel(String level) {
        if (level == null) {
            return null;
        }
        if (level.matches("^1\\.[0-4]$")) {
            // 例：1.4 -> 1_4
            level = level.replace('.', '_');
        } else if (level.matches("1\\.[5-8]$")) {
            // 例：1.5 -> 5
            level = level.substring(2);
        }
        return ParserConfiguration.LanguageLevel.valueOf("JAVA_" + level);
    }

    @Override
    public void execute() throws MojoExecutionException {
        final Path rootPath = sourceDirectory.toPath();
        if (!Files.isDirectory(rootPath)) {
            return;
        }
        if (encoding != null) {
            config.setCharacterEncoding(Charset.forName(encoding));
        }
        config.setLexicalPreservationEnabled(true);
        if (languageLevel != null) {
            config.setLanguageLevel(toLanguageLevel(languageLevel));
            if (unsupportedLevels.contains(config.getLanguageLevel())) {
                throw new IllegalStateException("Unsupported language level: " + languageLevel);
            }
        }
        jdk7 = config.getLanguageLevel().equals(JAVA_7);
        SourceRoot srcRoot = new SourceRoot(rootPath, config);
        StaticJavaParser.setConfiguration(config);
        try {
            srcRoot.parse("", config, this);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public SourceRoot.Callback.Result process(Path localPath, Path absolutePath,
            ParseResult<CompilationUnit> result) {
        if (!result.isSuccessful()) {
            return Result.TERMINATE;
        }
        CompilationUnit original = result.getResult().get();
        CompilationUnit cu = new CompilationUnitLombokizer(jdk7).apply(original);
        if (original != cu) {
            try (BufferedWriter writer = Files.newBufferedWriter(absolutePath,
                    config.getCharacterEncoding())) {
                writer.write(TokenUtil.asString(cu));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return SourceRoot.Callback.Result.DONT_SAVE;
    }
}
