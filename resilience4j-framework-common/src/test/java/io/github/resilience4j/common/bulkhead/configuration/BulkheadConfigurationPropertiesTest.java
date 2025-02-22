/*
 * Copyright 2019 Mahmoud Romeh
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
package io.github.resilience4j.common.bulkhead.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.Test;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.core.ConfigurationNotFoundException;

/**
 * unit test for bulkhead properties
 */
public class BulkheadConfigurationPropertiesTest {
	@Test
	public void tesFixedThreadPoolBulkHeadProperties() {
		//Given
		ThreadPoolBulkheadConfigurationProperties.InstanceProperties backendProperties1 = new ThreadPoolBulkheadConfigurationProperties.InstanceProperties();
		ThreadPoolProperties threadPoolProperties = new ThreadPoolProperties();
		threadPoolProperties.setCoreThreadPoolSize(1);
		backendProperties1.setThreadPoolProperties(threadPoolProperties);

		ThreadPoolBulkheadConfigurationProperties.InstanceProperties backendProperties2 = new ThreadPoolBulkheadConfigurationProperties.InstanceProperties();
		ThreadPoolProperties threadPoolProperties2 = new ThreadPoolProperties();
		threadPoolProperties2.setCoreThreadPoolSize(2);
		backendProperties2.setThreadPoolProperties(threadPoolProperties2);

		ThreadPoolBulkheadConfigurationProperties bulkheadConfigurationProperties = new ThreadPoolBulkheadConfigurationProperties();
		bulkheadConfigurationProperties.getBackends().put("backend1", backendProperties1);
		bulkheadConfigurationProperties.getBackends().put("backend2", backendProperties2);

		//Then
		assertThat(bulkheadConfigurationProperties.getBackends().size()).isEqualTo(2);
		assertThat(bulkheadConfigurationProperties.getInstances().size()).isEqualTo(2);
		ThreadPoolBulkheadConfig bulkhead1 = bulkheadConfigurationProperties.createThreadPoolBulkheadConfig("backend1");
		assertThat(bulkhead1).isNotNull();
		assertThat(bulkhead1.getCoreThreadPoolSize()).isEqualTo(1);

		ThreadPoolBulkheadConfig bulkhead2 = bulkheadConfigurationProperties.createThreadPoolBulkheadConfig("backend2");
		assertThat(bulkhead2).isNotNull();
		assertThat(bulkhead2.getCoreThreadPoolSize()).isEqualTo(2);

	}

