package com.github.crystalduke.lombok;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
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
        super(variable, isBoolean -> isBoolean ? "is" : "get");
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
        final NodeList<Parameter> parameters = method.getParameters();
        if (!(parameters.isEmpty()
                || parameters.size() == 1 && parameters.get(0).isVarArgs())) {
            return false;
        }
        String name = method.getNameAsString();
        if (name.equalsIgnoreCase("get" + fieldName)) {
            return true;
        }
        if ("boolean".equals(fieldType)) {
            if (name.equalsIgnoreCase("is" + fieldName)) {
                return true;
            }
            if (startsWithIs(fieldName)) {
                return name.equalsIgnoreCase(fieldName)
                        || name.equalsIgnoreCase("get" + fieldName.substring(2));
            }
        }
        return false;
    }

    /**
     * 引数のメソッドがオブジェクト構築時に指定したフィールドに {@link Getter} を付与することで生成可能か判定する.
     *
     * @param method メソッド定義
     * @return 生成可能であれば {@code true}, それ以外は {@code false}.
     */
    @Override
    public boolean canGenerate(MethodDeclaration method) {
        return super.canGenerate(method)
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
