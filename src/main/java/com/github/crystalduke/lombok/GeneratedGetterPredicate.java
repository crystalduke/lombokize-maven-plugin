package com.github.crystalduke.lombok;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.Optional;
import lombok.Getter;

/**
 * メソッド本体が {@link Getter} によって生成されたものと同じか判定する.
 */
public class GeneratedGetterPredicate extends GeneratedMethodPredicate {

    public GeneratedGetterPredicate(FieldDeclaration fieldDeclaration) {
        super(fieldDeclaration,
                type -> "boolean".equals(type) ? "is" : "get");
    }

    @Override
    public boolean test(MethodDeclaration method) {
        return super.test(method)
                && fieldType.equals(method.getTypeAsString())
                && method.getParameters().isEmpty()
                && equalsGeneratedBody(method);
    }

    private boolean equalsGeneratedBody(MethodDeclaration method) {
        return method.getBody()
                // メソッド内は1文だけ
                .map(BlockStmt::getStatements)
                .filter(statements -> statements.size() == 1)
                .map(statements -> statements.get(0))
                // return に返り値がある
                .filter(Statement::isReturnStmt)
                .map(statement -> statement.asReturnStmt().getExpression())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isReferredFrom)
                .isPresent();
    }
}
