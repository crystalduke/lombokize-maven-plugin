package com.github.crystalduke.lombok;

import static com.github.crystalduke.lombok.GeneratedMethodPredicate.startsWithIs;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import lombok.Setter;

/**
 * メソッド本体が {@link Setter} によって生成されたものと同じか判定する.
 */
public class GeneratedSetterPredicate extends GeneratedMethodPredicate {

    /**
     * {@link Setter} を付与するフィールドを指定してオブジェクトを構築する.
     *
     * @param variable {@link Setter} を付与するフィールドの変数.
     */
    public GeneratedSetterPredicate(VariableDeclarator variable) {
        super(variable, isBoolean -> "set");
    }

    /**
     * 引数のメソッドが存在することで、オブジェクト構築時に指定したフィールドに {@link Setter}
     * を付与してもメソッドが生成されないか判定する.
     *
     * @param method メソッド定義
     * @return 生成可能であれば {@code true}, それ以外は {@code false}.
     */
    @Override
    public boolean test(MethodDeclaration method) {
        if (method.getParameters().size() != 1) {
            return false;
        }
        String name = method.getNameAsString();
        if (name.equalsIgnoreCase("set" + fieldName)) {
            return true;
        }
        return "boolean".equals(fieldType)
                && startsWithIs(fieldName)
                && name.equalsIgnoreCase("set" + fieldName.substring(2));
    }

    /**
     * 引数のメソッドがオブジェクト構築時に指定したフィールドに {@link Setter} を付与することで生成可能か判定する.
     *
     * @param method メソッド定義
     * @return 生成可能であれば {@code true}, それ以外は {@code false}.
     */
    @Override
    public boolean canGenerate(MethodDeclaration method) {
        return super.canGenerate(method)
                // 返り値の型は void
                && "void".equals(method.getTypeAsString())
                // 引数は１つ
                && method.getParameters().size() == 1
                // 引数の型はフィールドと同じ
                && fieldType.equals(method.getParameters().get(0).getTypeAsString())
                && equalsGeneratedBody(method);
    }

    private boolean equalsGeneratedBody(MethodDeclaration method) {
        return method.getBody()
                // メソッド内は1文だけ
                .map(block -> block.getStatements())
                .filter(statements -> statements.size() == 1)
                .map(statements -> statements.get(0))
                // 代入文
                .filter(Statement::isExpressionStmt)
                .map(statement -> statement.asExpressionStmt().getExpression())
                .filter(Expression::isAssignExpr)
                .map(Expression::asAssignExpr)
                // 代入演算子は等号
                .filter(assignExpr -> assignExpr.getOperator().equals(AssignExpr.Operator.ASSIGN))
                // 左辺はオブジェクト構築時に指定したフィールド
                .filter(assignExpr -> isReferredFrom(assignExpr.getTarget()))
                // 右辺は引数
                .map(AssignExpr::getValue)
                .filter(Expression::isNameExpr)
                .map(Expression::asNameExpr)
                .map(NameExpr::getNameAsString)
                .filter(method.getParameters().get(0).getNameAsString()::equals)
                .isPresent();
    }
}
