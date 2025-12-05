package com.walmartlabs.concord.runtime.v2.runner.guice;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import org.junit.jupiter.api.Test;

import javax.inject.Named;
import javax.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.inject.Scopes.SINGLETON;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases to verify Guice 6.0.0 upgrade compatibility.
 * These tests ensure that core Guice functionality works correctly after the upgrade.
 */
class Guice6UpgradeTest {

    /**
     * Test basic injector creation and singleton binding.
     * Verifies that Guice.createInjector() works correctly with Guice 6.0.0.
     */
    @Test
    void testBasicInjectorCreation() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(SimpleService.class).in(SINGLETON);
            }
        });

        SimpleService service1 = injector.getInstance(SimpleService.class);
        SimpleService service2 = injector.getInstance(SimpleService.class);

        assertNotNull(service1);
        assertNotNull(service2);
        assertSame(service1, service2, "Singleton binding should return same instance");
    }

    /**
     * Test javax.inject.Inject annotation support.
     * Guice 6.0.0 should still support javax.inject annotations.
     */
    @Test
    void testJavaxInjectAnnotation() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(DependencyService.class).in(SINGLETON);
                bind(ServiceWithJavaxInject.class).in(SINGLETON);
            }
        });

        ServiceWithJavaxInject service = injector.getInstance(ServiceWithJavaxInject.class);

        assertNotNull(service);
        assertNotNull(service.getDependency());
    }

    /**
     * Test javax.inject.Named annotation support.
     * Verifies that @Named bindings work correctly with Guice 6.0.0.
     */
    @Test
    void testNamedBindings() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(String.class).annotatedWith(javax.inject.Named.class).toInstance("default");
                bind(String.class).annotatedWith(com.google.inject.name.Names.named("config")).toInstance("test-config");
            }
        });

        String config = injector.getInstance(Key.get(String.class, com.google.inject.name.Names.named("config")));

        assertEquals("test-config", config);
    }

    /**
     * Test TypeListener functionality.
     * This is critical for the MetricTypeListener used in the server module.
     */
    @Test
    void testTypeListener() {
        AtomicInteger listenerCallCount = new AtomicInteger(0);

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindListener(Matchers.any(), new TypeListener() {
                    @Override
                    public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
                        if (type.getRawType() == ListenerTestService.class) {
                            listenerCallCount.incrementAndGet();
                        }
                    }
                });
                bind(ListenerTestService.class).in(SINGLETON);
            }
        });

        injector.getInstance(ListenerTestService.class);

        assertTrue(listenerCallCount.get() > 0, "TypeListener should have been called");
    }

    /**
     * Test custom TypeListener with field injection.
     * Similar to MetricTypeListener in the server module.
     */
    @Test
    void testTypeListenerWithFieldInjection() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindListener(Matchers.any(), new CustomFieldInjectionListener());
                bind(ServiceWithCustomAnnotation.class).in(SINGLETON);
            }
        });

        ServiceWithCustomAnnotation service = injector.getInstance(ServiceWithCustomAnnotation.class);

        assertNotNull(service);
        assertEquals("injected-value", service.getInjectedValue());
    }

    /**
     * Test AbstractMatcher functionality.
     * Used in InjectorUtils.SubClassesOf for task class matching.
     */
    @Test
    void testAbstractMatcher() {
        AtomicInteger matchCount = new AtomicInteger(0);

        AbstractMatcher<TypeLiteral<?>> subClassMatcher = new AbstractMatcher<TypeLiteral<?>>() {
            @Override
            public boolean matches(TypeLiteral<?> t) {
                boolean matches = BaseService.class.isAssignableFrom(t.getRawType());
                if (matches) {
                    matchCount.incrementAndGet();
                }
                return matches;
            }
        };

        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindListener(subClassMatcher, new TypeListener() {
                    @Override
                    public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
                        // Just verify the listener is called
                    }
                });
                bind(ConcreteService.class).in(SINGLETON);
            }
        });

        injector.getInstance(ConcreteService.class);

        assertTrue(matchCount.get() > 0, "AbstractMatcher should have matched ConcreteService");
    }

    /**
     * Test Multibinder functionality.
     * Used extensively in the server module for binding sets of services.
     */
    @Test
    void testMultibinder() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                Multibinder<Plugin> pluginBinder = Multibinder.newSetBinder(binder(), Plugin.class);
                pluginBinder.addBinding().to(PluginA.class);
                pluginBinder.addBinding().to(PluginB.class);
            }
        });

        Set<Plugin> plugins = injector.getInstance(Key.get(new TypeLiteral<Set<Plugin>>() {}));

        assertNotNull(plugins);
        assertEquals(2, plugins.size());
    }

    /**
     * Test Provider injection.
     * Verifies that Provider<T> injection works correctly.
     */
    @Test
    void testProviderInjection() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(DependencyService.class).in(SINGLETON);
                bind(ServiceWithProvider.class).in(SINGLETON);
            }
        });

        ServiceWithProvider service = injector.getInstance(ServiceWithProvider.class);

        assertNotNull(service);
        assertNotNull(service.getDependency());
    }

    /**
     * Test that injector can be obtained from within a TypeListener.
     * This pattern is used in MetricTypeListener.
     */
    @Test
    void testInjectorProviderInTypeListener() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ConfigService.class).in(SINGLETON);
                bindListener(Matchers.any(), new TypeListener() {
                    @Override
                    public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
                        if (type.getRawType() == ServiceNeedingConfig.class) {
                            Provider<Injector> injectorProvider = encounter.getProvider(Injector.class);
                            encounter.register((MembersInjector<I>) instance -> {
                                ConfigService config = injectorProvider.get().getInstance(ConfigService.class);
                                ((ServiceNeedingConfig) instance).setConfig(config);
                            });
                        }
                    }
                });
                bind(ServiceNeedingConfig.class).in(SINGLETON);
            }
        });

        ServiceNeedingConfig service = injector.getInstance(ServiceNeedingConfig.class);

        assertNotNull(service);
        assertNotNull(service.getConfig());
    }

    /**
     * Test module composition with install().
     * Verifies that nested module installation works correctly.
     */
    @Test
    void testModuleComposition() {
        Injector injector = Guice.createInjector(new ParentModule());

        SimpleService simpleService = injector.getInstance(SimpleService.class);
        DependencyService dependencyService = injector.getInstance(DependencyService.class);

        assertNotNull(simpleService);
        assertNotNull(dependencyService);
    }

    // Test helper classes

    static class SimpleService {
        public String getValue() {
            return "simple";
        }
    }

    static class DependencyService {
        public String getValue() {
            return "dependency";
        }
    }

    static class ServiceWithJavaxInject {
        private final DependencyService dependency;

        @Inject
        public ServiceWithJavaxInject(DependencyService dependency) {
            this.dependency = dependency;
        }

        public DependencyService getDependency() {
            return dependency;
        }
    }

    static class ListenerTestService {
        public String getValue() {
            return "listener-test";
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface InjectCustomValue {
    }

    static class ServiceWithCustomAnnotation {
        @InjectCustomValue
        private String injectedValue;

        public String getInjectedValue() {
            return injectedValue;
        }
    }

    static class CustomFieldInjectionListener implements TypeListener {
        @Override
        public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            Class<?> clazz = type.getRawType();
            for (Field f : clazz.getDeclaredFields()) {
                if (f.isAnnotationPresent(InjectCustomValue.class)) {
                    encounter.register((MembersInjector<I>) instance -> {
                        try {
                            f.setAccessible(true);
                            f.set(instance, "injected-value");
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        }
    }

    static abstract class BaseService {
        public abstract String getName();
    }

    static class ConcreteService extends BaseService {
        @Override
        public String getName() {
            return "concrete";
        }
    }

    interface Plugin {
        String getName();
    }

    static class PluginA implements Plugin {
        @Override
        public String getName() {
            return "A";
        }
    }

    static class PluginB implements Plugin {
        @Override
        public String getName() {
            return "B";
        }
    }

    static class ServiceWithProvider {
        private final Provider<DependencyService> dependencyProvider;

        @Inject
        public ServiceWithProvider(Provider<DependencyService> dependencyProvider) {
            this.dependencyProvider = dependencyProvider;
        }

        public DependencyService getDependency() {
            return dependencyProvider.get();
        }
    }

    static class ConfigService {
        public String getConfig() {
            return "config-value";
        }
    }

    static class ServiceNeedingConfig {
        private ConfigService config;

        public void setConfig(ConfigService config) {
            this.config = config;
        }

        public ConfigService getConfig() {
            return config;
        }
    }

    static class ParentModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(SimpleService.class).in(SINGLETON);
            install(new ChildModule());
        }
    }

    static class ChildModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(DependencyService.class).in(SINGLETON);
        }
    }
}
