package com.github.crystalduke.lombok;

import com.github.javaparser.JavaToken;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.LineSeparator;
import java.util.Optional;

/**
 * {@link JavaToken} に関するユーティリティメソッドを集めたクラス.
 */
public class TokenUtil {

    /**
     * 引数の {@link Node} に含まれる {@link JavaToken} を連結した文字列を返す.
     */
    public static String asString(Node node) {
        return node.getTokenRange()
                .map(TokenUtil::asString)
                .orElse(null);
    }

    /**
     * 引数の {@link Node} に含まれる {@link JavaToken} を連結した文字列を返す.
     */
    public static String asString(Iterable<JavaToken> tokens) {
        StringBuilder content = new StringBuilder();
        for (JavaToken token : tokens) {
            content.append(token.asString());
        }
        return content.toString();
    }

    /**
     * 指定した範囲の {@link JavaToken} を削除する.
     *
     * @param begin 削除開始位置
     * @param end 削除終了位置
     * @return 削除した範囲.
     */
    public static TokenRange remove(JavaToken begin, JavaToken end) {
        TokenRange tokenRange = new TokenRange(begin, end);
        tokenRange.forEach(JavaToken::deleteToken);
        return tokenRange;
    }

    /**
     * メソッド宣言を削除する. このメソッドでは、メソッド宣言の前にある空白やコメントも削除する.
     *
     * @param method 削除するメソッド宣言
     * @return 削除された範囲.
     */
    public static TokenRange remove(MethodDeclaration method) {
        TokenRange range = method.getTokenRange().get();
        JavaToken beginToken = range.getBegin();
        // メソッド宣言から前の空白・コメントを削除する
        while (beginToken.getPreviousToken().isPresent()) {
            JavaToken previousToken = beginToken.getPreviousToken().get();
            if (!previousToken.getCategory().isWhitespaceOrComment()) {
                break;
            }
            beginToken = previousToken;
        }
        JavaToken endToken = removeUntilEol(range.getEnd());
        if (endToken.getNextToken().isPresent()
                && endToken.getNextToken().get().getCategory().isEndOfLine()) {
            // メソッド終了位置から改行まで削除する場合は、最初の改行まで削除しない
            beginToken = ignoreUntilEol(beginToken);
        }
        return remove(beginToken, endToken);
    }

    private static JavaToken removeUntilEol(JavaToken endToken) {
        // メソッド終了から行末まで空白文字だけであれば、空白文字も削除する
        JavaToken token = endToken;
        while (token.getNextToken().isPresent()) {
            JavaToken nextToken = token.getNextToken().get();
            final JavaToken.Category category = nextToken.getCategory();
            if (category.isEndOfLine()) {
                break;
            } else if (!category.isWhitespace()) {
                // 行末までに空白文字以外があれば、終了位置は引数のまま
                return endToken;
            }
            token = nextToken;
        }
        return token;
    }
    
    private static JavaToken ignoreUntilEol(JavaToken beginToken) {
        // 削除開始位置から行末まで空白文字以外に改行を含まないコメントがあれば、削除しない
        JavaToken token = beginToken;
        boolean containsComments = false;
        while (true) {
            final JavaToken.Category category = token.getCategory();
            if (category.isEndOfLine()) {
                break;
            } else if (!category.isWhitespaceOrComment()) {
                // 行末までに空白文字とコメント以外があれば、削除開始位置は引数のまま
                return beginToken;
            } else if (category.isComment()) {
                containsComments = true;
                // コメントに改行が含まれていれば、削除開始位置は引数のまま
                String comment = token.getText();
                if (comment.indexOf('\n') >= 0 || comment.indexOf('\r') >= 0) {
                    return beginToken;
                }
            }
            if (!token.getNextToken().isPresent()) {
                break;
            }
            token = token.getNextToken().get();
        }
        // 行末までにコメントがなければ、削除開始位置は元のまま
        return containsComments ? token : beginToken;
    }

