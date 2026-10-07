import java.nio.file.*;
import java.util.*;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;
import org.antlr.v4.parse.ANTLRLexer;

class StripAntlrComments {
    static List<Token> lex(String text) {
        ANTLRLexer lexer = new ANTLRLexer(new ANTLRStringStream(text));
        List<Token> result = new ArrayList<>();
        for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
            if (token.getType() == ANTLRLexer.ERRCHAR) throw new IllegalArgumentException("Invalid grammar token: " + token);
            result.add(token);
        }
        return result;
    }

    static boolean comment(Token token) {
        return token.getType() == ANTLRLexer.COMMENT || token.getType() == ANTLRLexer.DOC_COMMENT;
    }

    static List<String> signature(String text) {
        return lex(text).stream().filter(token -> !comment(token) && token.getType() != ANTLRLexer.WS)
            .map(token -> token.getType() + ":" + token.getText()).toList();
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        String original = Files.readString(file);
        char[] cleaned = original.toCharArray();
        int count = 0;
        for (Token token : lex(original)) {
            if (!comment(token)) continue;
            CommonToken value = (CommonToken) token;
            for (int i = value.getStartIndex(); i <= value.getStopIndex(); i++) {
                if (cleaned[i] != '\n' && cleaned[i] != '\r') cleaned[i] = ' ';
            }
            count++;
        }
        String result = new String(cleaned);
        if (!signature(original).equals(signature(result))) throw new IllegalStateException("Grammar token changed");
        boolean check = args.length > 1 && args[1].equals("--check");
        if (!check && !original.equals(result)) Files.writeString(file, result);
        System.out.println("ANTLR grammar comments=" + count + " token equality=PASS");
        if (check && count > 0) System.exit(1);
    }
}
