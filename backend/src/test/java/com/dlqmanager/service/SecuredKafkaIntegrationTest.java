package com.dlqmanager.service;

import com.dlqmanager.IntegrationTestBase;
import com.dlqmanager.config.KafkaConnection;
import com.dlqmanager.model.dto.KafkaConfigRequest;
import com.dlqmanager.model.dto.ReplayJobDto;
import com.dlqmanager.model.dto.ReplayRequestDto;
import com.dlqmanager.model.entity.DlqTopic;
import com.dlqmanager.model.enums.DetectionType;
import com.dlqmanager.model.enums.DlqStatus;
import com.dlqmanager.model.enums.KafkaAuthentication;
import com.dlqmanager.model.enums.ReplayStatus;
import com.dlqmanager.repository.DlqTopicRepository;
import com.dlqmanager.repository.KafkaConfigRepository;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.Container;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The app against a Kafka set up the way a company cluster usually is
 *
 * A second Kafka container whose client listener only accepts:
 * - an encrypted (TLS) connection, with a certificate from a private authority
 *   that the client has to be told to trust
 * - a login: PLAIN for "plainuser" (password in the broker config), or
 *   SCRAM-SHA-512 for "scramuser" (created after startup, like on a real cluster)
 */
class SecuredKafkaIntegrationTest extends IntegrationTestBase {

    private static final String PLAIN_USER = "plainuser";
    private static final String PLAIN_PASSWORD = "plain-s3cret";
    private static final String SCRAM_USER = "scramuser";
    private static final String SCRAM_PASSWORD = "scram-s3cret";

    private static final String KEYSTORE_PASSWORD = "changeit";

    private static ConfluentKafkaContainer securedKafka;

    /**
     * The broker's certificate as PEM text - what an admin would paste into Settings
     */
    private static String caCertificate;

    @Autowired
    private KafkaConfigService kafkaConfigService;

    @Autowired
    private KafkaConfigRepository kafkaConfigRepository;

    @Autowired
    private DlqBrowserService dlqBrowserService;

    @Autowired
    private ReplayService replayService;

    @Autowired
    private DlqTopicRepository dlqTopicRepository;

    @BeforeAll
    static void startSecuredKafka() throws Exception {
        Path keystore = createBrokerCertificate();

        securedKafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.5.0")
                // Testcontainers names the listener for clients "PLAINTEXT"; make it require TLS and a login
                .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                        "BROKER:PLAINTEXT,PLAINTEXT:SASL_SSL,CONTROLLER:PLAINTEXT")
                .withCopyFileToContainer(MountableFile.forHostPath(keystore, 0644), "/etc/kafka/secrets/broker.p12")
                .withEnv("KAFKA_SSL_KEYSTORE_LOCATION", "/etc/kafka/secrets/broker.p12")
                .withEnv("KAFKA_SSL_KEYSTORE_TYPE", "PKCS12")
                .withEnv("KAFKA_SSL_KEYSTORE_PASSWORD", KEYSTORE_PASSWORD)
                .withEnv("KAFKA_SSL_KEY_PASSWORD", KEYSTORE_PASSWORD)
                .withEnv("KAFKA_LISTENER_NAME_PLAINTEXT_SASL_ENABLED_MECHANISMS", "PLAIN,SCRAM-SHA-512")
                .withEnv("KAFKA_LISTENER_NAME_PLAINTEXT_PLAIN_SASL_JAAS_CONFIG",
                        "org.apache.kafka.common.security.plain.PlainLoginModule required "
                                + "user_" + PLAIN_USER + "=\"" + PLAIN_PASSWORD + "\";")
                .withEnv("KAFKA_LISTENER_NAME_PLAINTEXT_SCRAM___SHA___512_SASL_JAAS_CONFIG",
                        "org.apache.kafka.common.security.scram.ScramLoginModule required;");
        securedKafka.start();

