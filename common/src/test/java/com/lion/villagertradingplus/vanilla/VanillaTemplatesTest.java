package com.lion.villagertradingplus.vanilla;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class VanillaTemplatesTest {

    private static final String PROOF = "com.lion.villagertradingplus.vanilla.VanillaProof";

    @TestFactory
    List<DynamicTest> vanillaTradesAreTheTemplates() throws Exception {
        Path folder = Path.of(System.getProperty("vtp.rootDir"))
                .resolve("common/src/main/resources/data/villagertradingplus/vanilla_trades");
        System.setProperty("vtp.testConfigDir", Files.createTempDirectory("vtp-config").toString());

        Map<String, String> generated;
        List<String[]> results;

        try (WideningLoader loader = WideningLoader.forCurrentClasspath()) {
            Class<?> proof = loader.loadClass(PROOF);
            Map<String, String> raw = cast(call(proof.getMethod("templates")));
            generated = new TreeMap<>(raw);

            if (Boolean.getBoolean("vtp.writeTemplates")) {
                write(folder, generated);
            }

            results = cast(call(proof.getMethod("run", String.class), folder.toString()));
        }

        List<DynamicTest> tests = new ArrayList<>();
        tests.add(dynamicTest("the files in the jar are the ones made from the game", () -> {
            List<String> names = new ArrayList<>();

            try (var files = Files.list(folder)) {
                files.forEach(file -> names.add(file.getFileName().toString()));
            }

            names.sort(null);
            assertEquals(new ArrayList<>(generated.keySet()), names);

            for (Map.Entry<String, String> entry : generated.entrySet()) {
                String shipped = Files.readString(folder.resolve(entry.getKey()), StandardCharsets.UTF_8).replace("\r\n", "\n");
                assertEquals(entry.getValue(), shipped, entry.getKey() + " differs from the game, run with -PwriteTemplates");
            }
        }));

        for (String[] result : results) {
            tests.add(dynamicTest(result[0], () -> assertTrue(result[1].isEmpty(), result[1])));
        }

        return tests;
    }

    private static void write(Path folder, Map<String, String> files) throws Exception {
        Files.createDirectories(folder);

        try (var old = Files.list(folder)) {
            for (Path file : old.toList()) {
                Files.delete(file);
            }
        }

        for (Map.Entry<String, String> entry : files.entrySet()) {
            Files.writeString(folder.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static Object call(Method method, Object... args) throws Exception {
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }

            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
