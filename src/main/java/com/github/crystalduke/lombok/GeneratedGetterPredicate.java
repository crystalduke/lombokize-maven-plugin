package com.github.crystalduke.lombok;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.Optional;
import lombok.Getter;

/**
 * メソッド本体が {@link Getter} によって生成されたものと同じか判定する.
 */
public class GeneratedGetterPredicate extends GeneratedMethodPredicate {

    /**
     * {@link Getter} を付与するフィールドを指定してオブジェクトを構築する.
     *
     * @param variable {@link Getter} を付与するフィールドの変数.
     */
    public GeneratedGetterPredicate(VariableDeclarator variable) {
        super(variable,
                type -> "boolean".equals(type) ? "is" : "get");
    }

    /**
     * 引数のメソッドがオブジェクト構築時に指定したフィールドに {@link Getter} を付与することで生成可能か判定する.
     *
     * @param method メソッド定義
     * @return 生成可能であれば {@code true}, それ以外は {@code false}.
     */
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
                // 返り値はコンストラクタで指定したフィールド
                .filter(this::isReferredFrom)
                .isPresent();
    }
}
