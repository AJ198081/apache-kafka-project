package dev.aj.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.aj.domain.model.WikiModel;
import dev.aj.producer.CustomKafkaProducer;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Slf4j
public class OpenSearchConsumer {

    private static final String WIKIMEDIA = "wikimedia-1";

    @SneakyThrows
    public static void main(String[] args) {
        RestHighLevelClient openSearchClient = createOpenSearchClient();

        ObjectMapper objectMapper = new ObjectMapper();

        DefaultKafkaConsumerFactory<Long, WikiModel> consumerFactory = getConsumerFactory();
        Consumer<Long, WikiModel> kafkaConsumer = consumerFactory.createConsumer("consumer-wikimedia", "version-1");

        try (openSearchClient; kafkaConsumer) {
            boolean wikimediaIndexExists = openSearchClient.indices()
                                                           .exists(new GetIndexRequest(WIKIMEDIA),
                                                                   RequestOptions.DEFAULT);

            if (!wikimediaIndexExists) {
                CreateIndexRequest createIndexRequest = new CreateIndexRequest(WIKIMEDIA);
                RequestOptions aDefault = RequestOptions.DEFAULT;
                CreateIndexResponse indexResponse = openSearchClient.indices()
                                                                    .create(createIndexRequest, aDefault);
                indexResponse.index();
                log.info("[%s] index has been created!!".formatted(WIKIMEDIA));
            } else {
                log.info("[%s] index already exists!!".formatted(WIKIMEDIA));
            }

            kafkaConsumer.subscribe(Collections.singletonList(CustomKafkaProducer.TOPIC));

            while (true) {
                ConsumerRecords<Long, WikiModel> consumerRecords = kafkaConsumer.poll(Duration.ofMillis(3000));

                log.info(STR."Received and about to process \{consumerRecords.count()} records");

                BulkRequest bulkRequest = new BulkRequest();

                consumerRecords.forEach(record -> {
                    try {

                        String id = STR."\{record.topic()}_\{record.partition()}_\{record.value().getMeta().getId()}";

                        IndexRequest indexRequest = new IndexRequest(WIKIMEDIA)
                                .source(objectMapper.writeValueAsString(record.value()), XContentType.JSON)
                                .id(id);

//                        To 'index' each record once.
//                        IndexResponse index = openSearchClient.index(indexRequest, RequestOptions.DEFAULT);
//                        log.info("Index received post persists ID: [%s]".formatted(index.getId()));

//                        For bulk optimisation
                        bulkRequest.add(indexRequest);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                if (bulkRequest.numberOfActions() > 0) {

                    BulkResponse bulkResponse = openSearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);

                    log.info(STR."Indexed a total of \{bulkResponse.getItems().length} indexes");

                    //Commit offset
                    kafkaConsumer.commitSync();
                    log.info(STR."Offset has been committed for \{consumerRecords.count()} records");
                    TimeUnit.SECONDS.sleep(5);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static DefaultKafkaConsumerFactory<Long, WikiModel> getConsumerFactory() {
        JsonDeserializer<WikiModel> wikiModelJsonDeserializer = new JsonDeserializer<>();
        wikiModelJsonDeserializer.addTrustedPackages("*");

        Map<String, Object> consumerProps = new HashMap<>();

        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                          Stream.of("localhost:9092", "localhost:9095", "localhost:9098")
                                .toList());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "consumer-OpenSearch-demo");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        return new DefaultKafkaConsumerFactory<>(consumerProps, new LongDeserializer(), wikiModelJsonDeserializer);
    }

    private static ConcurrentKafkaListenerContainerFactory<Long, WikiModel> getListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<Long, WikiModel> listenerContainerFactory = new ConcurrentKafkaListenerContainerFactory<>();
        listenerContainerFactory.setConsumerFactory(getConsumerFactory());
        return listenerContainerFactory;
    }

    public static RestHighLevelClient createOpenSearchClient() {

        RestHighLevelClient restHighLevelClient;

        String connectionToOpenSearch = "http://localhost:9200";
        URI connectionURI = URI.create(connectionToOpenSearch);

        String userInfo = connectionURI.getUserInfo();

        if (Objects.isNull(userInfo)) {
            restHighLevelClient = new RestHighLevelClient(
                    RestClient.builder(new HttpHost(connectionURI.getHost(), connectionURI.getPort())));
        } else {
            String[] authenticationObject = userInfo.split(":");
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(authenticationObject[0],
                                                                                              authenticationObject[1]));
            restHighLevelClient = new RestHighLevelClient(RestClient.builder(
                                                                            new HttpHost(connectionURI.getHost(), connectionURI.getPort(), connectionURI.getScheme()))
                                                                    .setHttpClientConfigCallback(
                                                                            httpAsyncClientBuilder -> httpAsyncClientBuilder.setDefaultCredentialsProvider(
                                                                                                                                    credentialsProvider)
                                                                                                                            .setKeepAliveStrategy(
                                                                                                                                    new DefaultConnectionKeepAliveStrategy())));
        }

        return restHighLevelClient;
    }
}
