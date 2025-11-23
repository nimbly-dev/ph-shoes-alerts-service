package com.nimbly.phshoesbackend.alerts.scheduler.config;

import com.nimbly.phshoesbackend.services.common.core.repository.SuppressionRepository;
import com.nimbly.phshoesbackend.services.common.core.security.EmailCrypto;
import com.nimbly.phshoesbackend.useraccount.core.repository.AccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.repository.dynamo.DynamoDbAccountRepository;
import com.nimbly.phshoesbackend.useraccount.core.service.SuppressionService;
import com.nimbly.phshoesbackend.useraccount.core.service.impl.SuppressionServiceImpl;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppVerificationProps;
import com.nimbly.phshoesbackend.useraccount.core.config.props.AppAuthProps;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeService;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.UnsubscribeTokenCodec;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.impl.HmacUnsubscribeTokenCodec;
import com.nimbly.phshoesbackend.useraccount.core.unsubscribe.impl.UnsubscribeServiceImpl;
import com.nimbly.phshoesbackend.notification.core.model.props.NotificationEmailProps;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
@EnableConfigurationProperties({AppVerificationProps.class, AppAuthProps.class})
public class SchedulerAccountConfig {

    @Bean
    public AccountRepository accountRepository(DynamoDbClient dynamoDbClient) {
        return new DynamoDbAccountRepository(dynamoDbClient);
    }

    @Bean
    public SuppressionService suppressionService(SuppressionRepository suppressionRepository,
                                                 EmailCrypto emailCrypto) {
        return new SuppressionServiceImpl(suppressionRepository, emailCrypto);
    }

    @Bean
    public UnsubscribeTokenCodec unsubscribeTokenCodec(AppVerificationProps verificationProps,
                                                       AppAuthProps authProps,
                                                       EmailCrypto emailCrypto) {
        return new HmacUnsubscribeTokenCodec(verificationProps, authProps, emailCrypto);
    }

    @Bean
    public UnsubscribeService unsubscribeService(UnsubscribeTokenCodec codec,
                                                 SuppressionService suppressionService,
                                                 NotificationEmailProps emailProps,
                                                 AppVerificationProps verificationProps) {
        return new UnsubscribeServiceImpl(codec, suppressionService, emailProps, verificationProps);
    }
}
