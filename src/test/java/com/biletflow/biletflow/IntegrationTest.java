package com.biletflow.biletflow;

import com.biletflow.biletflow.config.AsyncSyncConfiguration;
import com.biletflow.biletflow.config.DatabaseTestcontainer;
import com.biletflow.biletflow.config.ElasticsearchTestConfiguration;
import com.biletflow.biletflow.config.ElasticsearchTestContainer;
import com.biletflow.biletflow.config.RedisTestContainer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base composite annotation for integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    classes = {
        BiletFlowApp.class,
        AsyncSyncConfiguration.class,
        com.biletflow.biletflow.config.JacksonHibernateConfiguration.class,
        DatabaseTestcontainer.class,
        ElasticsearchTestContainer.class,
        ElasticsearchTestConfiguration.class,
        RedisTestContainer.class,
    }
)
public @interface IntegrationTest {}
