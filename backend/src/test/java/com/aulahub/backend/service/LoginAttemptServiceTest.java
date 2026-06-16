package com.aulahub.backend.service;

import com.aulahub.backend.exception.DemasiadosIntentosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Reglas de rate limiting por IP: 5 intentos fallidos de login y 3 solicitudes
// de recuperacion de password por ventana. El servicio es puro (sin Spring ni DB).
class LoginAttemptServiceTest {

    private static final String IP = "10.0.0.1";

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void permiteHastaCincoFallosDeLoginYBloqueaElSexto() {
        for (int i = 0; i < 5; i++) {
            service.verificarBloqueo(IP);
            service.registrarFallo(IP);
        }
        assertThatThrownBy(() -> service.verificarBloqueo(IP))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void loginExitosoLimpiaLosFallosAcumulados() {
        for (int i = 0; i < 5; i++) service.registrarFallo(IP);

        service.limpiarIntentos(IP);

        assertThatCode(() -> service.verificarBloqueo(IP)).doesNotThrowAnyException();
    }

    @Test
    void elBloqueoEsPorIpNoGlobal() {
        for (int i = 0; i < 5; i++) service.registrarFallo(IP);

        assertThatCode(() -> service.verificarBloqueo("10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void recuperacionPermiteTresSolicitudesYBloqueaLaCuarta() {
        for (int i = 0; i < 3; i++) {
            service.verificarYRegistrarRecuperacion(IP);
        }
        assertThatThrownBy(() -> service.verificarYRegistrarRecuperacion(IP))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void elLimiteDeRecuperacionEsIndependienteDelDeLogin() {
        for (int i = 0; i < 3; i++) service.verificarYRegistrarRecuperacion(IP);

        // La IP agoto las recuperaciones pero el login sigue libre
        assertThatCode(() -> service.verificarBloqueo(IP)).doesNotThrowAnyException();
    }
}
