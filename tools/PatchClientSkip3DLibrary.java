import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class PatchClientSkip3DLibrary {
    private static final String TARGET_ENTRY = "rt4/MaterialManager.class";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: PatchClientSkip3DLibrary <client.jar>");
        }
        Path jar = Path.of(args[0]);
        Path tmp = Files.createTempFile(jar.getParent(), "client-skip-3d-", ".jar");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (JarFile jf = new JarFile(jar.toFile())) {
            var en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry entry = en.nextElement();
                try (InputStream in = jf.getInputStream(entry)) {
                    byte[] data = in.readAllBytes();
                    if (TARGET_ENTRY.equals(entry.getName())) {
                        data = patchMethod2807(data);
                    }
                    entries.put(entry.getName(), data);
                }
            }
        }
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(tmp))) {
            for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                JarEntry entry = new JarEntry(item.getKey());
                out.putNextEntry(entry);
                out.write(item.getValue());
                out.closeEntry();
            }
        }
        Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Patched " + TARGET_ENTRY + " method2807() to return immediately.");
    }

    private static byte[] patchMethod2807(byte[] cls) throws Exception {
        ClassReader r = new ClassReader(cls);
        Object[] cp = r.readConstantPool();
        r.skip(6); // access_flags, this_class, super_class
        int interfaces = r.u2();
        r.skip(interfaces * 2);
        skipMembers(r); // fields
        int methods = r.u2();
        for (int m = 0; m < methods; m++) {
            r.skip(2);
            String name = (String) cp[r.u2()];
            String desc = (String) cp[r.u2()];
            int attrs = r.u2();
            for (int a = 0; a < attrs; a++) {
                String attrName = (String) cp[r.u2()];
                int attrLen = r.u4();
                int attrStart = r.pos;
                if ("method2807".equals(name) && "()V".equals(desc) && "Code".equals(attrName)) {
                    r.skip(4); // max_stack, max_locals
                    int codeLen = r.u4();
                    int codeStart = r.pos;
                    if (codeLen <= 0) {
                        throw new IllegalStateException("method2807 has empty code");
                    }
                    cls[codeStart] = (byte) 0xB1; // return
                    for (int i = 1; i < codeLen; i++) {
                        cls[codeStart + i] = 0; // nop
                    }
                    return cls;
                }
                r.pos = attrStart + attrLen;
            }
        }
        throw new IllegalStateException("method2807()V Code attribute not found");
    }

    private static void skipMembers(ClassReader r) {
        int count = r.u2();
        for (int i = 0; i < count; i++) {
            r.skip(6);
            int attrs = r.u2();
            for (int a = 0; a < attrs; a++) {
                r.skip(2);
                int len = r.u4();
                r.skip(len);
            }
        }
    }

    private static final class ClassReader {
        final byte[] data;
        int pos;

        ClassReader(byte[] data) {
            this.data = data;
        }

        Object[] readConstantPool() {
            if (u4() != 0xCAFEBABE) {
                throw new IllegalArgumentException("not a class file");
            }
            skip(4); // minor, major
            int count = u2();
            Object[] cp = new Object[count];
            for (int i = 1; i < count; i++) {
                int tag = u1();
                switch (tag) {
                    case 1:
                        int len = u2();
                        cp[i] = new String(data, pos, len, java.nio.charset.StandardCharsets.UTF_8);
                        skip(len);
                        break;
                    case 3:
                    case 4:
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 18:
                        skip(4);
                        break;
                    case 5:
                    case 6:
                        skip(8);
                        i++;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        skip(2);
                        break;
                    case 15:
                        skip(3);
                        break;
                    default:
                        throw new IllegalArgumentException("unsupported constant pool tag " + tag);
                }
            }
            return cp;
        }

        int u1() {
            return data[pos++] & 0xFF;
        }

        int u2() {
            int v = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;
            return v;
        }

        int u4() {
            int v = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                    | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
            pos += 4;
            return v;
        }

        void skip(int n) {
            pos += n;
        }
    }
}
