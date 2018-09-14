package com.github.tankist88.carpenter.collector.util;

import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

public class CollectUtilTest {
    @Test
    public void clearAspectMethodTest() {
        String method0 = CollectUtils.clearAspectMethod("init_aroundBody0");
        assertEquals(method0, "init");
        String method1 = CollectUtils.clearAspectMethod("init");
        assertEquals(method1, "init");
    }

    private class TestA<T> {
    }
    private class TestB extends TestA<Integer> {
    }

    @Test
    public void createClassGenericInfoTest() {
        TestB testB = new TestB();
        assertEquals(CollectUtils.createClassGenericInfo(testB.getClass()), "java.lang.Integer");
    }
}
