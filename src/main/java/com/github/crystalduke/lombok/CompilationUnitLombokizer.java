package com.github.crystalduke.lombok;

import com.github.javaparser.JavaToken;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * {@link CompilationUnit} 単位で Lombok アノテーションを適用するクラス.
 */
@AllArgsConstructor
public class CompilationUnitLombokizer implements Function<CompilationUnit, CompilationUnit> {

    private static final Logger LOG = Logger.getLogger(CompilationUnitLombokizer.class.getName());
    private final boolean jdk7;

    /**
     * JDK 8 以上のソースを対象としたインスタンスを構築する.
     */
    public CompilationUnitLombokizer() {
        this(false);
    }

    /**
     * Lombok アノテーションを適用した {@link CompilationUnit} を返す.
     *
     * @param cu Lombok アノテーションを適用するインスタンス.
     * @return Lombok アノテーションを適用したインスタンス.
     */
    @Override
    public CompilationUnit apply(CompilationUnit cu) {
        LexicalPreservingPrinter.setup(cu);
        final FieldLombokizer getter = FieldLombokizer.forGetter(jdk7);
        final FieldLombokizer setter = FieldLombokizer.forSetter(jdk7);
        // フィールド単位にアノテーションを適用する
        int numOfGetter = 0;
        int numOfSetter = 0;
        int numOfFields = 0;
        for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            final boolean addGetter = getter.apply(field);
            final boolean addSetter = setter.apply(field);
            if (addGetter || addSetter) {
                numOfFields++;
            }
            if (addGetter) {
                numOfGetter++;
            }
            if (addSetter) {
                numOfSetter++;
            }
        }
        if (numOfFields > 0) {
            cu = refresh(cu);
            LOG.log(Level.INFO, "Added {0} @Getter and {1} @Setter on {2} fields",
                    new Object[] {numOfGetter, numOfSetter, numOfFields});
            // 適用したアノテーションの import 文を追加する
            Set<Class<?>> imports = new TreeSet<>(Comparator.comparing(Class::getName));
            for (Class<?> clazz : new Class<?>[]{Getter.class, Setter.class}) {
                List<AnnotationExpr> annotations = cu.findAll(AnnotationExpr.class,
                        annotation -> clazz.getSimpleName().equals(annotation.getName().getIdentifier()));
                if (!annotations.isEmpty()) {
                    imports.add(clazz);
                    if (annotations.stream().anyMatch(CompilationUnitLombokizer::containsAccessLevel)) {
                        imports.add(AccessLevel.class);
                    }
                }
            }
            for (Class<?> clazz : imports) {
                if (addImport(cu, clazz)) {
                    LOG.log(Level.FINE, "Add import {0}", clazz.getName());
                    cu = refresh(cu);
                }
            }
        }
        // クラス単位にアノテーションを適用する
        boolean modified = false;
        for (TypeDeclaration typeDeclaration : cu.findAll(TypeDeclaration.class)) {
            modified |= getter.apply(typeDeclaration);
            modified |= setter.apply(typeDeclaration);
        }
        if (modified) {
            cu = refresh(cu);
        }
        return cu;
    }

    /**
     * JavaToken を元に文字列にして、再度パースし直したものを返す.
     */
    private static CompilationUnit refresh(CompilationUnit cu) {
        // JavaToken を元に文字列にして、再度パースし直す
        String code = TokenUtil.asString(cu);
        cu = StaticJavaParser.parse(code);
        LexicalPreservingPrinter.setup(cu);
        return cu;
    }

    /**
     * {@link CompilationUnit} に指定したクラスの {@code import} 文を追加する. {@code import}
     * 文は、既存の {@code import} の最後に追加する.
     *
     * @return {@code import} を追加したら {@code true}, そうでなければ {@code false}.
     */
    private static boolean addImport(CompilationUnit cu, Class<?> clazz) {
        if (isImported(cu, clazz)) {
            return false;
        }
        ImportDeclaration importDeclaration = TokenUtil.clone(
                new ImportDeclaration(clazz.getName(), false, false));
        Node previousNode = null; // import 文を追加する直前のノード
        for (Node node : cu.getChildNodes()) {
            if (ImportDeclaration.class.isInstance(node)) {
                // import 文がある場合、最後の import 文の次の行に追加する
                previousNode = node;
            }
        }
        if (previousNode != null) {
            // import 文を追加する直前の Token
            JavaToken previousToken = previousNode.getTokenRange().get().getEnd();
            boolean found = false;
            JavaToken eol = null; // import 文の改行として使う Token
            for (JavaToken token = previousToken, nextToken;
                    !found && token.getNextToken().isPresent();
                    token = nextToken) {
                nextToken = token.getNextToken().get();
                switch (nextToken.getCategory()) {
                    case WHITESPACE_NO_EOL:
                        break;
                    case COMMENT:
                        if (nextToken.getText().contains("\n")) {
                            // import 文に続いて改行を含むコメントが来る場合、
                            // そのコメントの前に import 文を追加する.
                            found = true;
                        }
                        break;
                    case EOL:
                        previousToken = token;
                        eol = TokenUtil.clone(nextToken);
                        found = true;
                        break;
                    default:
                        // import 文と次のノードの間に改行がない場合、
                        // 次のノードの前に import 文を追加する
                        found = true;
                }
            }
            JavaToken nextToken = previousToken.getNextToken().get();
            nextToken.insert(eol != null ? eol : TokenUtil.lineSeparator(cu));
            for (JavaToken token : importDeclaration.getTokenRange().get()) {
                nextToken.insert(token);
            }
        } else {
            // import 文がなければ、型宣言の前に追加する
            Node nextNode = cu.findFirst(TypeDeclaration.class).get();
            if (nextNode.getComment().isPresent()) {
                // 型宣言にコメントがあれば、コメントの前に追加する
                nextNode = nextNode.getComment().get();
            }
            JavaToken nextToken = nextNode.getTokenRange().get().getBegin();
            for (JavaToken token : importDeclaration.getTokenRange().get()) {
                nextToken.insert(token);
            }
            nextToken.insert(TokenUtil.lineSeparator(cu));
            // import 文に続き、空行を追加しておく
            nextToken.insert(TokenUtil.lineSeparator(cu));
        }
        return true;
    }

    private static boolean isImported(CompilationUnit cu, Class<?> clazz) {
        return cu.findAll(ImportDeclaration.class)
                .stream()
                .anyMatch(declaration -> match(declaration, clazz));
    }

    private static boolean match(ImportDeclaration declaration, Class<?> clazz) {
        if (declaration.isStatic()) {
            return false;
        }
        final String identifier = declaration.getName().asString();
        return declaration.isAsterisk() && clazz.getPackage().getName().equals(identifier)
                || clazz.getName().equals(identifier);
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
}
