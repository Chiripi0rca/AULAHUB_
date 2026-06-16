package com.aulahub.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

// Emision y validacion de JWT. La clave y la expiracion se inyectan por
// reflexion porque en la app vienen de las properties jwt.secret / jwt.expiration.
class JwtUtilTest {

    private static final String SECRET =
            "clave-de-prueba-solo-para-tests-0123456789-0123456789-0123456789";
    private static final String EMAIL = "profe@uas.edu.mx";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = crearJwtUtil(3_600_000L); // 1 hora
    }

    private JwtUtil crearJwtUtil(long expiracionMs) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secretKey", SECRET);
        ReflectionTestUtils.setField(util, "expiration", expiracionMs);
        return util;
    }

    @Test
    void generaTokenYExtraeElMismoEmail() {
        String token = jwtUtil.generateToken(EMAIL);
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(EMAIL);
    }

    @Test
    void tokenValidoParaSuEmail() {
        String token = jwtUtil.generateToken(EMAIL);
        assertThat(jwtUtil.isTokenValid(token, EMAIL)).isTrue();
    }

    @Test
    void tokenNoEsValidoParaOtroEmail() {
        String token = jwtUtil.generateToken(EMAIL);
        assertThat(jwtUtil.isTokenValid(token, "otro@uas.edu.mx")).isFalse();
    }

    @Test
    void tokenCorruptoNoEsValido() {
        assertThat(jwtUtil.isTokenValid("esto-no-es-un-jwt", EMAIL)).isFalse();
    }

    @Test
    void tokenExpiradoNoEsValido() {
        JwtUtil vencido = crearJwtUtil(-1_000L); // el token nace ya expirado
        String token = vencido.generateToken(EMAIL);
        assertThat(vencido.isTokenValid(token, EMAIL)).isFalse();
    }
}