    /**
     * アノテーションを削除する. このメソッドでは、アノテーションに続く空白文字列も削除する.
     *
     * @param annotation 削除するアノテーション.
     * @return 削除した {@link JavaToken}.
     */
    public static TokenRange remove(AnnotationExpr annotation) {
        TokenRange range = annotation.getTokenRange().get();
        JavaToken endToken = range.getEnd();
        // アノテーション本体に続く空白を削除する
        while (endToken.getNextToken().isPresent()) {
            JavaToken nextToken = endToken.getNextToken().get();
            if (!nextToken.getCategory().isWhitespace()) {
                break;
            }
            endToken = nextToken;
        }
        return remove(range.getBegin(), endToken);
    }

    /**
     * {@link JavaToken} を複製する. このクラスでは、{@link JavaToken#getKind()} と
     * {@link JavaToken#getText()} が等しいインスタンスを生成する.
     *
     * @param token 複製元のインスタンス
     * @return 複製したインスタンス.
     */
    public static JavaToken clone(JavaToken token) {
        return new JavaToken(token.getKind(), token.getText());
    }

    static AnnotationExpr clone(AnnotationExpr annotation) {
        return StaticJavaParser.parseAnnotation(
                LexicalPreservingPrinter.print(annotation));
    }

    static ImportDeclaration clone(ImportDeclaration importDeclaration) {
        return StaticJavaParser.parseImport(
                LexicalPreservingPrinter.print(importDeclaration));
    }

    /**
     * ノードにアノテーションを付与する. このクラスでは、アノテーションは以下の位置に付与する.
     * <ul>
     * <li>ノードが空白以外で行頭にある場合、そのノードと同じインデントでノードの前の行にアノテーションを付与する.</li>
     * <li>それ以外の場合、アノテーションの前後に半角空白をつけて、ノードの前（同じ行）に付与する.</li>
     * </ul>
     *
     * @param target アノテーションを付与するノード
     * @param annotation アノテーション.
     */
    public static void addAnnotation(Node target, AnnotationExpr annotation) {
        JavaToken fieldBegin = target.getTokenRange().get().getBegin();
        JavaToken lineSeparatorToken = null;
        JavaToken indentTokenBegin = null;
        JavaToken indentTokenEnd = null;
        for (Optional<JavaToken> token = fieldBegin.getPreviousToken();
                token.isPresent() && lineSeparatorToken == null;) {
            JavaToken previousToken = token.get();
            switch (previousToken.getCategory()) {
                case WHITESPACE_NO_EOL:
                    // 行頭までの空白文字列をアノテーションのインデントにする
                    indentTokenBegin = previousToken;
                    if (indentTokenEnd == null) {
                        indentTokenEnd = indentTokenBegin;
                    }
                    break;
                case EOL:
                    lineSeparatorToken = previousToken;
                    break;
                default:
                    fieldBegin.insert(space());
                    indentTokenBegin = indentTokenEnd = null;
                    lineSeparatorToken = space();
            }
            token = previousToken.getPreviousToken();
        }
        clone(annotation).getTokenRange().get().forEach(fieldBegin::insert);
        if (lineSeparatorToken != null) {
            fieldBegin.insert(clone(lineSeparatorToken));
        }
        if (indentTokenBegin != null) {
            new TokenRange(indentTokenBegin, indentTokenEnd)
                    .forEach(token -> fieldBegin.insert(clone(token)));
        }
    }

    /**
     * 引数の {@link CompilationUnit#getLineEndingStyle()} を元に、改行の {@link JavaToken] を返す.
     *
     * @param cu    {@link CompilationUnit}
     * @return 改行を表す {@link JavaToken}
     */
    public static JavaToken lineSeparator(CompilationUnit cu) {
        JavaToken.Kind kind;
        LineSeparator lineSeparator = cu.getLineEndingStyle();
        switch (lineSeparator) {
            case CRLF:
                kind = JavaToken.Kind.WINDOWS_EOL;
                break;
            case LF:
                kind = JavaToken.Kind.UNIX_EOL;
                break;
            default:
                throw new IllegalStateException("Illegal line separator: " + lineSeparator);
        }
        return new JavaToken(kind.getKind(), lineSeparator.asRawString());
    }

    private static JavaToken space() {
        return new JavaToken(JavaToken.Kind.SPACE.getKind(), " ");
    }
}
