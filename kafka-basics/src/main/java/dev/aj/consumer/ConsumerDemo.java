package dev.aj.consumer;

import dev.aj.producer.ProducerDemo;
import dev.aj.producer.domain.User;
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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Slf4j
public class ConsumerDemo {

    private static final String GROUP_ID = "user_created_consumer-1";

    public static void main(String[] args) {

        KafkaConsumer<String, User> kafkaConsumer = getKafkaConsumer();

        kafkaConsumer.subscribe(Collections.singleton(ProducerDemo.TOPIC));

        ConsumerRecords<String, User> records = kafkaConsumer.poll(Duration.of(5, ChronoUnit.SECONDS));

        records.forEach(record -> {
            User user = record.value();
            log.info(STR."Received \{record.key()}, \{user}");
        });

    }

    private static KafkaConsumer<String, User> getKafkaConsumer() {
        Map<String, Object> kafkaProperties = new HashMap<>();

        List<String> bootstrapServers = Stream.of("localhost:9092", "localhost:9095", "localhost:9098")
                                              .toList();

        JsonDeserializer<User> userJsonDeserializer = new JsonDeserializer<>();
        userJsonDeserializer.addTrustedPackages("dev.aj.producer.domain");

        kafkaProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        kafkaProperties.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        kafkaProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new KafkaConsumer<>(kafkaProperties, new StringDeserializer(),
                                   userJsonDeserializer);
    }
}
