package com.github.tankist88.carpenter.collector.util;

import org.testng.annotations.Test;

import java.util.List;

import static com.github.tankist88.object2source.util.GenerationUtil.getClassHierarchy;
import static org.testng.Assert.*;

public class CollectUtilTest {
    @Test
    public void clearAspectMethodTest() {
        String method0 = CollectUtils.clearAspectMethod("init_aroundBody0");
        assertEquals(method0, "init");
        String method1 = CollectUtils.clearAspectMethod("init");
        assertEquals(method1, "init");
    }

    private abstract class AbstractPlan {
        private String name;
        private List<String> services;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getServices() {
            return services;
        }

        public void setServices(List<String> services) {
            this.services = services;
        }
    }

    private class Client {
        private AbstractPlan plan;

        public AbstractPlan getPlan() {
            return plan;
        }

        public void setPlan(AbstractPlan plan) {
            this.plan = plan;
        }
    }

    private class ClientService {
        public Client getClient(){
            return null;
        }
    }

    @Test
    public void isMaybeServiceClassTest() {
        assertTrue(CollectUtils.isMaybeServiceClass(getClassHierarchy(ClientService.class)));
        assertFalse(CollectUtils.isMaybeServiceClass(getClassHierarchy(Client.class)));
    }
}
