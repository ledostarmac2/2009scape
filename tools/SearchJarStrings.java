import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class SearchJarStrings {
    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile(Path.of(args[0]).toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = jar.getInputStream(entry)) {
                    in.transferTo(out);
                }
                String text = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
                for (int i = 1; i < args.length; i++) {
                    if (text.contains(args[i])) {
                        System.out.println(entry.getName() + "\t" + args[i]);
                        break;
                    }
                }
            }
        }
    }
}
