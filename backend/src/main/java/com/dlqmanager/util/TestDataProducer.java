package com.dlqmanager.util;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutionException;

/**
 * Test Data Producer
 *
 * Purpose: Generate realistic test messages for the orders-dlq topic
 *
 * This is a ONE-TIME utility script to populate test data.
 * Run this BEFORE building the message browser, so you have data to work with.
 *
 * What it does:
 * 1. Connects to Kafka cluster (localhost:9092)
 * 2. Creates 50 test messages with realistic order data
 * 3. Adds error headers (simulating failed messages)
 * 4. Sends messages to orders-dlq topic
 * 5. Prints progress and summary
 */
public class TestDataProducer {

    private static final String TOPIC = "orders-dlq";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final int NUM_MESSAGES = 50;

    private static final Random random = new Random();

    /**
     * Configure Kafka Producer
     *
     * Key configs:
     * - bootstrap.servers: Where is Kafka? (localhost:9092)
     * - key.serializer: How to convert key (String) to bytes
     * - value.serializer: How to convert value (JSON String) to bytes
     */
    private static Properties getProducerConfig() {
        Properties props = new Properties();
        props.put("bootstrap.servers", BOOTSTRAP_SERVERS);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("acks", "all");  // Wait for all replicas to acknowledge (safest)
        return props;
    }

    /**
     * Generate realistic order JSON payload
     *
     * @param orderId The order ID (e.g., "ORD-78923")
     * @param timestamp When the order was created
     * @return JSON string representing an order
     */
    private static String createOrderPayload(String orderId, Instant timestamp) {
        // Random user ID
        int userId = 100 + random.nextInt(900);

        // Random product
        String[] products = {
            "Wireless Mouse", "Mechanical Keyboard", "USB-C Cable",
            "Laptop Stand", "Webcam", "Headphones", "Monitor"
        };
        String productName = products[random.nextInt(products.length)];
        String productId = "PROD-" + (100 + random.nextInt(900));

        // Random quantity and price
        int quantity = 1 + random.nextInt(3);
        int price = 500 + random.nextInt(20000);
        int totalAmount = quantity * price;

        // Build JSON (manual string building - simple and clear)
        return String.format("""
            {
              "orderId": "%s",
              "userId": "USR-%d",
              "amount": %d,
              "currency": "INR",
              "items": [
                {
                  "productId": "%s",
                  "name": "%s",
                  "quantity": %d,
                  "price": %d
                }
              ],
              "shippingAddress": {
                "city": "Bangalore",
                "pincode": "560001"
              },
              "createdAt": "%s"
            }
            """, orderId, userId, totalAmount, productId, productName, quantity, price, timestamp.toString());
    }

    /**
     * Pick a random error type with weighted probability
     *
     * Distribution:
     * - DB Connection Timeout: 70%
     * - Invalid JSON Format: 20%
     * - Validation Failed: 6%
     * - Unknown Error: 4%
     *
     * Why different errors? To test the "Error Breakdown" feature later!
     */
    private static String getRandomErrorType() {
        int rand = random.nextInt(100);

        if (rand < 70) {
            return "DB Connection Timeout";
        } else if (rand < 90) {
            return "Invalid JSON Format";
        } else if (rand < 96) {
            return "Validation Failed";
        } else {
            return "Unknown Error";
        }
    }

    /**
     * Get random timestamp from the last 7 days
     *
     * Why? To simulate messages failing at different times.
     * This helps test time-based filtering later.
     */
    private static Instant getRandomTimestamp() {
        long daysAgo = random.nextInt(7);  // 0 to 6 days ago
        long hoursAgo = random.nextInt(24);  // 0 to 23 hours ago
        long minutesAgo = random.nextInt(60);  // 0 to 59 minutes ago

        return Instant.now()
                .minus(daysAgo, ChronoUnit.DAYS)
                .minus(hoursAgo, ChronoUnit.HOURS)
                .minus(minutesAgo, ChronoUnit.MINUTES);
    }

