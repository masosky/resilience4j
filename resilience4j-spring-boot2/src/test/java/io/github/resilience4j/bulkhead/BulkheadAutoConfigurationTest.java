/*
 * Copyright 2019 lespinsideg, Mahmoud Romeh
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
package io.github.resilience4j.bulkhead;

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.Ordered;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import io.github.resilience4j.bulkhead.autoconfigure.BulkheadProperties;
import io.github.resilience4j.bulkhead.autoconfigure.ThreadPoolBulkheadProperties;
import io.github.resilience4j.bulkhead.configure.BulkheadAspect;
import io.github.resilience4j.bulkhead.event.BulkheadEvent;
import io.github.resilience4j.common.bulkhead.monitoring.endpoint.BulkheadEndpointResponse;
import io.github.resilience4j.common.bulkhead.monitoring.endpoint.BulkheadEventDTO;
import io.github.resilience4j.common.bulkhead.monitoring.endpoint.BulkheadEventsEndpointResponse;
import io.github.resilience4j.service.test.TestApplication;
import io.github.resilience4j.service.test.bulkhead.BulkheadDummyService;
import io.github.resilience4j.service.test.bulkhead.BulkheadReactiveDummyService;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = TestApplication.class)
public class BulkheadAutoConfigurationTest {

	@Autowired
	private BulkheadRegistry bulkheadRegistry;

	@Autowired
	private ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry;

	@Autowired
	private BulkheadProperties bulkheadProperties;

	@Autowired
	private ThreadPoolBulkheadProperties threadPoolBulkheadProperties;

	@Autowired
	private BulkheadAspect bulkheadAspect;

	@Autowired
	private BulkheadDummyService dummyService;

	@Autowired
	private BulkheadReactiveDummyService reactiveDummyService;


	@Autowired
	private TestRestTemplate restTemplate;


	/**
	 * The test verifies that a Bulkhead instance is created and configured properly when the BulkheadDummyService is invoked and
	 * that the Bulkhead records permitted and rejected calls.
	 */
	@Test
	@DirtiesContext
	public void testBulkheadAutoConfigurationThreadPool() {
		ExecutorService es = Executors.newFixedThreadPool(5);

		assertThat(threadPoolBulkheadRegistry).isNotNull();
		assertThat(threadPoolBulkheadProperties).isNotNull();

		ThreadPoolBulkhead bulkhead = threadPoolBulkheadRegistry.bulkhead(BulkheadDummyService.BACKEND_C);
		assertThat(bulkhead).isNotNull();

		for (int i = 0; i < 4; i++) {
			es.submit(dummyService::doSomethingAsync);
		}

		await()
				.atMost(1, TimeUnit.SECONDS)
				.until(() -> bulkhead.getMetrics().getRemainingQueueCapacity() == 0);


		await()
				.atMost(1, TimeUnit.SECONDS)
				.until(() -> bulkhead.getMetrics().getQueueCapacity() == 1);
		// Test Actuator endpoints

		ResponseEntity<BulkheadEndpointResponse> bulkheadList = restTemplate.getForEntity("/actuator/bulkheads", BulkheadEndpointResponse.class);
		assertThat(bulkheadList.getBody().getBulkheads()).hasSize(4).containsExactly("backendA", "backendB", "backendB", "backendC");

		for (int i = 0; i < 5; i++) {
			es.submit(dummyService::doSomethingAsync);
		}

		ResponseEntity<BulkheadEventsEndpointResponse> bulkheadEventList = restTemplate.getForEntity("/actuator/bulkheadevents/backendC", BulkheadEventsEndpointResponse.class);
		List<BulkheadEventDTO> bulkheadEventsByBackend = bulkheadEventList.getBody().getBulkheadEvents();

		assertThat(bulkheadEventsByBackend.get(bulkheadEventsByBackend.size() - 1).getType()).isEqualTo(BulkheadEvent.Type.CALL_REJECTED);
		assertThat(bulkheadEventsByBackend).filteredOn(it -> it.getType() == BulkheadEvent.Type.CALL_REJECTED)
				.isNotEmpty();
		assertThat(bulkheadEventsByBackend.stream().filter(it -> it.getType() == BulkheadEvent.Type.CALL_PERMITTED).count() == 2);
		assertThat(bulkheadEventsByBackend.stream().filter(it -> it.getType() == BulkheadEvent.Type.CALL_FINISHED).count() == 1);

		assertThat(bulkheadAspect.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);

		es.shutdown();
	}


	/**
	 * The test verifies that a Bulkhead instance is created and configured properly when the BulkheadDummyService is invoked and
	 * that the Bulkhead records permitted and rejected calls.
	 */
	@Test
	@DirtiesContext
	public void testBulkheadAutoConfiguration() {
		ExecutorService es = Executors.newFixedThreadPool(5);

		assertThat(bulkheadRegistry).isNotNull();
		assertThat(bulkheadProperties).isNotNull();

		Bulkhead bulkhead = bulkheadRegistry.bulkhead(BulkheadDummyService.BACKEND);
		assertThat(bulkhead).isNotNull();

		for (int i = 0; i < 4; i++) {
			es.submit(dummyService::doSomething);
		}

		await()
				.atMost(1, TimeUnit.SECONDS)
				.until(() -> bulkhead.getMetrics().getAvailableConcurrentCalls() == 0);

		assertThat(bulkhead.getBulkheadConfig().getMaxWaitTime()).isEqualTo(0);
		assertThat(bulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(1);

		await()
				.atMost(1, TimeUnit.SECONDS)
				.until(() -> bulkhead.getMetrics().getAvailableConcurrentCalls() == 1);
		// Test Actuator endpoints

		ResponseEntity<BulkheadEndpointResponse> bulkheadList = restTemplate.getForEntity("/actuator/bulkheads", BulkheadEndpointResponse.class);
		assertThat(bulkheadList.getBody().getBulkheads()).hasSize(4).containsExactly("backendA", "backendB", "backendB", "backendC");

		for (int i = 0; i < 5; i++) {
			es.submit(dummyService::doSomething);
		}

		await()
				.atMost(1, TimeUnit.SECONDS)
				.until(() -> bulkhead.getMetrics().getAvailableConcurrentCalls() == 1);

		ResponseEntity<BulkheadEventsEndpointResponse> bulkheadEventList = restTemplate.getForEntity("/actuator/bulkheadevents", BulkheadEventsEndpointResponse.class);
		List<BulkheadEventDTO> bulkheadEvents = bulkheadEventList.getBody().getBulkheadEvents();

		assertThat(bulkheadEvents).isNotEmpty();
		assertThat(bulkheadEvents.get(bulkheadEvents.size() - 1).getType()).isEqualTo(BulkheadEvent.Type.CALL_FINISHED);
		assertThat(bulkheadEvents.get(bulkheadEvents.size() - 2).getType()).isEqualTo(BulkheadEvent.Type.CALL_REJECTED);

		bulkheadEventList = restTemplate.getForEntity("/actuator/bulkheadevents/backendA", BulkheadEventsEndpointResponse.class);
		List<BulkheadEventDTO> bulkheadEventsByBackend = bulkheadEventList.getBody().getBulkheadEvents();

		assertThat(bulkheadEventsByBackend).hasSameSizeAs(bulkheadEvents);
		assertThat(bulkheadEventsByBackend.get(bulkheadEvents.size() - 1).getType()).isEqualTo(BulkheadEvent.Type.CALL_FINISHED);
		assertThat(bulkheadEventsByBackend).filteredOn(it -> it.getType() == BulkheadEvent.Type.CALL_REJECTED)
				.isNotEmpty();

		assertThat(bulkheadAspect.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);

		es.shutdown();
	}

	/**
	 * The test verifies that a Bulkhead instance is created and configured properly when the BulkheadReactiveDummyService is invoked and
	 * that the Bulkhead records permitted and rejected calls.
	 */
	@Test
	@DirtiesContext
	public void testBulkheadAutoConfigurationRxJava2() {
		ExecutorService es = Executors.newFixedThreadPool(5);
		assertThat(bulkheadRegistry).isNotNull();
		assertThat(bulkheadProperties).isNotNull();

		Bulkhead bulkhead = bulkheadRegistry.bulkhead(BulkheadReactiveDummyService.BACKEND);
		assertThat(bulkhead).isNotNull();

		for (int i = 0; i < 5; i++) {
			es.submit(new Thread(() -> reactiveDummyService.doSomethingFlowable()
					.subscribe(String::toUpperCase, throwable -> System.out.println("Bulkhead Exception received: " + throwable.getMessage()))));
		}
		await()
				.atMost(1200, TimeUnit.MILLISECONDS)
				.until(() -> bulkhead.getMetrics().getAvailableConcurrentCalls() == 0);

		await()
				.atMost(1000, TimeUnit.MILLISECONDS)
				.until(() -> bulkhead.getMetrics().getAvailableConcurrentCalls() == 2);

		for (int i = 0; i < 5; i++) {
			es.submit(new Thread(() -> reactiveDummyService.doSomethingFlowable()
					.subscribe(String::toUpperCase, throwable -> System.out.println("Bulkhead Exception received: " + throwable.getMessage()))));
		}

		await()
				.atMost(1000, TimeUnit.MILLISECONDS)
				.until(() -> bulkhead.getMetrics().getAvailableConcurrentCalls() == 2);

		assertThat(bulkhead.getBulkheadConfig().getMaxWaitTime()).isEqualTo(10);
		assertThat(bulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(2);
		commonAssertions();

		es.shutdown();
	}


	/**
	 * The test verifies that a Bulkhead instance is created and configured properly when the BulkheadReactiveDummyService is invoked and
	 * that the Bulkhead records permitted and rejected calls.
	 */
	@Test
	@DirtiesContext
	public void testBulkheadAutoConfigurationReactor() {
		ExecutorService es = Executors.newFixedThreadPool(5);
		assertThat(bulkheadRegistry).isNotNull();
		assertThat(bulkheadProperties).isNotNull();

		Bulkhead bulkhead = bulkheadRegistry.bulkhead(BulkheadReactiveDummyService.BACKEND);
		assertThat(bulkhead).isNotNull();

		for (int i = 0; i < 5; i++) {
			es.submit(new Thread(() -> reactiveDummyService.doSomethingFlux()
					.subscribe(String::toUpperCase, throwable -> System.out.println("Bulkhead Exception received: " + throwable.getMessage()))));
		}
		await()
				.atMost(1200, TimeUnit.MILLISECONDS)
				.until(() -> bulkhead.getMetrics().getAvailableConcurrentCalls() == 0);

		await()
				.atMost(1000, TimeUnit.MILLISECONDS)
				.until(() -> bulkhead.getMetrics().getAvailableConcurrentCalls() == 2);

		for (int i = 0; i < 5; i++) {
			es.submit(new Thread(() -> reactiveDummyService.doSomethingFlux()
					.subscribe(String::toUpperCase, throwable -> System.out.println("Bulkhead Exception received: " + throwable.getMessage()))));
		}

		await()
				.atMost(1000, TimeUnit.MILLISECONDS)
				.until(() -> bulkhead.getMetrics().getAvailableConcurrentCalls() == 2);

		commonAssertions();
		assertThat(bulkhead.getBulkheadConfig().getMaxWaitTime()).isEqualTo(10);
		assertThat(bulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(2);

		es.shutdown();
	}

	private void commonAssertions() {
		// Test Actuator endpoints

		ResponseEntity<BulkheadEndpointResponse> bulkheadList = restTemplate.getForEntity("/actuator/bulkheads", BulkheadEndpointResponse.class);
		assertThat(bulkheadList.getBody().getBulkheads()).hasSize(4).containsExactly("backendA", "backendB", "backendB", "backendC");

		ResponseEntity<BulkheadEventsEndpointResponse> bulkheadEventList = restTemplate.getForEntity("/actuator/bulkheadevents", BulkheadEventsEndpointResponse.class);
		List<BulkheadEventDTO> bulkheadEvents = bulkheadEventList.getBody().getBulkheadEvents();

		assertThat(bulkheadEvents).isNotEmpty();
		assertThat(bulkheadEvents.get(bulkheadEvents.size() - 1).getType()).isEqualTo(BulkheadEvent.Type.CALL_FINISHED);
		assertThat(bulkheadEvents.get(bulkheadEvents.size() - 2).getType()).isEqualTo(BulkheadEvent.Type.CALL_FINISHED);
		assertThat(bulkheadEvents.get(bulkheadEvents.size() - 3).getType()).isEqualTo(BulkheadEvent.Type.CALL_REJECTED);
		assertThat(bulkheadEvents.get(bulkheadEvents.size() - 4).getType()).isEqualTo(BulkheadEvent.Type.CALL_REJECTED);

		bulkheadEventList = restTemplate.getForEntity("/actuator/bulkheadevents/backendB", BulkheadEventsEndpointResponse.class);
		List<BulkheadEventDTO> bulkheadEventsByBackend = bulkheadEventList.getBody().getBulkheadEvents();

		assertThat(bulkheadEventsByBackend).hasSameSizeAs(bulkheadEvents);
		assertThat(bulkheadEventsByBackend.get(bulkheadEvents.size() - 1).getType()).isEqualTo(BulkheadEvent.Type.CALL_FINISHED);
		assertThat(bulkheadEventsByBackend).filteredOn(it -> it.getType() == BulkheadEvent.Type.CALL_REJECTED)
				.isNotEmpty();

		assertThat(bulkheadAspect.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
	}
}
