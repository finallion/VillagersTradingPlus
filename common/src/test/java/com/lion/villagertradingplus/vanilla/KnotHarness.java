package com.lion.villagertradingplus.vanilla;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

/// Runs static test logic inside Fabric Loader's class loader, which applies the access wideners vanilla needs.
public final class KnotHarness {

    private static ClassLoader loader;

    private KnotHarness() {}

    private static synchronized ClassLoader loader() {
        if (loader == null) {
            System.setProperty("fabric.development", "true");
            System.setProperty("fabric.unitTest", "true");

            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();

            try {
                loader = new Knot(EnvType.CLIENT).init(new String[0]);
            } finally {
                thread.setContextClassLoader(previous);
            }
        }

        return loader;
    }

    public static void run(Class<?> logic, String method) throws Throwable {
        ClassLoader knot = loader();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(knot);

        try {
            Method target = Class.forName(logic.getName(), true, knot).getMethod(method);
            target.invoke(null);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
