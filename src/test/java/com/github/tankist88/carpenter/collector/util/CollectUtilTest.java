package com.github.tankist88.carpenter.collector.util;

import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

public class CollectUtilTest {
    @Test
    public void clearAspectMethodTest() {
        String method0 = CollectUtil.clearAspectMethod("init_aroundBody0");
        assertEquals(method0, "init");
        String method1 = CollectUtil.clearAspectMethod("init");
        assertEquals(method1, "init");
    }
}
