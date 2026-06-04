import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class PatchBotWorld {
    private static final String HELPER = "custom/bots/BotWorldEnhancer";
    private static final String COMPANION = "core/game/world/ImmerseWorld$Companion";
    private static final String ADVENTURER_TASK = "core/game/world/ImmerseWorld$Companion$immerseAdventurer$$inlined$schedule$1";
    private static final String AI_PLAYER = "core/game/bots/AIPlayer";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: PatchBotWorld <server.jar> <helper class root> <backup.jar>");
        }
        Path jar = Path.of(args[0]);
        Path helperRoot = Path.of(args[1]);
        Path backup = Path.of(args[2]);
        Files.copy(jar, backup, StandardCopyOption.REPLACE_EXISTING);

        Path tmp = Files.createTempFile(jar.getParent(), "server-botpatch-", ".jar");
        Set<String> written = new HashSet<>();
        try (JarFile in = new JarFile(jar.toFile()); JarOutputStream out = new JarOutputStream(Files.newOutputStream(tmp))) {
            Enumeration<JarEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] data;
                try (InputStream stream = in.getInputStream(entry)) {
                    data = readAll(stream);
                }
                String name = entry.getName();
                if (name.equals(COMPANION + ".class")) {
                    data = transformCompanion(data);
                } else if (name.equals(ADVENTURER_TASK + ".class")) {
                    data = transformAdventurerTask(data);
                } else if (name.equals(AI_PLAYER + ".class")) {
                    data = transformAIPlayer(data);
                }
                write(out, name, data, written);
            }

            Path helper = helperRoot.resolve("custom/bots/BotWorldEnhancer.class");
            write(out, HELPER + ".class", Files.readAllBytes(helper), written);
        }

        Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Patched " + jar + " and backed up to " + backup);
    }

    private static byte[] transformCompanion(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if (!name.equals("spawnBots$lambda$0") || !desc.equals("()V")) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM8, mv) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "installFixed", "()V", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    private static byte[] transformAdventurerTask(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if (!name.equals("run") || !desc.equals("()V")) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM8, mv) {
                    private boolean heldCompanionGetStatic;

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        if (opcode == Opcodes.GETSTATIC && owner.equals("core/game/world/ImmerseWorld") && name.equals("Companion")) {
                            flushHeld();
                            heldCompanionGetStatic = true;
                            return;
                        }
                        flushHeld();
                        super.visitFieldInsn(opcode, owner, name, desc);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        if (heldCompanionGetStatic && opcode == Opcodes.INVOKEVIRTUAL && owner.equals(COMPANION)
                                && name.equals("spawn_adventurers") && desc.equals("()V")) {
                            heldCompanionGetStatic = false;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "spawnRandomAdventurer", "()V", false);
                            return;
                        }
                        flushHeld();
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        flushHeld();
                        super.visitInsn(opcode);
                    }

                    private void flushHeld() {
                        if (heldCompanionGetStatic) {
                            heldCompanionGetStatic = false;
                            super.visitFieldInsn(Opcodes.GETSTATIC, "core/game/world/ImmerseWorld", "Companion", "Lcore/game/world/ImmerseWorld$Companion;");
                        }
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    private static byte[] transformAIPlayer(byte[] data) {
        ClassReader reader = new ClassReader(data);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM8, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if (!name.equals("tick") || !desc.equals("()V")) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM8, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                        if (opcode == Opcodes.INVOKESPECIAL && owner.equals("core/game/node/entity/player/Player")
                                && name.equals("tick") && desc.equals("()V")) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "updateRun", "(Lcore/game/bots/AIPlayer;)V", false);
                        }
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    private static void write(JarOutputStream out, String name, byte[] data, Set<String> written) throws IOException {
        if (!written.add(name)) {
            return;
        }
        JarEntry e = new JarEntry(name);
        out.putNextEntry(e);
        out.write(data);
        out.closeEntry();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
