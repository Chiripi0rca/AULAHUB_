package com.aulahub.backend.service;

import com.aulahub.backend.dto.request.ForgotPasswordRequestDTO;
import com.aulahub.backend.dto.request.LoginRequestDTO;
import com.aulahub.backend.dto.request.RefreshTokenRequestDTO;
import com.aulahub.backend.dto.request.ResetPasswordRequestDTO;
import com.aulahub.backend.dto.response.AuthResponseDTO;
import com.aulahub.backend.exception.CrendencialesInvalidasException;
import com.aulahub.backend.exception.DemasiadosIntentosException;
import com.aulahub.backend.model.PasswordResetTokenEntity;
import com.aulahub.backend.model.RefreshTokenEntity;
import com.aulahub.backend.model.RolEntity;
import com.aulahub.backend.model.UserEntity;
import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.repository.AulaRepository;
import com.aulahub.backend.repository.FacultadRepository;
import com.aulahub.backend.repository.PasswordResetTokenRepository;
import com.aulahub.backend.repository.RefreshTokenRepository;
import com.aulahub.backend.repository.RolRepository;
import com.aulahub.backend.repository.UserRepository;
import com.aulahub.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Autenticacion: login multi-dispositivo (no tumbar las sesiones previas),
// tope de sesiones, rate limit de login y de recuperacion, refresh y reset.
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "profe@uas.edu.mx";
    private static final String IP = "10.0.0.1";

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private RolRepository rolRepository;
    @Mock private FacultadRepository facultadRepository;
    @Mock private AulaRepository aulaRepository;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private ActividadLogService actividadLogService;
    @Mock private LoginAttemptService loginAttemptService;

    @InjectMocks private AuthService authService;

    @Captor private ArgumentCaptor<List<RefreshTokenEntity>> tokensBorradosCaptor;

    // ── helpers ──────────────────────────────────────────────────────────────

    private UserEntity usuarioActivo() {
        return UserEntity.builder()
                .id(7L).email(EMAIL).nombre("Profe Prueba")
                .passwordHash("$hash$").activo(true)
                .build();
    }

    private LoginRequestDTO loginRequest() {
        LoginRequestDTO r = new LoginRequestDTO();
        r.setEmail(EMAIL);
        r.setPassword("secreta123");
        return r;
    }

    private RefreshTokenEntity refreshToken(String valor, LocalDateTime expira) {
        return RefreshTokenEntity.builder()
                .token(valor).user(usuarioActivo()).expiryDate(expira)
                .build();
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    void loginExitosoDevuelveTokensYNoBorraLasSesionesPrevias() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioActivo()));
        when(refreshTokenRepository.findByUser_IdOrderByExpiryDateDesc(7L)).thenReturn(List.of());
        when(jwtUtil.generateToken(EMAIL)).thenReturn("access-jwt");
        when(rolRepository.findByUsuarioId(7L))
                .thenReturn(List.of(RolEntity.builder().tipo(TipoRol.PROFESOR).build()));

        AuthResponseDTO res = authService.login(loginRequest(), IP);

        assertThat(res.getToken()).isEqualTo("access-jwt");
        assertThat(res.getRefreshToken()).isNotBlank();
        assertThat(res.getRoles()).containsExactly("PROFESOR");
        // Multi-dispositivo: el login NO debe invalidar los refresh tokens previos
        verify(refreshTokenRepository, never()).deleteByUser_Id(any());
        verify(refreshTokenRepository).save(any(RefreshTokenEntity.class));
        verify(loginAttemptService).limpiarIntentos(IP);
    }

    @Test
    void loginConElTopeDeSesionesRecortaLasMasViejas() {
        List<RefreshTokenEntity> cinco = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // ordenados como los devuelve el repo: el mas nuevo primero
            cinco.add(refreshToken("t" + i, LocalDateTime.now().plusDays(7).minusDays(i)));
        }
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioActivo()));
        when(refreshTokenRepository.findByUser_IdOrderByExpiryDateDesc(7L)).thenReturn(cinco);
        when(jwtUtil.generateToken(EMAIL)).thenReturn("access-jwt");
        when(rolRepository.findByUsuarioId(7L)).thenReturn(List.of());

        authService.login(loginRequest(), IP);

        // Con 5 sesiones y una nueva entrando, se borra solo la mas vieja
        verify(refreshTokenRepository).deleteAll(tokensBorradosCaptor.capture());
        assertThat(tokensBorradosCaptor.getValue()).hasSize(1);
        assertThat(tokensBorradosCaptor.getValue().get(0).getToken()).isEqualTo("t4");
    }

    @Test
    void loginConCredencialesMalasRegistraElFalloYNoCreaSesion() {
        doThrow(new BadCredentialsException("bad"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(loginRequest(), IP))
                .isInstanceOf(CrendencialesInvalidasException.class);

        verify(loginAttemptService).registrarFallo(IP);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void loginConCuentaDesactivadaEsRechazado() {
        UserEntity inactivo = usuarioActivo();
        inactivo.setActivo(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> authService.login(loginRequest(), IP))
                .isInstanceOf(CrendencialesInvalidasException.class);
    }

    @Test
    void loginConIpBloqueadaNiSiquieraIntentaAutenticar() {
        doThrow(new DemasiadosIntentosException("bloqueada"))
                .when(loginAttemptService).verificarBloqueo(IP);

        assertThatThrownBy(() -> authService.login(loginRequest(), IP))
                .isInstanceOf(DemasiadosIntentosException.class);

        verifyNoInteractions(authenticationManager);
    }

    // ── refresh ──────────────────────────────────────────────────────────────

    @Test
    void refreshValidoEmiteNuevoAccessTokenYConservaElRefresh() {
        RefreshTokenEntity vigente = refreshToken("rt-1", LocalDateTime.now().plusDays(3));
        when(refreshTokenRepository.findByToken("rt-1")).thenReturn(Optional.of(vigente));
        when(jwtUtil.generateToken(EMAIL)).thenReturn("access-nuevo");
        when(rolRepository.findByUsuarioId(7L)).thenReturn(List.of());

        RefreshTokenRequestDTO req = new RefreshTokenRequestDTO();
        req.setRefreshToken("rt-1");
        AuthResponseDTO res = authService.refresh(req);

        assertThat(res.getToken()).isEqualTo("access-nuevo");
        assertThat(res.getRefreshToken()).isEqualTo("rt-1");
    }

    @Test
    void refreshExpiradoSeEliminaYExigeNuevoLogin() {
        RefreshTokenEntity vencido = refreshToken("rt-viejo", LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByToken("rt-viejo")).thenReturn(Optional.of(vencido));

        RefreshTokenRequestDTO req = new RefreshTokenRequestDTO();
        req.setRefreshToken("rt-viejo");

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(CrendencialesInvalidasException.class);
        verify(refreshTokenRepository).delete(vencido);
    }

    // ── recuperacion de password ─────────────────────────────────────────────

    @Test
    void recuperacionAplicaElRateLimitAunqueElCorreoNoExista() {
        // Anti-enumeracion: el limite cuenta TODAS las solicitudes por igual
        when(userRepository.findByEmail("no@existe.mx")).thenReturn(Optional.empty());

        ForgotPasswordRequestDTO req = new ForgotPasswordRequestDTO();
        req.setEmail("no@existe.mx");
        authService.enviarEmailRecuperacion(req, IP);

        verify(loginAttemptService).verificarYRegistrarRecuperacion(IP);
        verifyNoInteractions(emailService);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void recuperacionConCorreoExistenteGeneraTokenYMandaEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioActivo()));

        ForgotPasswordRequestDTO req = new ForgotPasswordRequestDTO();
        req.setEmail(EMAIL);
        authService.enviarEmailRecuperacion(req, IP);

        verify(passwordResetTokenRepository).save(any(PasswordResetTokenEntity.class));
        verify(emailService).enviarEmailRecuperacion(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void resetPasswordConTokenYaUsadoEsRechazado() {
        PasswordResetTokenEntity usado = PasswordResetTokenEntity.builder()
                .token("token-x").user(usuarioActivo())
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .usado(true)
                .build();
        when(passwordResetTokenRepository.findByToken("token-x")).thenReturn(Optional.of(usado));

        ResetPasswordRequestDTO req = new ResetPasswordRequestDTO();
        req.setToken("token-x");
        req.setNuevaPassword("NuevaSegura123");

        assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOf(CrendencialesInvalidasException.class);
        verify(userRepository, never()).save(any());
    }
}
