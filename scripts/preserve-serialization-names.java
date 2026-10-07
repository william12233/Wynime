import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.lexer.KtTokens;

class PreserveSerializationNames {
    public static void main(String[] args) throws Exception {
        var disposable = Disposer.newDisposable();
        var environment = KotlinCoreEnvironment.createForProduction(disposable, new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
        var factory = new KtPsiFactory(environment.getProject(), false);
        Path root = Path.of(args[0]).toRealPath();
        int count = 0;
        for (String relative : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
            Path path = root.resolve(relative).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) continue;
            String text = Files.readString(path);
            KtFile file = factory.createFile(text);
            var insertions = new TreeMap<Integer, String>(Comparator.reverseOrder());
            for (var declaration : PsiTreeUtil.collectElementsOfType(file, KtClassOrObject.class)) {
                var annotations = declaration.getAnnotationEntries();
                boolean serializable = annotations.stream().anyMatch(a -> a.getShortName() != null && a.getShortName().asString().equals("Serializable"));
                boolean named = annotations.stream().anyMatch(a -> a.getShortName() != null && a.getShortName().asString().equals("SerialName"));
                if (!serializable || named || declaration.getFqName() == null) continue;
                var parent = PsiTreeUtil.getParentOfType(declaration, KtClassOrObject.class, true);
                boolean sealedSubtype = parent != null && parent.hasModifier(KtTokens.SEALED_KEYWORD);
                if (!sealedSubtype && !"DefaultMedia".equals(declaration.getName())) continue;
                String name = declaration.getFqName().asString();
                if (!name.startsWith("me.him188.ani.")) continue;
                int offset = declaration.getTextRange().getStartOffset();
                int line = text.lastIndexOf('\n', offset - 1) + 1;
                String indent = text.substring(line, offset);
                if (!indent.isBlank()) throw new IllegalStateException("Unsupported declaration indentation: " + relative);
                insertions.put(offset, "@SerialName(\"" + name + "\")\n" + indent);
            }
            if (insertions.isEmpty()) continue;
            StringBuilder edited = new StringBuilder(text);
            for (var insertion : insertions.entrySet()) edited.insert(insertion.getKey(), insertion.getValue());
            if (!text.contains("import kotlinx.serialization.SerialName") && !text.contains("import kotlinx.serialization.*")) {
                int end = edited.indexOf("\n", edited.indexOf("package "));
                edited.insert(end + 1, "\nimport kotlinx.serialization.SerialName\n");
            }
            if (args.length > 2 && args[2].equals("--apply")) Files.writeString(path, edited.toString());
            else System.out.println(relative + ": " + insertions.values());
            count += insertions.size();
        }
        Disposer.dispose(disposable);
        System.out.println("Preserved serialization descriptor names via Kotlin AST: " + count);
    }
}
