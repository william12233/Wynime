import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.security.MessageDigest;

class PrepareVideoShaderResources {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]).toAbsolutePath().normalize();
        Path destination = Path.of(args[1]).toAbsolutePath().normalize();
        boolean check = args.length > 2 && args[2].equals("--check");
        Properties contracts = new Properties();
        try (var reader = Files.newBufferedReader(source.resolve("directive-contracts.properties"), StandardCharsets.UTF_8)) {
            contracts.load(reader);
        }
        int files = 0;
        int directives = 0;
        try (var paths = Files.walk(source)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (!(name.endsWith(".glsl") || name.endsWith(".frag") || name.endsWith(".vert"))) continue;
                String text = Files.readString(file).replace("\r\n", "\n");
                List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
                Path settings = file.resolveSibling(name + ".directives.properties");
                if (Files.isRegularFile(settings)) {
                    Properties properties = new Properties();
                    try (var reader = Files.newBufferedReader(settings, StandardCharsets.UTF_8)) {
                        properties.load(reader);
                    }
                    for (String key : properties.stringPropertyNames()) {
                        int line = Integer.parseInt(key);
                        String instruction = properties.getProperty(key);
                        if (line < 0 || line >= lines.size() || !lines.get(line).isBlank()
                            || instruction.isBlank() || instruction.contains("\n") || instruction.contains("\r")) {
                            throw new IllegalArgumentException("Invalid shader instruction: " + settings + ":" + key);
                        }
                        lines.set(line, "//!" + instruction);
                        directives++;
                    }
                }
                Path target = destination.resolve("files/shaders").resolve(source.relativize(file)).normalize();
                if (!target.startsWith(destination)) throw new IllegalArgumentException("Shader escapes resource directory");
                String program = String.join("\n", lines);
                String commands = String.join("\n", lines.stream().filter(line -> line.startsWith("//!")).toList());
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(commands.getBytes(StandardCharsets.UTF_8)));
                String key = source.relativize(file).toString().replace('\\', '/');
                if (!hash.equals(contracts.getProperty(key))) throw new IllegalStateException("Shader directive contract changed: " + key);
                if (check) {
                    if (!program.equals(Files.readString(target).replace("\r\n", "\n"))) {
                        throw new IllegalStateException("Generated shader changed: " + target);
                    }
                } else {
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, program, StandardCharsets.UTF_8);
                }
                files++;
            }
        }
        System.out.println("Video shader resources=" + files + " execution directives=" + directives);
    }
}