        // Create the SCRAM user through the broker's internal (no login) listener
        Container.ExecResult result = securedKafka.execInContainer("kafka-configs",
                "--bootstrap-server", "localhost:9093", "--alter",
                "--add-config", "SCRAM-SHA-512=[password=" + SCRAM_PASSWORD + "]",
                "--entity-type", "users", "--entity-name", SCRAM_USER);
        assertThat(result.getExitCode()).as(result.getStdout() + result.getStderr()).isZero();
    }

    @AfterAll
    static void stopSecuredKafka() {
        securedKafka.stop();
    }

    /**
     * Other test classes share this Spring context and expect the default (unsecured) test Kafka
     */
    @AfterEach
    void backToTheDefaultConnection() {
        kafkaConfigRepository.deleteAll();
    }

    @Test
    void connectionTestPassesWithTheRightPlainLogin() {
        Map<String, Object> result = kafkaConfigService.testConnection(
                login(KafkaAuthentication.PLAIN, PLAIN_USER, PLAIN_PASSWORD));

        assertThat(result).containsEntry("success", true).containsEntry("brokerCount", 1);
    }

    @Test
    void connectionTestPassesWithTheRightScramLogin() {
        Map<String, Object> result = kafkaConfigService.testConnection(
                login(KafkaAuthentication.SCRAM_SHA_512, SCRAM_USER, SCRAM_PASSWORD));

        assertThat(result).containsEntry("success", true).containsEntry("brokerCount", 1);
    }

    @Test
    void connectionTestFailsWithAWrongPassword() {
        Map<String, Object> plain = kafkaConfigService.testConnection(
                login(KafkaAuthentication.PLAIN, PLAIN_USER, "wrong"));
        Map<String, Object> scram = kafkaConfigService.testConnection(
                login(KafkaAuthentication.SCRAM_SHA_512, SCRAM_USER, "wrong"));

        assertThat(plain).containsEntry("success", false);
        assertThat((String) plain.get("error")).contains("Authentication failed");
        assertThat(scram).containsEntry("success", false);
        assertThat((String) scram.get("error")).contains("Authentication failed");
    }

    @Test
    void connectionTestFailsWithoutLoginAndEncryption() {
        Map<String, Object> result = kafkaConfigService.testConnection(
                new KafkaConfigRequest(securedKafka.getBootstrapServers(), KafkaAuthentication.NONE, false,
                        null, null, null));

        // The broker never answers a client like that, so the message has to point at the cause
        assertThat(result).containsEntry("success", false);
        assertThat((String) result.get("error")).contains("login and encryption settings");
    }

    @Test
    void connectionTestFailsWhenTheCompanyCertificateIsNotGiven() {
        Map<String, Object> result = kafkaConfigService.testConnection(
                new KafkaConfigRequest(securedKafka.getBootstrapServers(), KafkaAuthentication.PLAIN, true,
                        PLAIN_USER, PLAIN_PASSWORD, null));

        assertThat(result).containsEntry("success", false);
        assertThat((String) result.get("error")).contains("certificate is not trusted");
    }

    @Test
    void browsingAndReplayWorkOnceTheLoginIsSaved() throws Exception {
        String source = uniqueTopic("payments");
        String dlq = source + "-dlq";
        createSecuredTopics(source, dlq);
        produceToSecured(dlq, 3);

        kafkaConfigService.saveConfig(login(KafkaAuthentication.SCRAM_SHA_512, SCRAM_USER, SCRAM_PASSWORD));
        UUID id = register(dlq, source);

        // Browse: the pooled consumers sign in
        assertThat(dlqBrowserService.getMessageCount(id)).isEqualTo(3);
        assertThat(dlqBrowserService.getErrorBreakdown(id)).containsEntry("Card declined", 3L);
        assertThat(dlqBrowserService.getMessages(id, 1, 10)).hasSize(3);

        // Replay: the producer signs in too
        ReplayJobDto job = replayService.replayMessage(single(id, 1L));

        assertThat(job.getStatus()).isEqualTo(ReplayStatus.COMPLETED);
        List<ConsumerRecord<String, String>> replayed = readAllFromSecured(source, 1);
        assertThat(replayed).hasSize(1);
        assertThat(replayed.get(0).key()).isEqualTo("PAY-1");
    }

    // --- Helpers ---

    private static KafkaConfigRequest login(KafkaAuthentication authentication, String username, String password) {
        return new KafkaConfigRequest(securedKafka.getBootstrapServers(), authentication, true,
                username, password, caCertificate);
    }

    /**
     * What a correctly configured client for the secured cluster looks like
     */
    private static Map<String, Object> securedClient() {
        return new KafkaConnection(securedKafka.getBootstrapServers(), KafkaAuthentication.PLAIN, true,
                PLAIN_USER, PLAIN_PASSWORD, caCertificate).clientProperties();
    }

    /**
     * A self-signed certificate for the broker, made with the JDK's keytool.
     * Self-signed means it is its own authority - exactly the "company-internal authority" case:
     * no client trusts it until it is given the certificate.
     *
     * @return the keystore the broker uses (private key + certificate)
     */
    private static Path createBrokerCertificate() throws Exception {
        Path dir = Files.createTempDirectory("dlq-secured-kafka");
        Path keystore = dir.resolve("broker.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();

        run(keytool, "-genkeypair", "-alias", "broker", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "san=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", keystore.toString(),
                "-storepass", KEYSTORE_PASSWORD, "-keypass", KEYSTORE_PASSWORD);
        caCertificate = run(keytool, "-exportcert", "-alias", "broker", "-rfc",
                "-keystore", keystore.toString(), "-storepass", KEYSTORE_PASSWORD);

        return keystore;
    }

    private static String run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        return output;
    }

    private static void createSecuredTopics(String... names) throws Exception {
        try (AdminClient admin = AdminClient.create(securedClient())) {
            List<NewTopic> topics = new ArrayList<>();
            for (String name : names) {
                topics.add(new NewTopic(name, 1, (short) 1));
            }
            admin.createTopics(topics).all().get();
        }
    }

    private static void produceToSecured(String topic, int count) throws Exception {
        Map<String, Object> props = securedClient();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 0; i < count; i++) {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        topic, 0, "PAY-" + i, "{\"paymentId\":\"PAY-" + i + "\"}");
                record.headers().add("X-Error-Message", "Card declined".getBytes(StandardCharsets.UTF_8));
                producer.send(record).get();
            }
        }
    }

    private static List<ConsumerRecord<String, String>> readAllFromSecured(String topic, int expected) {
        Map<String, Object> props = securedClient();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));

            long deadline = System.currentTimeMillis() + 15_000;
            while (records.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        return records;
    }

    private UUID register(String dlqTopicName, String sourceTopic) {
        DlqTopic topic = new DlqTopic();
        topic.setDlqTopicName(dlqTopicName);
        topic.setSourceTopic(sourceTopic);
        topic.setDetectionType(DetectionType.MANUAL);
        topic.setStatus(DlqStatus.ACTIVE);
        return dlqTopicRepository.save(topic).getId();
    }

    private static ReplayRequestDto single(UUID dlqTopicId, long offset) {
        ReplayRequestDto request = new ReplayRequestDto();
        request.setDlqTopicId(dlqTopicId);
        request.setMessageOffset(offset);
        request.setMessagePartition(0);
        request.setInitiatedBy("it-test");
        return request;
    }
}
