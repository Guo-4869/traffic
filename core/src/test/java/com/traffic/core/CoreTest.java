package com.traffic.core;

import org.junit.Test;

public class CoreTest {
    @Test public void scenarios() throws Exception {
        Class.forName("CoreCheck").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
    }
}
