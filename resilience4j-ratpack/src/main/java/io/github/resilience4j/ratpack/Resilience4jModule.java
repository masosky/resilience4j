/*
 * Copyright 2017 Dan Maas, Jan Sykora
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.resilience4j.ratpack;

import javax.inject.Inject;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.bulkhead.event.BulkheadEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties;
import io.github.resilience4j.common.bulkhead.configuration.ThreadPoolBulkheadConfigurationProperties;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigurationProperties;
import io.github.resilience4j.common.ratelimiter.configuration.RateLimiterConfigurationProperties;
import io.github.resilience4j.common.retry.configuration.RetryConfigurationProperties;
import io.github.resilience4j.consumer.DefaultEventConsumerRegistry;
import io.github.resilience4j.consumer.EventConsumerRegistry;
import io.github.resilience4j.metrics.BulkheadMetrics;
import io.github.resilience4j.metrics.CircuitBreakerMetrics;
import io.github.resilience4j.metrics.RateLimiterMetrics;
import io.github.resilience4j.metrics.RetryMetrics;
import io.github.resilience4j.metrics.ThreadPoolBulkheadMetrics;
import io.github.resilience4j.prometheus.collectors.BulkheadMetricsCollector;
import io.github.resilience4j.prometheus.collectors.CircuitBreakerMetricsCollector;
import io.github.resilience4j.prometheus.collectors.RateLimiterMetricsCollector;
import io.github.resilience4j.prometheus.collectors.ThreadPoolBulkheadMetricsCollector;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.ratelimiter.event.RateLimiterEvent;
import io.github.resilience4j.ratpack.bulkhead.BulkheadMethodInterceptor;
import io.github.resilience4j.ratpack.bulkhead.monitoring.endpoint.BulkheadChain;
import io.github.resilience4j.ratpack.circuitbreaker.CircuitBreakerMethodInterceptor;
import io.github.resilience4j.ratpack.circuitbreaker.monitoring.endpoint.CircuitBreakerChain;
import io.github.resilience4j.ratpack.ratelimiter.RateLimiterMethodInterceptor;
import io.github.resilience4j.ratpack.ratelimiter.monitoring.endpoint.RateLimiterChain;
import io.github.resilience4j.ratpack.retry.RetryMethodInterceptor;
import io.github.resilience4j.ratpack.retry.monitoring.endpoint.RetryChain;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.retry.event.RetryEvent;
import io.prometheus.client.CollectorRegistry;
import ratpack.dropwizard.metrics.DropwizardMetricsModule;
import ratpack.guice.ConfigurableModule;
import ratpack.handling.HandlerDecorator;
import ratpack.handling.Handlers;
import ratpack.service.Service;
import ratpack.service.StartEvent;

/**
 * This module registers class and method interceptors for bulkheads, circuit breakers, rate limiters, and retries.
 * <p>
 * This module also registers metrics:
 * - bulkhead, circuitbreaker, ratelimiter, and retry metrics with dropwizard metrics, if enabled.
 * - circuitbreaker, ratelimiter, and retry metrics with prometheus, if enabled.
 * <p>
 * Only enable metrics if you have dependencies for resilience4j-metrics in the classpath and an instance of
 * {@link MetricRegistry} is bound (usually this will happen when installing {@link DropwizardMetricsModule}).
 * This must be done manually, since guice doesn't know if dropwizard is on the runtime classpath.
 * <p>
 * Only enable prometheus if you have a dependency on resilience4j-prometheus in the classpath and an instance of
 * {@link CollectorRegistry} is bound. This must be done manually, since guice doesn't know if prometheus is on the runtime classpath.
 * <p>
 * Also note that for this to work, CircuitBreaker, RateLimiter, and Retry instances must be created
 * before the respective registries are bound.
 */
public class Resilience4jModule extends ConfigurableModule<Resilience4jConfig> {

