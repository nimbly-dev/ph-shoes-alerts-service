package com.nimbly.phshoesbackend.alerts.core.config;

import com.nimbly.phshoesbackend.alerts.core.config.props.AppAwsProps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.io.File;
import java.net.URI;
import java.time.Duration;

@Configuration
public class DynamoConfig {

    private final AppAwsProps aws;

    public DynamoConfig(AppAwsProps aws) {
        this.aws = aws;
    }

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        if (StringUtils.hasText(aws.getEndpoint())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public DynamoDbClient dynamoDbClient(AwsCredentialsProvider credentialsProvider) {
        SdkHttpClient http = ApacheHttpClient.builder()
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(2))
                .socketTimeout(Duration.ofSeconds(5))
                .build();

        var builder = DynamoDbClient.builder()
                .httpClient(http)
                .credentialsProvider(credentialsProvider)
                .region(Region.of(aws.getRegion()));

        if (StringUtils.hasText(aws.getEndpoint())) {
            builder = builder.endpointOverride(normalizeEndpoint(aws.getEndpoint()));
        }

        var client = builder.build();
        System.out.println("[DDB] region=" + aws.getRegion() + " endpoint=" +
                (StringUtils.hasText(aws.getEndpoint()) ? normalizeEndpoint(aws.getEndpoint()) : "(aws)"));
        return client;
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient low) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(low).build();
    }

    private static URI normalizeEndpoint(String raw) {
        if (!StringUtils.hasText(raw)) return null;

        String url = raw.matches("^[a-zA-Z]+://.*") ? raw : "http://" + raw;

        boolean inContainer = new File("/.dockerenv").exists();
        if (url.startsWith("http://localstack") && !inContainer) {
            url = url.replaceFirst("http://localstack", "http://localhost");
        }

        if (url.matches("^http://(localhost|localstack)(/.*)?$")) {
            url = url.replaceFirst("^(http://[^/:]+)", "$1:4566");
        }

        return URI.create(url);
    }
}
