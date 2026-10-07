import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jetbrains.kotlin.lexer.KotlinLexer;
import org.jetbrains.kotlin.lexer.KtTokens;

class RenameProjectIdentifiers {
    static Map<String, String> modelNames = new HashMap<>();
    static String renamed(String identifier) {
        if (modelNames.containsKey(identifier)) return modelNames.get(identifier);
        return switch (identifier) {
            case "ANI" -> "WYNIME";
            case "NoAniAccount" -> "NoBangumiAccount";
            case "LoggedInAni" -> "LoggedInBangumi";
            case "aniAccessToken" -> "legacyServiceAccessToken";
            case "aniServerRules" -> "serviceServerRules";
            case "aniBuildConfig" -> "wynimeBuildConfig";
            case "ANIMEKO" -> "LEGACY_SERVICE";
            default -> identifier.replaceAll("Ani(?=[A-Z])", "Wynime")
                .replaceAll("^ani(?=[A-Z])", "wynime")
                .replaceAll("^_ani(?=[A-Z])", "_wynime")
                .replace("Animeko", "Wynime").replace("animeko", "wynime");
        };
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toRealPath();
        if (Arrays.asList(args).contains("--model-names")) {
            try (var paths = Files.list(root.resolve("app-models/src/commonMain/kotlin/com/wynime/models"))) {
                paths.filter(path -> path.getFileName().toString().matches("Wynime.*\\.kt")).forEach(path -> {
                    String name = path.getFileName().toString().replace(".kt", "");
                    String replacement = switch (name) {
                        case "WynimeUPSERT" -> "PlaybackHistoryUpsertDto";
                        case "WynimeDELETE" -> "PlaybackHistoryDeleteDto";
                        case "WynimeSyncRequest" -> "PlaybackHistorySyncRequestDto";
                        default -> name.replaceFirst("^Wynime", "") + "Dto";
                    };
                    modelNames.put(name, replacement);
                });
            }
        }
        var names = new TreeMap<String, String>();
        Path inventory = root.resolve(".tmp-code-cleanup/identifier-renames.tsv");
        if (Files.isRegularFile(inventory)) {
            for (String line : Files.readAllLines(inventory)) {
                String[] pair = line.split("\t", 2);
                if (pair.length == 2) names.put(pair[0], pair[1]);
            }
        }
        int files = 0;
        for (String relative : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
            Path path = root.resolve(relative).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) continue;
            String original = Files.readString(path);
            StringBuilder result = new StringBuilder();
            KotlinLexer lexer = new KotlinLexer();
            lexer.start(original);
            while (lexer.getTokenType() != null) {
                String token = original.substring(lexer.getTokenStart(), lexer.getTokenEnd());
                if (lexer.getTokenType() == KtTokens.IDENTIFIER) {
                    String replacement = renamed(token);
                    if (!replacement.equals(token)) names.put(token, replacement);
                    token = replacement;
                }
                result.append(token);
                lexer.advance();
            }
            if (!result.toString().equals(original)) {
                files++;
                if (Arrays.asList(args).contains("--apply")) {
                    for (int attempt = 0; ; attempt++) {
                        try {
                            Files.writeString(path, result.toString());
                            break;
                        } catch (java.nio.file.FileSystemException error) {
                            if (attempt >= 5) throw error;
                            Thread.sleep(200L * (attempt + 1));
                        }
                    }
                }
            }
        }
        var lines = new ArrayList<String>();
        names.forEach((before, after) -> lines.add(before + "\t" + after));
        Files.write(inventory, lines);
        System.out.println("Identifier-only rename: files=" + files + " names=" + names.size());
    }
}