    @Override
    protected void configure() {
        // interceptors
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(CircuitBreaker.class), injected(new CircuitBreakerMethodInterceptor()));
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(RateLimiter.class), injected(new RateLimiterMethodInterceptor()));
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(Retry.class), injected(new RetryMethodInterceptor()));
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(Bulkhead.class), injected(new BulkheadMethodInterceptor()));
        bindInterceptor(Matchers.annotatedWith(CircuitBreaker.class), Matchers.any(), injected(new CircuitBreakerMethodInterceptor()));
        bindInterceptor(Matchers.annotatedWith(RateLimiter.class), Matchers.any(), injected(new RateLimiterMethodInterceptor()));
        bindInterceptor(Matchers.annotatedWith(Retry.class), Matchers.any(), injected(new RetryMethodInterceptor()));
        bindInterceptor(Matchers.annotatedWith(Bulkhead.class), Matchers.any(), injected(new BulkheadMethodInterceptor()));

        // default registries
        OptionalBinder.newOptionalBinder(binder(), CircuitBreakerRegistry.class).setDefault().toInstance(CircuitBreakerRegistry.ofDefaults());
        OptionalBinder.newOptionalBinder(binder(), RateLimiterRegistry.class).setDefault().toInstance(RateLimiterRegistry.ofDefaults());
        OptionalBinder.newOptionalBinder(binder(), RetryRegistry.class).setDefault().toInstance(RetryRegistry.ofDefaults());
        OptionalBinder.newOptionalBinder(binder(), BulkheadRegistry.class).setDefault().toInstance(BulkheadRegistry.ofDefaults());
        OptionalBinder.newOptionalBinder(binder(), ThreadPoolBulkheadRegistry.class).setDefault().toInstance(ThreadPoolBulkheadRegistry.ofDefaults());

        // event consumers
        bind(new TypeLiteral<EventConsumerRegistry<CircuitBreakerEvent>>() {}).toInstance(new DefaultEventConsumerRegistry<>());
        bind(new TypeLiteral<EventConsumerRegistry<RateLimiterEvent>>() {}).toInstance(new DefaultEventConsumerRegistry<>());
        bind(new TypeLiteral<EventConsumerRegistry<RetryEvent>>() {}).toInstance(new DefaultEventConsumerRegistry<>());
        bind(new TypeLiteral<EventConsumerRegistry<BulkheadEvent>>() {}).toInstance(new DefaultEventConsumerRegistry<>());

        // event chains
        Multibinder<HandlerDecorator> binder = Multibinder.newSetBinder(binder(), HandlerDecorator.class);
        bind(CircuitBreakerChain.class).in(Scopes.SINGLETON);
        bind(RateLimiterChain.class).in(Scopes.SINGLETON);
        bind(RetryChain.class).in(Scopes.SINGLETON);
        bind(BulkheadChain.class).in(Scopes.SINGLETON);
        binder.addBinding().toProvider(() -> (registry, rest) -> {
            if (registry.get(Resilience4jConfig.class).getEndpoints().getCircuitBreakers().isEnabled()) {
                return Handlers.chain(Handlers.chain(registry, registry.get(CircuitBreakerChain.class)), rest);
            } else {
                return rest;
            }
        });
        binder.addBinding().toProvider(() -> (registry, rest) -> {
            if (registry.get(Resilience4jConfig.class).getEndpoints().getRateLimiters().isEnabled()) {
                return Handlers.chain(Handlers.chain(registry, registry.get(RateLimiterChain.class)), rest);
            } else {
                return rest;
            }
        });
        binder.addBinding().toProvider(() -> (registry, rest) -> {
            if (registry.get(Resilience4jConfig.class).getEndpoints().getRetries().isEnabled()) {
                return Handlers.chain(Handlers.chain(registry, registry.get(RetryChain.class)), rest);
            } else {
                return rest;
            }
        });
        binder.addBinding().toProvider(() -> (registry, rest) -> {
            if (registry.get(Resilience4jConfig.class).getEndpoints().getBulkheads().isEnabled()) {
                return Handlers.chain(Handlers.chain(registry, registry.get(BulkheadChain.class)), rest);
            } else {
                return rest;
            }
        });

        // startup
        bind(Resilience4jService.class).in(Scopes.SINGLETON);
    }

    private <T> T injected(T instance) {
        requestInjection(instance);
        return instance;
    }

    private static class Resilience4jService implements Service {
        private final Injector injector;
        private final Resilience4jConfig config;

        @Inject
        public Resilience4jService(Injector injector, Resilience4jConfig config) {
            this.injector = injector;
            this.config = config;
        }

        @Override
        public void onStart(StartEvent event) {

            EndpointsConfig endpointsConfig = event.getRegistry().get(Resilience4jConfig.class).getEndpoints();

            // build circuit breakers
            CircuitBreakerRegistry circuitBreakerRegistry = injector.getInstance(CircuitBreakerRegistry.class);
            EventConsumerRegistry<CircuitBreakerEvent> cbConsumerRegistry = injector.getInstance(Key.get(new TypeLiteral<EventConsumerRegistry<CircuitBreakerEvent>>() {
            }));
            CircuitBreakerConfigurationProperties circuitBreakerConfigurations = config.getCircuitBreaker();
            circuitBreakerConfigurations.getInstances().forEach((name, circuitBreakerConfig) -> {
                io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker =
                        circuitBreakerRegistry.circuitBreaker(name, circuitBreakerConfigurations.createCircuitBreakerConfig(circuitBreakerConfig));
                if (endpointsConfig.getCircuitBreakers().isEnabled()) {
	                circuitBreaker.getEventPublisher().onEvent(cbConsumerRegistry.createEventConsumer(name, circuitBreakerConfig.getEventConsumerBufferSize() != null ? circuitBreakerConfig.getEventConsumerBufferSize() : 100));
                }
            });

            // build rate limiters
            RateLimiterRegistry rateLimiterRegistry = injector.getInstance(RateLimiterRegistry.class);
            EventConsumerRegistry<RateLimiterEvent> rlConsumerRegistry = injector.getInstance(Key.get(new TypeLiteral<EventConsumerRegistry<RateLimiterEvent>>() {
            }));
            RateLimiterConfigurationProperties rateLimiterConfigurations = config.getRateLimiter();
            rateLimiterConfigurations.getInstances().forEach((name, rateLimiterConfig) -> {
                io.github.resilience4j.ratelimiter.RateLimiter rateLimiter =
                        rateLimiterRegistry.rateLimiter(name, rateLimiterConfigurations.createRateLimiterConfig(rateLimiterConfig));
                if (endpointsConfig.getRateLimiters().isEnabled()) {
	                rateLimiter.getEventPublisher().onEvent(rlConsumerRegistry.createEventConsumer(name, rateLimiterConfig.getEventConsumerBufferSize() != null ? rateLimiterConfig.getEventConsumerBufferSize() : 100));
                }
            });

            // build retries
            RetryRegistry retryRegistry = injector.getInstance(RetryRegistry.class);
            EventConsumerRegistry<RetryEvent> rConsumerRegistry = injector.getInstance(Key.get(new TypeLiteral<EventConsumerRegistry<RetryEvent>>() {
            }));
            RetryConfigurationProperties retryConfigurations = config.getRetry();
            retryConfigurations.getInstances().forEach((name, retryConfig) -> {
                io.github.resilience4j.retry.Retry retry =
                        retryRegistry.retry(name, retryConfigurations.createRetryConfig(retryConfig));
                if (endpointsConfig.getRetries().isEnabled()) {
	                retry.getEventPublisher().onEvent(rConsumerRegistry.createEventConsumer(name, retryConfig.getEventConsumerBufferSize() != null ? retryConfig.getEventConsumerBufferSize() : 100));
                }
            });

            // build bulkheads
            BulkheadRegistry bulkheadRegistry = injector.getInstance(BulkheadRegistry.class);
            EventConsumerRegistry<BulkheadEvent> bConsumerRegistry = injector.getInstance(Key.get(new TypeLiteral<EventConsumerRegistry<BulkheadEvent>>() {
            }));
            BulkheadConfigurationProperties bulkheadConfigurations= config.getBulkhead();
            bulkheadConfigurations.getInstances().forEach((name, bulkheadConfig) -> {
                io.github.resilience4j.bulkhead.Bulkhead bulkhead =
                        bulkheadRegistry.bulkhead(name, bulkheadConfigurations.createBulkheadConfig(bulkheadConfig));
                if (endpointsConfig.getBulkheads().isEnabled()) {
	                bulkhead.getEventPublisher().onEvent(bConsumerRegistry.createEventConsumer(name, bulkheadConfig.getEventConsumerBufferSize() != null ? bulkheadConfig.getEventConsumerBufferSize() : 100));
                }
            });

            // build thread pool bulkheads
            ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry = injector.getInstance(ThreadPoolBulkheadRegistry.class);
            ThreadPoolBulkheadConfigurationProperties threadPoolBulkheadConfigurations = config.getThreadPoolBulkhead();
            threadPoolBulkheadConfigurations.getInstances().forEach((name, bulkheadConfig) -> {
                io.github.resilience4j.bulkhead.ThreadPoolBulkhead bulkhead =
                        threadPoolBulkheadRegistry.bulkhead(name, threadPoolBulkheadConfigurations.createThreadPoolBulkheadConfig(bulkheadConfig));
                if (endpointsConfig.getThreadPoolBulkheads().isEnabled()) {
	                bulkhead.getEventPublisher().onEvent(bConsumerRegistry.createEventConsumer(name, bulkheadConfig.getEventConsumerBufferSize() != null ? bulkheadConfig.getEventConsumerBufferSize() : 100));
                }
            });

            // dropwizard metrics
            if (config.isMetrics() && injector.getExistingBinding(Key.get(MetricRegistry.class)) != null) {
                MetricRegistry metricRegistry = injector.getInstance(MetricRegistry.class);
                CircuitBreakerMetrics circuitBreakerMetrics = CircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);
                RateLimiterMetrics rateLimiterMetrics = RateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry);
                RetryMetrics retryMetrics = RetryMetrics.ofRetryRegistry(retryRegistry);
                BulkheadMetrics bulkheadMetrics = BulkheadMetrics.ofBulkheadRegistry(bulkheadRegistry);
                ThreadPoolBulkheadMetrics threadPoolBulkheadMetrics = ThreadPoolBulkheadMetrics.ofBulkheadRegistry(threadPoolBulkheadRegistry);
                metricRegistry.registerAll(circuitBreakerMetrics);
                metricRegistry.registerAll(rateLimiterMetrics);
                metricRegistry.registerAll(retryMetrics);
                metricRegistry.registerAll(bulkheadMetrics);
                metricRegistry.registerAll(threadPoolBulkheadMetrics);
            }

            // prometheus
            if (config.isPrometheus() && injector.getExistingBinding(Key.get(CollectorRegistry.class)) != null) {
                CollectorRegistry collectorRegistry = injector.getInstance(CollectorRegistry.class);
                CircuitBreakerMetricsCollector circuitBreakerMetricsCollector = CircuitBreakerMetricsCollector.ofCircuitBreakerRegistry(circuitBreakerRegistry);
                RateLimiterMetricsCollector rateLimiterMetricsCollector = RateLimiterMetricsCollector.ofRateLimiterRegistry(rateLimiterRegistry);
                BulkheadMetricsCollector bulkheadMetricsCollector = BulkheadMetricsCollector.ofBulkheadRegistry(bulkheadRegistry);
                ThreadPoolBulkheadMetricsCollector threadPoolBulkheadMetricsCollector = ThreadPoolBulkheadMetricsCollector.ofBulkheadRegistry(threadPoolBulkheadRegistry);
                circuitBreakerMetricsCollector.register(collectorRegistry);
                rateLimiterMetricsCollector.register(collectorRegistry);
                bulkheadMetricsCollector.register(collectorRegistry);
                threadPoolBulkheadMetricsCollector.register(collectorRegistry);
            }

        }

    }

}
