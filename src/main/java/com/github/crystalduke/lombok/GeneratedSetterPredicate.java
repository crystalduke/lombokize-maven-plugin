package com.github.crystalduke.lombok;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import lombok.Setter;

/**
 * メソッド本体が {@link Setter} によって生成されたものと同じか判定する.
 */
public class GeneratedSetterPredicate extends GeneratedMethodPredicate {

    public GeneratedSetterPredicate(FieldDeclaration fieldDeclaration) {
        super(fieldDeclaration,
                type -> "set");
    }

    @Override
    public boolean test(MethodDeclaration method) {
        return super.test(method)
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
                // 左辺はフィールド
                .filter(assignExpr -> isReferencedFrom(assignExpr.getTarget()))
                // 右辺は引数
                .map(AssignExpr::getValue)
                .filter(Expression::isNameExpr)
                .map(Expression::asNameExpr)
                .map(NameExpr::getNameAsString)
                .filter(method.getParameters().get(0).getNameAsString()::equals)
                .isPresent();
    }
}
