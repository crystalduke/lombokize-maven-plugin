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

public class TokenUtil {

	public static String asString(Node node) {
		return node.getTokenRange()
				.map(TokenUtil::asString)
				.orElse(null);
	}

	public static String asString(Iterable<JavaToken> tokens) {
		StringBuilder content = new StringBuilder();
		for (JavaToken token : tokens) {
			content.append(token.asString());
		}
		return content.toString();
	}

	public static TokenRange remove(JavaToken begin, JavaToken end) {
		TokenRange tokenRange = new TokenRange(begin, end);
		tokenRange.forEach(JavaToken::deleteToken);
		return tokenRange;
	}

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
		return remove(beginToken, range.getEnd());
	}

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
