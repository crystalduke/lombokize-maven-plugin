package com.github.crystalduke.lombok;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.SourceRoot;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private ParserConfiguration.LanguageLevel languageLevel;

    @Override
    public void execute() throws MojoExecutionException {
        final Path rootPath = sourceDirectory.toPath();
        if (!Files.isDirectory(rootPath)) {
            return;
        }
        ParserConfiguration config = new ParserConfiguration();
        if (encoding != null) {
            config.setCharacterEncoding(Charset.forName(encoding));
        }
        config.setLexicalPreservationEnabled(true);
        if (languageLevel != null) {
            config.setLanguageLevel(languageLevel);
        }
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
		CompilationUnit cu = result.getResult().get();
        // do something
        return SourceRoot.Callback.Result.DONT_SAVE;
    }
}
