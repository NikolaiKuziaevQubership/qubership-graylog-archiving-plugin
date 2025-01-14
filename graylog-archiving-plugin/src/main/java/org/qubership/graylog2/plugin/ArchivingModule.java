package org.qubership.graylog2.plugin;

import com.google.inject.name.Names;
import org.qubership.graylog2.plugin.archiving.ArchivingService;
import org.qubership.graylog2.plugin.rest.resources.ArchivingResource;
import org.qubership.graylog2.plugin.utils.FileProcessor;
import org.qubership.graylog2.plugin.utils.GraylogProcessor;
import io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.graylog2.plugin.PluginConfigBean;
import org.graylog2.plugin.PluginModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.regions.Region;

import java.util.Collections;
import java.util.Set;

public class ArchivingModule extends PluginModule {
    private static final Logger log = LoggerFactory.getLogger(ArchivingModule.class);
    private static final String DEFAULT_ELASTICSEARCH_URL = "http://elasticsearch:9200";
    private static final String ELASTICSEARCH_ENV = "GRAYLOG_ELASTICSEARCH_HOSTS";
    private static final String ELASTICSEARCH_SERVICE = "es";
    private static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";

    @Override
    public Set<? extends PluginConfigBean> getConfigBeans() {
        return Collections.emptySet();
    }

    @Override
    protected void configure() {
        bind(ArchivingService.class);
        bind(GraylogProcessor.class);
        bind(FileProcessor.class);
        final JestClientFactory factory;
        if (System.getenv(AWS_ACCESS_KEY_ID) == null || System.getenv(AWS_ACCESS_KEY_ID).isEmpty()) {
            factory = new JestClientFactory();
        } else {
            HttpRequestInterceptor interceptor = new AwsRequestSigningApacheInterceptor(
                    ELASTICSEARCH_SERVICE,
                    Aws4Signer.create(),
                    EnvironmentVariableCredentialsProvider.create(),
                    Region.US_EAST_1
            );

            factory = new JestClientFactory() {
                @Override
                protected HttpClientBuilder configureHttpClient(HttpClientBuilder builder) {
                    builder.addInterceptorLast(interceptor);
                    return builder;
                }

                @Override
                protected HttpAsyncClientBuilder configureHttpClient(HttpAsyncClientBuilder builder) {
                    builder.addInterceptorLast(interceptor);
                    return builder;
                }
            };
        }
        factory.setHttpClientConfig(
                new HttpClientConfig.Builder(getElasticSearchUrl())
                        .multiThreaded(true)
                        .defaultMaxTotalConnectionPerRoute(2)
                        .maxTotalConnection(10)
                        .readTimeout(0)
                        .build());
        bind(JestClient.class).annotatedWith(Names.named("ArchivingJestClient"))
                .toInstance(factory.getObject());
        bindRestResources();
    }

    private void bindRestResources() {
        addRestResource(ArchivingResource.class);
    }

    private String getElasticSearchUrl() {
        String elasticSearchHostsString = System.getenv(ELASTICSEARCH_ENV);
        String[] elasticSearchHosts = elasticSearchHostsString.split(",");
        log.info("Elasticsearch host: " + elasticSearchHostsString);
        // Currently this plugin is not support some ElasticSearch hosts, so use the first link from connection string
        if (elasticSearchHosts.length > 0) {
            return elasticSearchHosts[0];
        } else {
            return DEFAULT_ELASTICSEARCH_URL;
        }
    }
}