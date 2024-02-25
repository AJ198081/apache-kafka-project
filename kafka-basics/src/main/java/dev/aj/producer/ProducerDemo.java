package dev.aj.producer;

import dev.aj.domain.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Slf4j
public class ProducerDemo {

    public static final String TOPIC = "user-created-topic-new";

    private static ProducerRecord<String, User> getUserProducerRecord() {
        User aj = User.builder().userId(UUID.randomUUID())
                      .name("AJ")
                      .email("abg@test.com")
                      .id(new Random().nextInt(1000, 10000))
                      .build();

        return new ProducerRecord<>(TOPIC,
                                    aj.getUserId().toString(),
                                    aj);
    }

    @SneakyThrows
    public static void main(String[] args) {
        ProducerDemo producerDemo = new ProducerDemo();

        KafkaProducer<String, User> kafkaProducer = producerDemo.instantiateProducer();

        for (int i = 0; i < 100; i++) {
            TimeUnit.MILLISECONDS.sleep(1000);

            producerDemo.producerToKafkaTopicSynchronized(kafkaProducer);
            producerDemo.producerToKafkaTopicAsync(kafkaProducer);
        }

        kafkaProducer.close();
    }

    private KafkaProducer<String, User> instantiateProducer() {

        Map<String, Object> kafkaProperties = new HashMap<>();

        List<String> bootstrapServers = Stream.of("localhost:9092", "localhost:9095", "localhost:9098")
                                              .toList();

        kafkaProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        kafkaProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());
        kafkaProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        kafkaProperties.put(ProducerConfig.BATCH_SIZE_CONFIG, 5);

        return new KafkaProducer<>(kafkaProperties);
    }

    private void producerToKafkaTopicSynchronized(KafkaProducer<String, User> kafkaProducer) {

        StopWatch stopWatch = new StopWatch("Producer Watch");

        stopWatch.start();

        ProducerRecord<String, User> produceAj = getUserProducerRecord();

        Future<RecordMetadata> recordMetadataFuture = kafkaProducer.send(produceAj);

        try {
            RecordMetadata recordMetadata = recordMetadataFuture.get(5, TimeUnit.SECONDS);
            stopWatch.stop();
            log.info("Producer has returned with %s, took %s".formatted(recordMetadata, stopWatch.getStopTime()));
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            log.info("Exception of type %s, with message %s".formatted(ex.getClass().toString(), ex.getMessage()));
            throw new RuntimeException(ex);
        }
    }

    private void producerToKafkaTopicAsync(KafkaProducer<String, User> kafkaProducer) {

        ProducerRecord<String, User> userProducerRecord = getUserProducerRecord();

        kafkaProducer.send(userProducerRecord, (metadata, exception) -> {
            if (exception == null) {
                log.info("Received metadata%n Key: %s%n Topic: %s%n Partition %s%n Offset %d%n Timestamp %d".formatted(userProducerRecord.key(),
                                               metadata.topic(),
                                               metadata.partition(),
                                               metadata.offset(),
                                               metadata.timestamp()));
            } else {
                log.info("Exception of type %s, message is %s".formatted(exception.getClass().getName(),
                                                                         exception.getMessage()));
            }
        });
    }

}