	@Test
	public void testCreateThreadPoolBulkHeadPropertiesWithSharedConfigs() {
		//Given
		ThreadPoolBulkheadConfigurationProperties.InstanceProperties defaultProperties = new ThreadPoolBulkheadConfigurationProperties.InstanceProperties();
		ThreadPoolProperties threadPoolProperties = new ThreadPoolProperties();
		threadPoolProperties.setCoreThreadPoolSize(1);
		threadPoolProperties.setQueueCapacity(1);
		threadPoolProperties.setKeepAliveTime(5);
		threadPoolProperties.setMaxThreadPoolSize(10);
		defaultProperties.setThreadPoolProperties(threadPoolProperties);

		ThreadPoolBulkheadConfigurationProperties.InstanceProperties sharedProperties = new ThreadPoolBulkheadConfigurationProperties.InstanceProperties();
		ThreadPoolProperties threadPoolProperties2 = new ThreadPoolProperties();
		threadPoolProperties2.setCoreThreadPoolSize(2);
		threadPoolProperties2.setQueueCapacity(2);
		sharedProperties.setThreadPoolProperties(threadPoolProperties2);

		ThreadPoolBulkheadConfigurationProperties.InstanceProperties backendWithDefaultConfig = new ThreadPoolBulkheadConfigurationProperties.InstanceProperties();
		backendWithDefaultConfig.setBaseConfig("default");
		ThreadPoolProperties threadPoolProperties3 = new ThreadPoolProperties();
		threadPoolProperties3.setCoreThreadPoolSize(3);
		backendWithDefaultConfig.setThreadPoolProperties(threadPoolProperties3);

		ThreadPoolBulkheadConfigurationProperties.InstanceProperties backendWithSharedConfig = new ThreadPoolBulkheadConfigurationProperties.InstanceProperties();
		backendWithSharedConfig.setBaseConfig("sharedConfig");
		ThreadPoolProperties threadPoolProperties4 = new ThreadPoolProperties();
		threadPoolProperties4.setCoreThreadPoolSize(4);
		backendWithSharedConfig.setThreadPoolProperties(threadPoolProperties4);

		ThreadPoolBulkheadConfigurationProperties bulkheadConfigurationProperties = new ThreadPoolBulkheadConfigurationProperties();
		bulkheadConfigurationProperties.getConfigs().put("default", defaultProperties);
		bulkheadConfigurationProperties.getConfigs().put("sharedConfig", sharedProperties);

		bulkheadConfigurationProperties.getBackends().put("backendWithDefaultConfig", backendWithDefaultConfig);
		bulkheadConfigurationProperties.getBackends().put("backendWithSharedConfig", backendWithSharedConfig);

		//When
		//Then
		try {
			assertThat(bulkheadConfigurationProperties.getBackends().size()).isEqualTo(2);
			// Should get default config and core number
			ThreadPoolBulkheadConfig bulkhead1 = bulkheadConfigurationProperties.createThreadPoolBulkheadConfig("backendWithDefaultConfig");
			assertThat(bulkhead1).isNotNull();
			assertThat(bulkhead1.getCoreThreadPoolSize()).isEqualTo(3);
			assertThat(bulkhead1.getQueueCapacity()).isEqualTo(1);
			// Should get shared config and overwrite core number
			ThreadPoolBulkheadConfig bulkhead2 = bulkheadConfigurationProperties.createThreadPoolBulkheadConfig("backendWithSharedConfig");
			assertThat(bulkhead2).isNotNull();
			assertThat(bulkhead2.getCoreThreadPoolSize()).isEqualTo(4);
			assertThat(bulkhead2.getQueueCapacity()).isEqualTo(2);
			// Unknown backend should get default config of Registry
			ThreadPoolBulkheadConfig bulkhead3 = bulkheadConfigurationProperties.createThreadPoolBulkheadConfig("unknownBackend");
			assertThat(bulkhead3).isNotNull();
			assertThat(bulkhead3.getCoreThreadPoolSize()).isEqualTo(ThreadPoolBulkheadConfig.DEFAULT_CORE_THREAD_POOL_SIZE);
		} catch (Exception e) {
			System.out.println("exception in testCreateThreadPoolBulkHeadRegistryWithSharedConfigs():" + e);
		}

	}


	@Test
	public void testBulkHeadProperties() {
		//Given
		io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties instanceProperties1 = new io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties();
		instanceProperties1.setMaxConcurrentCalls(3);
		assertThat(instanceProperties1.getEventConsumerBufferSize()).isNull();

		io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties instanceProperties2 = new io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties();
		instanceProperties2.setMaxConcurrentCalls(2);
		assertThat(instanceProperties2.getEventConsumerBufferSize()).isNull();

		BulkheadConfigurationProperties bulkheadConfigurationProperties = new BulkheadConfigurationProperties();
		bulkheadConfigurationProperties.getInstances().put("backend1", instanceProperties1);
		bulkheadConfigurationProperties.getInstances().put("backend2", instanceProperties2);


		//Then
		assertThat(bulkheadConfigurationProperties.getInstances().size()).isEqualTo(2);
		BulkheadConfig bulkhead1 = bulkheadConfigurationProperties.createBulkheadConfig(instanceProperties1);
		assertThat(bulkhead1).isNotNull();
		assertThat(bulkhead1.getMaxConcurrentCalls()).isEqualTo(3);

		BulkheadConfig bulkhead2 = bulkheadConfigurationProperties.createBulkheadConfig(instanceProperties2);
		assertThat(bulkhead2).isNotNull();
		assertThat(bulkhead2.getMaxConcurrentCalls()).isEqualTo(2);


	}

