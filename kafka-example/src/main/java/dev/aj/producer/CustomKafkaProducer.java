package dev.aj.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aj.domain.model.WikiModel;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.web.client.RestClient;

@Slf4j
public class CustomKafkaProducer {

    public static final String TOPIC = "wikimedia-recent-change-v2";

    public static void main(String[] args) {

        CustomKafkaProducer customKafkaProducer = new CustomKafkaProducer();
        customKafkaProducer.eventSourceConfig();
    }

    private KafkaProducer<Long, WikiModel> getProducer() {

        Properties kafkaProperties = new Properties();

        List<String> bootstrapServers = Stream.of("localhost:9092", "localhost:9095", "localhost:9098")
                                              .toList();

        kafkaProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        kafkaProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class.getName());
        kafkaProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());
        kafkaProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        kafkaProperties.put(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32 * 1024)); //32 kB
        kafkaProperties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        kafkaProperties.put(ProducerConfig.LINGER_MS_CONFIG, Integer.toString(50));
        kafkaProperties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000);
        kafkaProperties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, Integer.toString(1100));
        kafkaProperties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // higher performance and keeps ordering
        kafkaProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // message gets delivered once

        return new KafkaProducer<>(kafkaProperties);
    }

    private void eventSourceConfig() {



        KafkaProducer<Long, WikiModel> producer = getProducer();

        RestClient restClient = RestClient.builder()
                                          .baseUrl("https://stream.wikimedia.org/v2/stream")
                                          .build();

        restClient.get()
                  .uri("/recentchange")
                  .exchange((clientRequest, clientResponse) -> {
                      if (clientResponse.getStatusCode().is2xxSuccessful()) {
                          BufferedReader bufferedReader = new BufferedReader(
                                  new InputStreamReader(clientResponse.getBody()));

                          bufferedReader.lines()
                                        .filter(line -> line.startsWith("data: "))
                                        .map(this::parseJson)
                                        .forEach(wikiModel -> {
                                            producer.send(new ProducerRecord<Long, WikiModel>(TOPIC,
                                                                                              wikiModel.getId(),
                                                                                              wikiModel));
                                        });

                      } else {
                          log.info("Returned Status %s".formatted(clientResponse.getStatusCode()));
                      }
                      return HttpStatus.OK;
                  });
    }

    @SneakyThrows
    private WikiModel parseJson(String input) {
            String data = input.split("data: ")[1];
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(data, WikiModel.class);
    }
}