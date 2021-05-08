package com.github.crystalduke.lombok;

import com.github.javaparser.JavaToken;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
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
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Lombok アノテーションを適用し、メソッドを削除する.
 */
public class FieldLombokizer implements Function<FieldDeclaration, Boolean> {

    final Class<? extends Annotation> annotationClass;
    final Function<FieldDeclaration, Predicate<MethodDeclaration>> predicate;

    /**
     * {@link Getter} を適用してメソッドを削除するインスタンスを返す.
     *
     * @return {@link Getter} を適用するインスタンス.
     */
    public static FieldLombokizer forGetter() {
        return new FieldLombokizer(Getter.class, GeneratedGetterPredicate::new);
    }

    /**
     * {@link Setter} を適用してメソッドを削除するインスタンスを返す.
     *
     * @return {@link Setter} を適用するインスタンス.
     */
    public static FieldLombokizer forSetter() {
        return new FieldLombokizer(Setter.class, GeneratedSetterPredicate::new);
    }

    private FieldLombokizer(Class<? extends Annotation> annotationClass,
            Function<FieldDeclaration, Predicate<MethodDeclaration>> predicate) {
        this.annotationClass = annotationClass;
        this.predicate = predicate;
    }

    /**
     * 全ての非 static フィールドに属性のないアノテーションが付与されていた場合, フィールドのアノテーションは削除し,クラスにアノテーションを付与する.
     *
     * @param typeDeclaration 型宣言
     * @return クラスにアノテーションを付与した場合は {@code true}, それ以外は {@code false}.
     */
    public Boolean apply(TypeDeclaration<?> typeDeclaration) {
        if (Setter.class.equals(annotationClass)
                && typeDeclaration.isEnumDeclaration()) {
            // enum は Setter を付与するとエラーとなるので対象外
            return false;
        }
        final String simpleName = annotationClass.getSimpleName();
        if (hasAnnotation(typeDeclaration, simpleName)) {
            // 既にアノテーションがある
            return false;
        }
        // 属性のないアノテーション (MarkerAnnotationExpr）
        List<Optional<AnnotationExpr>> annotations = typeDeclaration
                // 型宣言の子ノードのうち
                .getChildNodes()
                .stream()
                // フィールドが
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                // 非 static なものが
                .filter(field -> !field.isStatic())
                // 指定のアノテーションが付与されていて
                .map(field -> field.getAnnotationByClass(annotationClass))
                // そのアノテーションに属性がないかどうか
                .map(ann -> ann.filter(MarkerAnnotationExpr.class::isInstance))
                .collect(Collectors.toList());
        if (annotations.isEmpty()
                || !annotations.stream().allMatch(Optional::isPresent)) {
            // 非 static フィールドに対象のアノテーションがないか、条件に合致しない
            return false;
        }
        // 型宣言にアノテーションを追加して、フィールドから削除する
        TokenUtil.addAnnotation(typeDeclaration, new MarkerAnnotationExpr(simpleName));
        annotations.forEach(annotation -> TokenUtil.remove(annotation.get()));
        return true;
    }

    @Override
    public Boolean apply(FieldDeclaration fieldDeclaration) {
        final String simpleName = annotationClass.getSimpleName();
        if (hasAnnotation(fieldDeclaration, simpleName)
                || fieldDeclaration.getParentNode()
                        .filter(TypeDeclaration.class::isInstance)
                        .map(TypeDeclaration.class::cast)
                        .filter(type -> hasAnnotation(type, simpleName))
                        .isPresent()) {
            // フィールドか型宣言にアノテーションがある
            return false;
        }
        Node classBody = fieldDeclaration.getParentNode().get();
        // 対象のフィールドに @Getter を付与した場合に生成されるメソッドが存在し
        // かつ定義可能であれば、そのメソッドを抽出する.
        List<MethodDeclaration> methods = classBody.getChildNodes().stream()
                .filter(MethodDeclaration.class::isInstance)
                .map(MethodDeclaration.class::cast)
                .filter(predicate.apply(fieldDeclaration))
                .collect(Collectors.toList());
        if (methods.isEmpty()) {
            return false;
        }
        if (methods.size() > 1) {
            // コンパイルエラーとなるはずだが、念のため
            throw new IllegalStateException();
        }
        MethodDeclaration method = methods.get(0);
        AnnotationExpr annotation = createAnnotation(method, annotationClass);
        TokenUtil.remove(method);
        NodeList<Modifier> modifiers = fieldDeclaration.getModifiers();
        // 修飾子がなければ型の前、あれば最初の修飾子の前にアノテーションを付与する
        Node addAnnotationBefore = modifiers.isEmpty()
                ? fieldDeclaration.getVariable(0).getType()
                : modifiers.get(0);
        TokenUtil.addAnnotation(addAnnotationBefore, annotation);
        return true;
    }

    private static <N extends Node> boolean hasAnnotation(NodeWithAnnotations<N> node, String simpleName) {
        return node.getAnnotations()
                .stream()
                .map(AnnotationExpr::getName)
                .map(Name::getIdentifier)
                .anyMatch(simpleName::equals);
    }

    private static AnnotationExpr createAnnotation(MethodDeclaration method, Class<? extends Annotation> clazz) {
        NodeList<Expression> methodAnnotations = convertType(method.getAnnotations());
        NodeList<Expression> paramAnnotations = method.getParameters().isEmpty()
                // Getter は引数無し
                ? new NodeList<>()
                // Setter は引数が１つだけなので、最初のパラメータを抽出
                : convertType(method.getParameter(0).getAnnotations());
        String className = clazz.getSimpleName();
        FieldAccessExpr accessLevel = toAccessLevelExpr(method);
        if (methodAnnotations.isEmpty() && paramAnnotations.isEmpty()) {
            return accessLevel == null
                    ? new MarkerAnnotationExpr(className)
                    : new SingleMemberAnnotationExpr(new Name(className), accessLevel);
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

    /**
     * 各要素をパースし直し {@link JavaToken} を含むインスタンスのコレクションとして返す.
     */
    private static NodeList<Expression> convertType(NodeList<AnnotationExpr> annotations) {
        return new NodeList<>(
                annotations.stream()
                        .map(LexicalPreservingPrinter::print)
                        .map(StaticJavaParser::parseAnnotation)
                        .map(Expression.class::cast)
                        .collect(Collectors.toList()));
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
}