	@Test
	public void testCreateBulkHeadPropertiesWithSharedConfigs() {
		//Given
		io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties defaultProperties = new io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties();
		defaultProperties.setMaxConcurrentCalls(3);
		defaultProperties.setMaxWaitTime(50L);
		assertThat(defaultProperties.getEventConsumerBufferSize()).isNull();

		io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties sharedProperties = new io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties();
		sharedProperties.setMaxConcurrentCalls(2);
		sharedProperties.setMaxWaitDuration(Duration.ofMillis(100L));
		assertThat(sharedProperties.getEventConsumerBufferSize()).isNull();

		io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties backendWithDefaultConfig = new io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties();
		backendWithDefaultConfig.setBaseConfig("default");
		backendWithDefaultConfig.setMaxWaitTime(200L);
		assertThat(backendWithDefaultConfig.getEventConsumerBufferSize()).isNull();

		io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties backendWithSharedConfig = new io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties();
		backendWithSharedConfig.setBaseConfig("sharedConfig");
		backendWithSharedConfig.setMaxWaitTime(300L);
		assertThat(backendWithSharedConfig.getEventConsumerBufferSize()).isNull();

		BulkheadConfigurationProperties bulkheadConfigurationProperties = new BulkheadConfigurationProperties();
		bulkheadConfigurationProperties.getConfigs().put("default", defaultProperties);
		bulkheadConfigurationProperties.getConfigs().put("sharedConfig", sharedProperties);

		bulkheadConfigurationProperties.getInstances().put("backendWithDefaultConfig", backendWithDefaultConfig);
		bulkheadConfigurationProperties.getInstances().put("backendWithSharedConfig", backendWithSharedConfig);


		//Then
		assertThat(bulkheadConfigurationProperties.getInstances().size()).isEqualTo(2);

		// Should get default config and overwrite max calls and wait time
		BulkheadConfig bulkhead1 = bulkheadConfigurationProperties.createBulkheadConfig(backendWithDefaultConfig);
		assertThat(bulkhead1).isNotNull();
		assertThat(bulkhead1.getMaxConcurrentCalls()).isEqualTo(3);
		assertThat(bulkhead1.getMaxWaitTime()).isEqualTo(200L);

		// Should get shared config and overwrite wait time
		BulkheadConfig bulkhead2 = bulkheadConfigurationProperties.createBulkheadConfig(backendWithSharedConfig);
		assertThat(bulkhead2).isNotNull();
		assertThat(bulkhead2.getMaxConcurrentCalls()).isEqualTo(2);
		assertThat(bulkhead2.getMaxWaitTime()).isEqualTo(300L);

		// Unknown backend should get default config of Registry
		BulkheadConfig bulkhead3 = bulkheadConfigurationProperties.createBulkheadConfig(new BulkheadConfigurationProperties.InstanceProperties());
		assertThat(bulkhead3).isNotNull();
		assertThat(bulkhead3.getMaxWaitTime()).isEqualTo(0L);

	}

	@Test
	public void testCreateBulkHeadPropertiesWithUnknownConfig() {
		BulkheadConfigurationProperties bulkheadConfigurationProperties = new BulkheadConfigurationProperties();

		io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties instanceProperties = new io.github.resilience4j.common.bulkhead.configuration.BulkheadConfigurationProperties.InstanceProperties();
		instanceProperties.setBaseConfig("unknownConfig");
		bulkheadConfigurationProperties.getInstances().put("backend", instanceProperties);

		//When
		assertThatThrownBy(() -> bulkheadConfigurationProperties.createBulkheadConfig(instanceProperties))
				.isInstanceOf(ConfigurationNotFoundException.class)
				.hasMessage("Configuration with name 'unknownConfig' does not exist");
	}
}