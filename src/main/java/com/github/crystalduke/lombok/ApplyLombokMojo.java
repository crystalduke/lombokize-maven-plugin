package com.github.crystalduke.lombok;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.SourceRoot;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
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
    ParserConfiguration config = new ParserConfiguration();

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
        LexicalPreservingPrinter.setup(cu);
        Set<Class<?>> imports = new TreeSet<>(Comparator.comparing(Class::getName));
        for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            imports.addAll(apply(field, new GeneratedGetterPredicate(field), Getter.class));
            imports.addAll(apply(field, new GeneratedSetterPredicate(field), Setter.class));
        }
        for (Class<?> clazz : imports) {
            cu.addImport(clazz);
        }
        boolean modified = imports.isEmpty() == false;
        for (TypeDeclaration typeDeclaration : cu.findAll(TypeDeclaration.class)) {
            modified |= apply(typeDeclaration, Getter.class);
            modified |= apply(typeDeclaration, Setter.class);
        }
        if (modified) {
            try (BufferedWriter writer = Files.newBufferedWriter(absolutePath,
                    config.getCharacterEncoding())) {
                writer.write(LexicalPreservingPrinter.print(cu));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return SourceRoot.Callback.Result.DONT_SAVE;
    }

    private static AccessLevel toAccessLevel(MethodDeclaration method) {
        switch (method.getAccessSpecifier()) {
            case PUBLIC:
                return AccessLevel.PUBLIC;
            case PROTECTED:
                return AccessLevel.PROTECTED;
            case PACKAGE_PRIVATE:
                return AccessLevel.PACKAGE;
            case PRIVATE:
            default:
                return AccessLevel.PRIVATE;
        }
    }

    private static FieldAccessExpr toAccessLevelExpr(MethodDeclaration method) {
        AccessLevel accessLevel = toAccessLevel(method);
        switch (accessLevel) {
            case PUBLIC:
                return null;
            default:
                return new FieldAccessExpr(
                        new NameExpr(AccessLevel.class.getSimpleName()),
                        accessLevel.name());
        }
    }

    private static NodeList<Expression> convertType(NodeList<AnnotationExpr> annotations) {
        return new NodeList<>(
                annotations.stream()
                        .map(LexicalPreservingPrinter::print)
                        .map(StaticJavaParser::parseAnnotation)
                        .map(Expression.class::cast)
                        .collect(Collectors.toList()));
    }

    private static AnnotationExpr createAnnotation(MethodDeclaration method, Class<?> clazz) {
        NodeList<Expression> methodAnnotations = convertType(method.getAnnotations());
        NodeList<Expression> paramAnnotations = method.getParameters().isEmpty()
                ? new NodeList<>()
                : convertType(method.getParameter(0).getAnnotations());
        String className = clazz.getSimpleName();
        FieldAccessExpr accessLevel = toAccessLevelExpr(method);
        if (methodAnnotations.isEmpty() && paramAnnotations.isEmpty()) {
            if (accessLevel == null) {
                return new MarkerAnnotationExpr(className);
            }
            return new SingleMemberAnnotationExpr(new Name(className), accessLevel);
        }
        NodeList<MemberValuePair> attributes = new NodeList<>();
        if (accessLevel != null) {
            attributes.add(new MemberValuePair("value", accessLevel));
        }
        if (!methodAnnotations.isEmpty()) {
            attributes.add(new MemberValuePair("onMethod",
                    new SingleMemberAnnotationExpr(new Name("__"),
                            new ArrayInitializerExpr(methodAnnotations))));
        }
        if (!paramAnnotations.isEmpty()) {
            attributes.add(new MemberValuePair("onParam",
                    new SingleMemberAnnotationExpr(new Name("__"),
                            new ArrayInitializerExpr(paramAnnotations))));
        }
        return new NormalAnnotationExpr(new Name(className), attributes);
    }

    private static boolean containsAccessLevel(AnnotationExpr annotation) {
        final Class<? extends AnnotationExpr> clazz = annotation.getClass();
        return clazz.equals(SingleMemberAnnotationExpr.class)
                || clazz.equals(NormalAnnotationExpr.class)
                && NormalAnnotationExpr.class.cast(annotation).getPairs()
                        .stream()
                        .map(MemberValuePair::getNameAsString)
                        .anyMatch("value"::equals);
    }

    private static void remove(MethodDeclaration method) {
        method.getComment().ifPresent(Comment::remove);
        method.remove();
    }

    private static <N extends Node> boolean hasAnnotation(NodeWithAnnotations<N> node, String simpleName) {
        return node.getAnnotations()
                .stream()
                .map(AnnotationExpr::getName)
                .map(Name::getIdentifier)
                .anyMatch(simpleName::equals);
    }

    private boolean apply(TypeDeclaration<?> typeDeclaration, Class<? extends Annotation> clazz) {
        final String simpleName = clazz.getSimpleName();
        if (hasAnnotation(typeDeclaration, simpleName)) {
            // 既にアノテーションがある
            return false;
        }
        // 属性のないアノテーション (MarkerAnnotationExpr）
        List<Optional<AnnotationExpr>> annotations = typeDeclaration
                .getChildNodes()
                .stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .map(field -> field.getAnnotationByClass(clazz))
                .map(ann -> ann.filter(MarkerAnnotationExpr.class::isInstance))
                .collect(Collectors.toList());
        if (annotations.isEmpty()
                || !annotations.stream().allMatch(Optional::isPresent)) {
            return false;
        }
        typeDeclaration.addAnnotation(new MarkerAnnotationExpr(simpleName));
        annotations.forEach(annotation -> annotation.get().remove());
        return true;
    }

    public Set<Class<?>> apply(FieldDeclaration fieldDeclaration,
            Predicate<MethodDeclaration> methodPredicate,
            Class<? extends Annotation> annotationClass) {
        if (hasAnnotation(fieldDeclaration, annotationClass.getSimpleName())) {
            // 既にアノテーションがある
            return Collections.emptySet();
        }
        Node classBody = fieldDeclaration.getParentNode().get();
        List<MethodDeclaration> methods = classBody.getChildNodes().stream()
                .filter(MethodDeclaration.class::isInstance)
                .map(MethodDeclaration.class::cast)
                .filter(methodPredicate)
                .collect(Collectors.toList());
        if (methods.isEmpty()) {
            return Collections.emptySet();
        }
        if (methods.size() > 1) {
            throw new IllegalStateException();
        }
        Set<Class<?>> imports = new HashSet<>(2);
        imports.add(annotationClass);
        MethodDeclaration method = methods.get(0);
        AnnotationExpr annotation = createAnnotation(method, annotationClass);
        if (containsAccessLevel(annotation)) {
            imports.add(AccessLevel.class);
        }
        remove(method);
        fieldDeclaration.addAnnotation(annotation);
        return imports;
    }
}
