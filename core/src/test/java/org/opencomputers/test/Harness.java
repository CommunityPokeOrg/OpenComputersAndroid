package org.opencomputers.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Zero-dependency test runner. Every public void no-arg method whose name
 * starts with "test" on a registered class is a test case. Fails the process
 * (exit 1) if any test fails — usable from Gradle JavaExec or plain java.
 */
public final class Harness {
    private static final List<Class<?>> SUITE = List.of(
            TextBufferTest.class,
            FileSystemTest.class,
            ComponentBusTest.class,
            DataComponentTest.class,
            RedstoneComponentTest.class,
            MachineIntegrationTest.class
    );

    public static void main(String[] args) {
        int passed = 0;
        List<String> failures = new ArrayList<>();
        for (Class<?> cls : SUITE) {
            Method[] methods = cls.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method m : methods) {
                if (!m.getName().startsWith("test")
                        || !Modifier.isPublic(m.getModifiers())
                        || m.getParameterCount() != 0
                        || m.getReturnType() != void.class) {
                    continue;
                }
                String id = cls.getSimpleName() + "." + m.getName();
                try {
                    Object instance = cls.getDeclaredConstructor().newInstance();
                    m.invoke(instance);
                    passed++;
                    System.out.println("PASS " + id);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    failures.add(id + " -> " + cause);
                    System.out.println("FAIL " + id + " -> " + cause);
                } catch (ReflectiveOperationException e) {
                    failures.add(id + " -> " + e);
                    System.out.println("FAIL " + id + " -> " + e);
                }
            }
        }
        System.out.println();
        System.out.println("passed=" + passed + " failed=" + failures.size());
        for (String f : failures) {
            System.out.println("  " + f);
        }
        // Exit explicitly: LuaJ leaves a non-daemon thread alive after machine
        // tests, which would otherwise keep the JVM (and CI) hanging.
        System.exit(failures.isEmpty() ? 0 : 1);
    }
}
