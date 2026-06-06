import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class PatchDragonfireProtection implements Opcodes {
    private static final int LEDOSTARS_STING = 14547;
    private static final String CONTENT_API = "core/api/ContentAPIKt.class";
    private static final String PLAYER = "core/game/node/entity/player/Player.class";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: PatchDragonfireProtection <server.jar> [server.jar...]");
        }
        for (String arg : args) {
            patchJar(Path.of(arg));
        }
    }

    private static void patchJar(Path jar) throws Exception {
        byte[] original = Files.readAllBytes(jar);
        Set<String> seen = new HashSet<>();
        boolean[] changed = { false };
        Path backup = jar.resolveSibling(jar.getFileName() + ".bak-sting-dragonfire-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.copy(jar, backup);
        Path tmp = Files.createTempFile(jar.getParent(), "server-sting-dragonfire-", ".jar");
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] data = readAll(in);
                String name = entry.getName();
                if (name.equals(CONTENT_API)) {
                    data = patchContentApi(data);
                    changed[0] = true;
                } else if (name.equals(PLAYER)) {
                    data = patchPlayer(data);
                    changed[0] = true;
                }
                ZipEntry next = new ZipEntry(name);
                next.setTime(entry.getTime());
                out.putNextEntry(next);
                out.write(data);
                out.closeEntry();
                seen.add(name);
            }
        } catch (Exception ex) {
            Files.deleteIfExists(tmp);
            Files.write(jar, original);
            throw ex;
        }
        if (!seen.contains(CONTENT_API) || !seen.contains(PLAYER)) {
            Files.deleteIfExists(tmp);
            throw new IllegalStateException("server jar did not contain both target classes: " + jar);
        }
        Files.move(tmp, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Patched Ledostar's Sting dragonfire protection in " + jar + " (backup " + backup + ")");
    }

    private static byte[] patchContentApi(byte[] bytes) {
        if (methodContainsStingId(bytes, "hasDragonfireShieldProtection", "(Lcore/game/node/entity/player/Player;Z)Z")) {
            return bytes;
        }
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = classWriter(cr);
        cr.accept(new ClassVisitor(ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, exceptions);
                if (name.equals("hasDragonfireShieldProtection") && desc.equals("(Lcore/game/node/entity/player/Player;Z)Z")) {
                    return new MethodVisitor(ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            Label noItem = new Label();
                            Label done = new Label();
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETSTATIC, "core/api/EquipmentSlot", "SHIELD", "Lcore/api/EquipmentSlot;");
                            mv.visitMethodInsn(INVOKESTATIC, "core/api/ContentAPIKt", "getItemFromEquipment",
                                    "(Lcore/game/node/entity/player/Player;Lcore/api/EquipmentSlot;)Lcore/game/node/item/Item;", false);
                            mv.visitInsn(DUP);
                            mv.visitJumpInsn(IFNULL, noItem);
                            mv.visitMethodInsn(INVOKEVIRTUAL, "core/game/node/item/Item", "getId", "()I", false);
                            mv.visitIntInsn(SIPUSH, LEDOSTARS_STING);
                            mv.visitJumpInsn(IF_ICMPNE, done);
                            mv.visitInsn(ICONST_1);
                            mv.visitInsn(IRETURN);
                            mv.visitLabel(noItem);
                            mv.visitInsn(POP);
                            mv.visitLabel(done);
                        }
                    };
                }
                return mv;
            }
        }, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    private static byte[] patchPlayer(byte[] bytes) {
        if (methodContainsStingId(bytes, "getDragonfireProtection", "(Z)I")) {
            return bytes;
        }
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = classWriter(cr);
        cr.accept(new ClassVisitor(ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, exceptions);
                if (name.equals("getDragonfireProtection") && desc.equals("(Z)I")) {
                    return new MethodVisitor(ASM9, mv) {
                        private boolean injected;

                        @Override
                        public void visitVarInsn(int opcode, int var) {
                            super.visitVarInsn(opcode, var);
                            if (!injected && opcode == ISTORE && var == 2) {
                                injected = true;
                                emitStingShieldBit(mv);
                            }
                        }
                    };
                }
                return mv;
            }
        }, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }

    private static void emitStingShieldBit(MethodVisitor mv) {
        Label noItem = new Label();
        Label done = new Label();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKEVIRTUAL, "core/game/node/entity/player/Player", "getEquipment",
                "()Lcore/game/container/impl/EquipmentContainer;", false);
        mv.visitInsn(ICONST_5);
        mv.visitMethodInsn(INVOKEVIRTUAL, "core/game/container/impl/EquipmentContainer", "get",
                "(I)Lcore/game/node/item/Item;", false);
        mv.visitInsn(DUP);
        mv.visitJumpInsn(IFNULL, noItem);
        mv.visitMethodInsn(INVOKEVIRTUAL, "core/game/node/item/Item", "getId", "()I", false);
        mv.visitIntInsn(SIPUSH, LEDOSTARS_STING);
        mv.visitJumpInsn(IF_ICMPNE, done);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitInsn(ICONST_4);
        mv.visitInsn(IOR);
        mv.visitVarInsn(ISTORE, 2);
        mv.visitJumpInsn(GOTO, done);
        mv.visitLabel(noItem);
        mv.visitInsn(POP);
        mv.visitLabel(done);
    }

    private static boolean methodContainsStingId(byte[] bytes, String methodName, String desc) {
        final boolean[] found = { false };
        new ClassReader(bytes).accept(new ClassVisitor(ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String sig, String[] exceptions) {
                if (!name.equals(methodName) || !descriptor.equals(desc)) {
                    return null;
                }
                return new MethodVisitor(ASM9) {
                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if (opcode == SIPUSH && operand == LEDOSTARS_STING) {
                            found[0] = true;
                        }
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Integer && ((Integer) value) == LEDOSTARS_STING) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return found[0];
    }

    private static ClassWriter classWriter(ClassReader cr) {
        return new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                return "java/lang/Object";
            }
        };
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
