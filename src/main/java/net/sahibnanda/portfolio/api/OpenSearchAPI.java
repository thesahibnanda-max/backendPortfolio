package net.sahibnanda.portfolio.api;

import java.util.Objects;
import net.sahibnanda.portfolio.config.OpenSearchProperties;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.stereotype.Component;

import javax.print.attribute.standard.ReferenceUriSchemesSupported;

/**
 * Wraps the OpenSearch Java client, configured from
 * {@link OpenSearchProperties}.
 */
@Component
public final class OpenSearchAPI {

  /** The configured OpenSearch client. */
  private final OpenSearchClient openSearchClient;

  /**
   * Creates a new OpenSearch API wrapper.
   *
   * @param openSearchProperties connection settings for the OpenSearch cluster
   */
  public OpenSearchAPI(final OpenSearchProperties openSearchProperties) {
    Objects.requireNonNull(openSearchProperties,
        "openSearchProperties must not be null");

    HttpHost httpHost = new HttpHost(
        Boolean.TRUE.equals(openSearchProperties.https())
            ? ReferenceUriSchemesSupported.HTTPS.toString()
            : ReferenceUriSchemesSupported.HTTP.toString(),
        openSearchProperties.host(), openSearchProperties.port());

    BasicCredentialsProvider credentialsProvider =
        new BasicCredentialsProvider();
    credentialsProvider.setCredentials(new AuthScope(httpHost),
        new UsernamePasswordCredentials(openSearchProperties.username(),
            openSearchProperties.password().toCharArray()));

    ApacheHttpClient5Transport transport =
        ApacheHttpClient5TransportBuilder.builder(httpHost)
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                .setDefaultCredentialsProvider(credentialsProvider))
            .build();

    this.openSearchClient = new OpenSearchClient(transport);
  }
}
