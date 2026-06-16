package com.aulahub.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // activa los @Scheduled — necesario para el rechazo automático de reservas vencidas y eliminar los refresh tokens que ya estan expirados
@EnableAsync // permite @Async: el envio de correos corre en segundo plano y no bloquea la peticion (reservas, etc.)
public class AulahubBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AulahubBackendApplication.class, args);
	}

}
