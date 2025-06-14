package com.example.testapp;

import com.example.testapp.domain.EventStatusRequest;
import com.example.testapp.domain.ExternalApiResponse;
import com.example.testapp.service.EventStatusService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092",
    "kafka.topic=test-event-status-topic",
    "external.api.url=http://localhost:8081/mock"
})
class EventStatusAppApplicationTests {

	@Autowired
	private EmbeddedKafkaBroker embeddedKafkaBroker;

	@Autowired
	private EventStatusService eventStatusService;

	@MockBean
	private RestTemplate restTemplate;

	private KafkaTemplate<String, String> kafkaTemplate;
	private Consumer<String, String> consumer;
	private static final String TEST_TOPIC = "test-event-status-topic";

	@BeforeEach
	void setUp() {
		// Mock external API response
		ExternalApiResponse mockResponse = new ExternalApiResponse();
		mockResponse.setEventId("test-event");
		mockResponse.setCurrentScore("0:0");
		when(restTemplate.getForObject(anyString(), eq(ExternalApiResponse.class)))
				.thenReturn(mockResponse);

		// Set up Kafka producer
		Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
		producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		DefaultKafkaProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
		kafkaTemplate = new KafkaTemplate<>(producerFactory);

		// Set up Kafka consumer
		Map<String, Object> consumerProps = new HashMap<>(KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker));
		consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
		consumer = consumerFactory.createConsumer();
		consumer.subscribe(Collections.singletonList(TEST_TOPIC));
	}

	@AfterEach
	void tearDown() {
		if (consumer != null) {
			consumer.close();
		}
		if (kafkaTemplate != null) {
			kafkaTemplate.destroy();
		}
	}

	@Test
	void testKafkaMessagePublication_Success() throws InterruptedException {
		// Arrange
		EventStatusRequest request = new EventStatusRequest();
		request.setEventId("test-event-123");
		request.setStatus(EventStatusRequest.Status.LIVE);

		// Act
		eventStatusService.updateEventStatus(request);

		// Assert - Wait for the scheduled job to publish to Kafka
		Thread.sleep(3000); // Wait for the scheduled job to run

		ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
		assertFalse(records.isEmpty(), "Should have received at least one message");

		boolean messageFound = false;
		for (ConsumerRecord<String, String> record : records) {
			if (record.key().equals("test-event-123")) {
				assertTrue(record.value().contains("test-event-123"));
				assertTrue(record.value().contains("currentScore"));
				messageFound = true;
				break;
			}
		}
		assertTrue(messageFound, "Should have found message for test-event-123");
	}

	@Test
	void testKafkaMessagePublication_MultipleEvents() throws InterruptedException {
		// Arrange
		EventStatusRequest request1 = new EventStatusRequest();
		request1.setEventId("event-1");
		request1.setStatus(EventStatusRequest.Status.LIVE);

		EventStatusRequest request2 = new EventStatusRequest();
		request2.setEventId("event-2");
		request2.setStatus(EventStatusRequest.Status.LIVE);

		// Act
		eventStatusService.updateEventStatus(request1);
		eventStatusService.updateEventStatus(request2);

		// Assert - Wait for scheduled jobs to publish to Kafka
		Thread.sleep(4000);

		ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
		assertTrue(records.count() >= 2, "Should have received at least 2 messages");

		Map<String, Boolean> foundEvents = new HashMap<>();
		foundEvents.put("event-1", false);
		foundEvents.put("event-2", false);

		for (ConsumerRecord<String, String> record : records) {
			if (foundEvents.containsKey(record.key())) {
				foundEvents.put(record.key(), true);
			}
		}

		assertTrue(foundEvents.get("event-1"), "Should have found message for event-1");
		assertTrue(foundEvents.get("event-2"), "Should have found message for event-2");
	}

	@Test
	void testKafkaMessagePublication_EventStatusChange() throws InterruptedException {
		// Arrange
		EventStatusRequest liveRequest = new EventStatusRequest();
		liveRequest.setEventId("status-change-event");
		liveRequest.setStatus(EventStatusRequest.Status.LIVE);

		EventStatusRequest notLiveRequest = new EventStatusRequest();
		notLiveRequest.setEventId("status-change-event");
		notLiveRequest.setStatus(EventStatusRequest.Status.NOT_LIVE);

		// Act - Start with LIVE status
		eventStatusService.updateEventStatus(liveRequest);
		Thread.sleep(2000); // Wait for first message

		// Clear any existing messages
		consumer.poll(Duration.ofSeconds(1));

		// Change to NOT_LIVE status
		eventStatusService.updateEventStatus(notLiveRequest);
		Thread.sleep(2000); // Wait for potential messages

		// Assert - Should not receive new messages after status change to NOT_LIVE
		ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));
		assertTrue(records.isEmpty(), "Should not receive messages for NOT_LIVE events");
	}

	@Test
	void testKafkaMessagePublication_MessageFormat() throws InterruptedException {
		// Arrange
		EventStatusRequest request = new EventStatusRequest();
		request.setEventId("format-test-event");
		request.setStatus(EventStatusRequest.Status.LIVE);

		// Act
		eventStatusService.updateEventStatus(request);
		Thread.sleep(3000);

		// Assert - Check message format
		ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
		assertFalse(records.isEmpty(), "Should have received at least one message");

		for (ConsumerRecord<String, String> record : records) {
			if (record.key().equals("format-test-event")) {
				String value = record.value();
				assertTrue(value.contains("\"eventId\":\"format-test-event\""), "Message should contain eventId");
				assertTrue(value.contains("\"currentScore\":"), "Message should contain currentScore field");
				assertTrue(value.startsWith("{"), "Message should be valid JSON");
				assertTrue(value.endsWith("}"), "Message should be valid JSON");
				break;
			}
		}
	}

	@Test
	void testKafkaMessagePublication_ServiceHandlesErrorsGracefully() {
		// Arrange - Mock external API to throw exception
		when(restTemplate.getForObject(anyString(), eq(ExternalApiResponse.class)))
				.thenThrow(new RuntimeException("External API error"));

		EventStatusRequest request = new EventStatusRequest();
		request.setEventId("error-test-event");
		request.setStatus(EventStatusRequest.Status.LIVE);

		// Act & Assert - Service should handle errors gracefully
		assertDoesNotThrow(() -> {
			eventStatusService.updateEventStatus(request);
			Thread.sleep(2000); // Wait for error handling
		});
	}
}
