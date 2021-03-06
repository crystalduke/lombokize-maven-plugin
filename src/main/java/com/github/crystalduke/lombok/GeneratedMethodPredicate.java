package com.github.crystalduke.lombok;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Lombok アノテーションにより生成されるメソッドと同じ定義か判定する.
 */
abstract class GeneratedMethodPredicate implements Predicate<MethodDeclaration> {

    final boolean isStatic;
    final String fieldType;
    final String fieldName;
    final String methodName;
    final String className;
    final String fqcn;
    
    protected static boolean startsWithIs(String fieldName) {
        return fieldName.startsWith("is")
                && fieldName.length() > 2
                && !Character.isLowerCase(fieldName.charAt(2));
    }

    private static String toMethodName(String fieldName, boolean isBoolean, String prefix) {
        if (isBoolean && startsWithIs(fieldName)) {
            return prefix + fieldName.substring(2);
        }
        return prefix
                + Character.toUpperCase(fieldName.charAt(0))
                + fieldName.substring(1);
    }

    GeneratedMethodPredicate(VariableDeclarator field,
            Function<Boolean, String> methodPrefix) {
        final FieldDeclaration fieldDeclaration = field.getParentNode()
                .map(FieldDeclaration.class::cast)
                .get();
        isStatic = fieldDeclaration.isStatic();
        fieldType = field.getTypeAsString();
        fieldName = field.getName().getIdentifier();
        boolean isBoolean = "boolean".equals(fieldType);
        methodName = toMethodName(fieldName, isBoolean, methodPrefix.apply(isBoolean));
        Node classBody = fieldDeclaration.getParentNode().get();
        if (TypeDeclaration.class.isInstance(classBody)) {
            TypeDeclaration<?> type = TypeDeclaration.class.cast(classBody);
            fqcn = type.getFullyQualifiedName().get();
            className = type.getNameAsString();
        } else {
            fqcn = className = null;
        }
    }

    /**
     * サブクラス共通の判定をする.
     */
    public boolean canGenerate(MethodDeclaration method) {
        return methodName.equals(method.getNameAsString())
                && isStatic == method.isStatic()
                && !method.isAbstract()
                && !method.isFinal()
                && !method.isNative()
                && !method.isSynchronized()
                && method.getThrownExceptions().isEmpty();
    }

    /**
     * 引数がコンストラクタで指定したフィールドを参照しているか判定する.
     *
     * @param expression 式.
     * @return 引数がコンストラクタで指定したフィールドを参照していれば {@code true}, それ以外は {@code false}.
     */
    protected boolean isReferredFrom(Expression expression) {
        if (expression.isNameExpr()) {
            // フィールド名だけ
            final NameExpr nameExpr = expression.asNameExpr();
            return fieldName.equals(nameExpr.getName().getIdentifier());
        }
        if (!expression.isFieldAccessExpr() || className == null) {
            // フィールドアクセスではないか、匿名クラス
            return false;
        }
        final FieldAccessExpr fieldAccessExpr = expression.asFieldAccessExpr();
        if (!fieldName.equals(fieldAccessExpr.getName().getIdentifier())) {
            // アクセスしているフィールドが違う
            return false;
        }
        Expression scope = fieldAccessExpr.getScope();
        if (isStatic) {
            if (scope.isFieldAccessExpr()) {
                String scopeName = scope.asFieldAccessExpr().getNameAsString();
                return anyMatch(scopeName, className, fqcn);
            }
        } else if (scope.isThisExpr()) {
            // this.fieldName の場合、this の前に何もないか、
            // フィールドと同じクラスであればよい
            return !scope.asThisExpr().getTypeName()
                    .filter(typeName -> !anyMatch(typeName.asString(), className, fqcn))
                    .isPresent();
        }
        return false;
    }

    private static <T> boolean anyMatch(T o1, T... o2) {
        return Arrays.stream(o2).anyMatch(o1::equals);
    }
}
