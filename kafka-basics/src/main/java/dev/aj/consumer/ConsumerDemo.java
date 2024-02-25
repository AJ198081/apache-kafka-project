package dev.aj.consumer;

import dev.aj.producer.ProducerDemo;
import dev.aj.domain.User;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Slf4j
public class ConsumerDemo {

    private static final String GROUP_ID = "user_created_consumer-1";

    public static void main(String[] args) {

        KafkaConsumer<String, User> kafkaConsumer = getKafkaConsumer();

        final Thread thread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                log.info("Detected a shutdown, let's exit by calling consumer.wakeup()...");
                kafkaConsumer.wakeup();

                //join the main thread to allow the execution of the code in the main thread.
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        try {
            kafkaConsumer.subscribe(Collections.singleton(ProducerDemo.TOPIC));
            boolean continue_polling = true;
            while (continue_polling) {
                ConsumerRecords<String, User> records = kafkaConsumer.poll(Duration.of(5, ChronoUnit.SECONDS));

                records.forEach(record -> {
                    User user = record.value();
                    log.info(STR."Received \{record.key()}, \{user}");
                });
            }
        } catch (WakeupException e) {
            // Expected exception, nothing to react to, as reaction will happen programmatically.
            log.info("WakeupException is thrown when invoking 'wakeup()' on the consumer object");
        } catch (Exception e) {
            log.error("Unexpected exception during the consumer poll");
        } finally {
            kafkaConsumer.close(); // Tries to commit the current offset to Kraft, if possible.
        }

    }

    private static KafkaConsumer<String, User> getKafkaConsumer() {
        Map<String, Object> kafkaProperties = new HashMap<>();

        List<String> bootstrapServers = Stream.of("localhost:9092", "localhost:9095", "localhost:9098")
                                              .toList();

        JsonDeserializer<User> userJsonDeserializer = new JsonDeserializer<>();
        userJsonDeserializer.addTrustedPackages("dev.aj.domain");

        kafkaProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        kafkaProperties.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        kafkaProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Best way to optimise your Kafka cluster in general is to flash it up with minimum configuration,
        // Determine from the log what you really need
        kafkaProperties.put("partition.assignment.strategy",
                            "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");

        return new KafkaConsumer<>(kafkaProperties, new StringDeserializer(),
                                   userJsonDeserializer);
    }
}
