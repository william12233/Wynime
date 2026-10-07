import java.nio.file.*;
import java.util.*;
import org.jetbrains.kotlin.lexer.KotlinLexer;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtPsiFactory;

class AuditModelReferences {
    static Set<String> identifiers(String text) {
        var result = new HashSet<String>();
        var lexer = new KotlinLexer();
        lexer.start(text);
        while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() == KtTokens.IDENTIFIER) result.add(text.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
            lexer.advance();
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toRealPath();
        Path directory = root.resolve("app-models/src/commonMain/kotlin/com/wynime/models");
        var models = new TreeMap<String, Path>();
        try (var files = Files.list(directory)) {
            files.filter(file -> file.toString().endsWith(".kt")).forEach(file -> models.put(file.getFileName().toString().replace(".kt", ""), file));
        }
        var used = new TreeSet<String>();
        for (String relative : Files.readAllLines(Path.of(args[1]))) {
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || file.startsWith(directory) || !Files.isRegularFile(file)) continue;
            String text = Files.readString(file);
            Set<String> names = relative.endsWith(".kt") || relative.endsWith(".kts") ? identifiers(text) : Set.of();
            for (String name : models.keySet()) {
                if (names.contains(name) || text.contains("com.wynime.models." + name)) used.add(name);
            }
        }
        var dependencies = new TreeMap<String, Set<String>>();
        var implementors = new TreeMap<String, Set<String>>();
        var disposable = Disposer.newDisposable();
        var environment = KotlinCoreEnvironment.createForProduction(disposable, new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
        var parser = new KtPsiFactory(environment.getProject(), false);
        for (var item : models.entrySet()) {
            String text = Files.readString(item.getValue());
            Set<String> names = identifiers(text);
            names.retainAll(models.keySet());
            dependencies.put(item.getKey(), names);
            for (var declaration : PsiTreeUtil.findChildrenOfType(parser.createFile(text), KtClassOrObject.class)) {
                for (var supertype : declaration.getSuperTypeListEntries()) {
                    if (supertype.getTypeReference() == null) continue;
                    String parent = supertype.getTypeReference().getText();
                    if (models.containsKey(parent)) implementors.computeIfAbsent(parent, ignored -> new TreeSet<>()).add(item.getKey());
                }
            }
        }
        Disposer.dispose(disposable);
        int count;
        do {
            count = used.size();
            for (String name : new ArrayList<>(used)) {
                used.addAll(dependencies.get(name));
                used.addAll(implementors.getOrDefault(name, Set.of()));
            }
        } while (count != used.size());
        var unused = new TreeSet<>(models.keySet());
        unused.removeAll(used);
        Files.write(root.resolve(".tmp-code-cleanup/unused-models.txt"), unused);
        Files.write(root.resolve(".tmp-code-cleanup/retained-models.txt"), used);
        System.out.println("Model identifier, dynamic FQN and subtype closure: retained=" + used.size() + " unused=" + unused.size());
        unused.forEach(System.out::println);
    }
}
