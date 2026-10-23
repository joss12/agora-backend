
package com.springcommerce.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class BackendApplicationTests {

	@Container
	@ServiceConnection
	static MySQLContainer mysql =
			new MySQLContainer("mysql:8.4");

	@Autowired
	private org.springframework.context.ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context).isNotNull();
	}
}
