package com.lion.villagertradingplus.vanilla;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/// Loads all but the JDK classes itself and opens the package-private members of Minecraft that a plain JVM keeps shut.
public final class WideningLoader extends URLClassLoader {

    private static final String CONFIG_DIRECTORY = "com.lion.villagertradingplus.platform.ConfigDirectory";

    private WideningLoader(URL[] urls) {
        super(urls, ClassLoader.getPlatformClassLoader());
    }

    public static WideningLoader forCurrentClasspath() throws IOException {
        List<URL> urls = new ArrayList<>();

        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!entry.isEmpty()) {
                urls.add(new File(entry).toURI().toURL());
            }
        }

        return new WideningLoader(urls.toArray(new URL[0]));
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);

            if (loaded == null) {
                if (isJdk(name)) {
                    loaded = super.loadClass(name, false);
                } else {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException e) {
                        loaded = super.loadClass(name, false);
                    }
                }
            }

            if (resolve) {
                resolveClass(loaded);
            }

            return loaded;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes;

        if (name.equals(CONFIG_DIRECTORY)) {
            bytes = configDirectory();
        } else {
            try (InputStream in = getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }

                bytes = in.readAllBytes();
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }

            if (name.startsWith("net.minecraft.")) {
                bytes = widen(bytes);
            }
        }

        return defineClass(name, bytes, 0, bytes.length);
    }

    private static boolean isJdk(String name) {
        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jdk.")
                || name.startsWith("sun.")
                || name.startsWith("com.sun.")
                || name.startsWith("org.w3c.")
                || name.startsWith("org.xml.");
    }

    private static byte[] widen(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, open(access), name, signature, superName, interfaces);
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                super.visitInnerClass(name, outerName, innerName, open(access));
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                return super.visitField(open(access), name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return super.visitMethod(open(access), name, descriptor, signature, exceptions);
            }
        }, 0);

        return writer.toByteArray();
    }

    private static int open(int access) {
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            return access;
        }

        return (access & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
    }

    /// The platform method is replaced by Architectury at build time. The test JVM gets a plain directory instead.
    private static byte[] configDirectory() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, CONFIG_DIRECTORY.replace('.', '/'), null, "java/lang/Object", null);

        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "getConfigDirectory",
                "()Ljava/nio/file/Path;",
                null,
                null
        );
        method.visitCode();
        method.visitLdcInsn("vtp.testConfigDir");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "java/nio/file/Paths",
                "get",
                "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;",
                false
        );
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