    /**
     * Main method - Run this to populate test data!
     */
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("    DLQ Test Data Producer");
        System.out.println("========================================");
        System.out.println("Target topic: " + TOPIC);
        System.out.println("Number of messages: " + NUM_MESSAGES);
        System.out.println("Kafka cluster: " + BOOTSTRAP_SERVERS);
        System.out.println("========================================\n");

        // Create Kafka Producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(getProducerConfig());

        int successCount = 0;
        int failureCount = 0;

        // Track error distribution
        int dbTimeoutCount = 0;
        int invalidJsonCount = 0;
        int validationFailedCount = 0;
        int unknownErrorCount = 0;

        try {
            // Send 50 messages
            for (int i = 0; i < NUM_MESSAGES; i++) {
                // Generate message data
                String orderId = "ORD-" + (78900 + i);
                String errorType = getRandomErrorType();
                Instant timestamp = getRandomTimestamp();
                String payload = createOrderPayload(orderId, timestamp);

                // Count error types for summary
                switch (errorType) {
                    case "DB Connection Timeout" -> dbTimeoutCount++;
                    case "Invalid JSON Format" -> invalidJsonCount++;
                    case "Validation Failed" -> validationFailedCount++;
                    case "Unknown Error" -> unknownErrorCount++;
                }

                // Create ProducerRecord
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC,      // Topic name
                    orderId,    // Key (used for partitioning)
                    payload     // Value (JSON string)
                );

                // Add DLQ-specific headers
                // These headers simulate what a real DLQ producer would add
                record.headers().add("X-Original-Topic", "orders".getBytes());
                record.headers().add("X-Error-Message", errorType.getBytes());
                record.headers().add("X-Retry-Count", "3".getBytes());
                record.headers().add("X-Failed-Timestamp", String.valueOf(timestamp.toEpochMilli()).getBytes());
                record.headers().add("X-Exception-Class", "java.sql.SQLException".getBytes());
                record.headers().add("X-Consumer-Group", "order-processor-group".getBytes());

                try {
                    // Send synchronously (wait for confirmation)
                    RecordMetadata metadata = producer.send(record).get();

                    successCount++;
                    System.out.printf("[%2d/%d] ✓ Sent: %s | Error: %-25s | Partition: %d | Offset: %d%n",
                            i + 1, NUM_MESSAGES, orderId, errorType, metadata.partition(), metadata.offset());

                } catch (ExecutionException | InterruptedException e) {
                    failureCount++;
                    System.err.printf("[%2d/%d] ✗ Failed: %s | Error: %s%n",
                            i + 1, NUM_MESSAGES, orderId, e.getMessage());
                }
            }

        } finally {
            // Always close the producer
            producer.close();
        }

        // Print summary
        System.out.println("\n========================================");
        System.out.println("         SUMMARY");
        System.out.println("========================================");
        System.out.println("Total messages: " + NUM_MESSAGES);
        System.out.println("✓ Succeeded: " + successCount);
        System.out.println("✗ Failed: " + failureCount);
        System.out.println("\nError Distribution:");
        System.out.println("  DB Connection Timeout: " + dbTimeoutCount + " (" + (dbTimeoutCount * 100 / NUM_MESSAGES) + "%)");
        System.out.println("  Invalid JSON Format: " + invalidJsonCount + " (" + (invalidJsonCount * 100 / NUM_MESSAGES) + "%)");
        System.out.println("  Validation Failed: " + validationFailedCount + " (" + (validationFailedCount * 100 / NUM_MESSAGES) + "%)");
        System.out.println("  Unknown Error: " + unknownErrorCount + " (" + (unknownErrorCount * 100 / NUM_MESSAGES) + "%)");
        System.out.println("========================================");

        if (successCount == NUM_MESSAGES) {
            System.out.println("\n✅ SUCCESS! All test messages sent to " + TOPIC);
            System.out.println("\nNext steps:");
            System.out.println("1. Verify messages: docker exec -it dlq-kafka kafka-console-consumer --topic orders-dlq --from-beginning --max-messages 5");
            System.out.println("2. Build the message browser service");
        } else {
            System.out.println("\n⚠️  Some messages failed. Check Kafka connection.");
        }
    }
}
