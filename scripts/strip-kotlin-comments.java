import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jetbrains.kotlin.lexer.KotlinLexer;
import org.jetbrains.kotlin.lexer.KtTokens;

class StripKotlinComments {
    static List<String> tokens(String text) {
        KotlinLexer lexer = new KotlinLexer();
        lexer.start(text);
        List<String> result = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            if (!KtTokens.COMMENTS.contains(lexer.getTokenType()) && lexer.getTokenType() != KtTokens.WHITE_SPACE) {
                result.add(lexer.getTokenType() + ":" + text.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
            }
            lexer.advance();
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toRealPath();
        int files = 0;
        int comments = 0;
        Path selection = Path.of(args[1]);
        List<String> selected;
        if (Files.isDirectory(selection)) {
            try (var paths = Files.walk(selection)) {
                selected = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".kt") || path.toString().endsWith(".kts"))
                    .map(path -> root.relativize(path.toAbsolutePath().normalize()).toString()).toList();
            }
        } else {
            selected = Files.readAllLines(selection, StandardCharsets.UTF_8);
        }
        boolean checkOnly = args.length > 2 && args[2].equals("--check");
        for (String relative : selected) {
            Path path = root.resolve(relative).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) continue;
            String text = Files.readString(path).replace("\r\n", "\n");
            char[] cleaned = text.toCharArray();
            KotlinLexer lexer = new KotlinLexer();
            lexer.start(text);
            List<String> notices = new ArrayList<>();
            int count = 0;
            while (lexer.getTokenType() != null) {
                if (KtTokens.COMMENTS.contains(lexer.getTokenType())) {
                    String value = text.substring(lexer.getTokenStart(), lexer.getTokenEnd());
                    if (value.startsWith("#!")) {
                        lexer.advance();
                        continue;
                    }
                    if (value.contains("Copyright") || value.contains("SPDX-License-Identifier")) notices.add(value);
                    for (int i = lexer.getTokenStart(); i < lexer.getTokenEnd(); i++) {
                        if (cleaned[i] != '\n' && cleaned[i] != '\r') cleaned[i] = ' ';
                    }
                    count++;
                }
                lexer.advance();
            }
            String result = new String(cleaned);
            KotlinLexer whitespaceLexer = new KotlinLexer();
            whitespaceLexer.start(result);
            StringBuilder compact = new StringBuilder();
            while (whitespaceLexer.getTokenType() != null) {
                String value = result.substring(whitespaceLexer.getTokenStart(), whitespaceLexer.getTokenEnd());
                if (whitespaceLexer.getTokenType() == KtTokens.WHITE_SPACE) {
                    value = value.replaceAll("[ \\t]+(?=\\n)", "").replaceAll("\\n{3,}", "\n\n");
                    if (whitespaceLexer.getTokenStart() == 0) value = value.stripLeading();
                }
                compact.append(value);
                whitespaceLexer.advance();
            }
            result = compact.toString();
            if (!tokens(text).equals(tokens(result))) {
                List<String> before = tokens(text);
                List<String> after = tokens(result);
                for (int i = 0; i < Math.min(before.size(), after.size()); i++) {
                    if (!before.get(i).equals(after.get(i))) {
                        System.out.println("Before " + before.get(i) + " After " + after.get(i));
                        break;
                    }
                }
                throw new IllegalStateException("Token change: " + relative);
            }
            if (!checkOnly && !notices.isEmpty()) {
                Path notice = root.resolve("licenses/source-notices").resolve(relative + ".license");
                Files.createDirectories(notice.getParent());
                if (!Files.exists(notice)) Files.writeString(notice, String.join("\n\n", notices) + "\n");
            }
            if (!checkOnly && !result.equals(text)) {
                for (int attempt = 0; ; attempt++) {
                    try {
                        Files.writeString(path, result);
                        break;
                    } catch (java.nio.file.FileSystemException error) {
                        if (attempt >= 5) throw error;
                        Thread.sleep(200L * (attempt + 1));
                    }
                }
            }
            files++;
            comments += count;
        }
        System.out.println("files=" + files + " comments=" + comments + " non-comment token equality=PASS");
        if (checkOnly && comments != 0) throw new IllegalStateException("Source comments remain: " + comments);
    }
}
