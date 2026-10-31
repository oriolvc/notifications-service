package com.oriolvc.notificationsservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base class for integration tests. The Postgres container is a Spring bean, so it is shared by every test class that
 * reuses the same (cached) application context.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {}
