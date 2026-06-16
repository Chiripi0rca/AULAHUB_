package com.aulahub.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Prueba de humo: levanta el contexto completo de Spring (beans, seguridad,
// Flyway contra la base de datos local). Requiere MySQL corriendo, igual que
// el desarrollo normal. En CI la base la provee un contenedor de MySQL.
@SpringBootTest
@ActiveProfiles({"dev", "local"})
class AulahubBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
