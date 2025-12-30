package com.nimbly.phshoesbackend.alerts.scheduler.web.config;

import com.nimbly.phshoesbackend.commons.core.repository.SuppressionRepository;
import com.nimbly.phshoesbackend.commons.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.commons.core.security.jwt.JwtSecurityProperties;
import com.nimbly.phshoesbackend.notification.core.model.props.NotificationEmailProps;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.repository.dynamo.DynamoDbAccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.service.impl.SuppressionServiceImpl;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeTokenCodec;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.impl.HmacUnsubscribeTokenCodec;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.impl.UnsubscribeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SchedulerAccountConfigTest {

    private final SchedulerAccountConfig config = new SchedulerAccountConfig();

    @Test
    void accountRepository_returnsDynamoRepository() {
        // Arrange
        DynamoDbClient dynamoDbClient = mock(DynamoDbClient.class);

        // Act
        AccountRepository repository = config.accountRepository(dynamoDbClient);

        // Assert
        assertNotNull(repository);
        assertTrue(repository instanceof DynamoDbAccountRepository);
    }

    @Test
    void suppressionService_returnsSuppressionServiceImpl() {
        // Arrange
        SuppressionRepository suppressionRepository = mock(SuppressionRepository.class);
        EmailCrypto emailCrypto = mock(EmailCrypto.class);

        // Act
        SuppressionService service = config.suppressionService(suppressionRepository, emailCrypto);

        // Assert
        assertNotNull(service);
        assertTrue(service instanceof SuppressionServiceImpl);
    }

    @Test
    void unsubscribeTokenCodec_returnsHmacCodec() {
        // Arrange
        AppVerificationProps verificationProps = mock(AppVerificationProps.class);
        JwtSecurityProperties jwtSecurityProperties = mock(JwtSecurityProperties.class);
        EmailCrypto emailCrypto = mock(EmailCrypto.class);

        // Act
        UnsubscribeTokenCodec codec = config.unsubscribeTokenCodec(
                verificationProps,
                jwtSecurityProperties,
                emailCrypto
        );

        // Assert
        assertNotNull(codec);
        assertTrue(codec instanceof HmacUnsubscribeTokenCodec);
    }

    @Test
    void unsubscribeService_returnsUnsubscribeServiceImpl() {
        // Arrange
        UnsubscribeTokenCodec codec = mock(UnsubscribeTokenCodec.class);
        SuppressionService suppressionService = mock(SuppressionService.class);
        NotificationEmailProps emailProps = new NotificationEmailProps();
        AppVerificationProps verificationProps = mock(AppVerificationProps.class);

        // Act
        UnsubscribeService service = config.unsubscribeService(
                codec,
                suppressionService,
                emailProps,
                verificationProps
        );

        // Assert
        assertNotNull(service);
        assertTrue(service instanceof UnsubscribeServiceImpl);
    }

    @Test
    void schedulerAccountConfig_enablesExpectedProperties() {
        // Arrange
        EnableConfigurationProperties annotation = SchedulerAccountConfig.class
                .getAnnotation(EnableConfigurationProperties.class);

        // Act
        List<Class<?>> configured = annotation == null ? List.of() : List.of(annotation.value());

        // Assert
        assertTrue(configured.contains(AppVerificationProps.class));
        assertTrue(configured.contains(JwtSecurityProperties.class));
    }
}
