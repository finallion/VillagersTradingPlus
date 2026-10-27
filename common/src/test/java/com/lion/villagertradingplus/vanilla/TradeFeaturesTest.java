package com.lion.villagertradingplus.vanilla;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class TradeFeaturesTest {

    @TestFactory
    List<DynamicTest> features() throws Exception {
        System.setProperty("vtp.testConfigDir", Files.createTempDirectory("vtp-config").toString());
        List<String[]> results;

        try (WideningLoader loader = WideningLoader.forCurrentClasspath()) {
            Class<?> checks = loader.loadClass("com.lion.villagertradingplus.vanilla.FeatureChecks");

            try {
                results = cast(checks.getMethod("run").invoke(null));
            } catch (InvocationTargetException e) {
                throw e.getCause() instanceof Exception cause ? cause : e;
            }
        }

        List<DynamicTest> tests = new ArrayList<>();

        for (String[] result : results) {
            tests.add(dynamicTest(result[0], () -> assertTrue(result[1].isEmpty(), result[1])));
        }

        return tests;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
