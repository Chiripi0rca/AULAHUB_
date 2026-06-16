package com.aulahub.backend.service;

import com.aulahub.backend.dto.request.CambiarPasswordRequestDTO;
import com.aulahub.backend.dto.request.ForgotPasswordRequestDTO;
import com.aulahub.backend.dto.request.LoginRequestDTO;
import com.aulahub.backend.dto.request.RefreshTokenRequestDTO;
import com.aulahub.backend.dto.request.RegisterRequestDTO;
import com.aulahub.backend.dto.request.ResetPasswordRequestDTO;
import com.aulahub.backend.dto.response.AuthResponseDTO;
import com.aulahub.backend.dto.response.UsuarioResponseDTO;
import com.aulahub.backend.exception.CrendencialesInvalidasException;
import com.aulahub.backend.exception.EmailYaRegistradoException;
import com.aulahub.backend.exception.FacultadNoEncontradaException;
import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.exception.ReservaEstadoInvalidoException;
import com.aulahub.backend.exception.UsuarioNoEncontradoException;
import com.aulahub.backend.model.FacultadEntity;
import com.aulahub.backend.model.PasswordResetTokenEntity;
import com.aulahub.backend.model.RefreshTokenEntity;
import com.aulahub.backend.model.AulaEntity;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RolRepository rolRepository;
    private final FacultadRepository facultadRepository;
    private final AulaRepository aulaRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ActividadLogService actividadLogService;
    private final LoginAttemptService loginAttemptService;

    // Vigencia del refresh token en días. Larga por diseño (estilo apps móviles):
    // mientras el usuario abra la app dentro de esta ventana, no vuelve a iniciar sesión,
    // porque cada refresh la renueva (expiración deslizante, ver refresh()).
    @Value("${jwt.refresh-expiration-days:90}")
    private long refreshExpirationDays;

    // Autentica al usuario, genera accessToken y refreshToken.
    // 'ip' se usa para el control de intentos por fuerza bruta.
    @Transactional
    public AuthResponseDTO login(LoginRequestDTO request, String ip) {
        // Si la IP superó el límite de intentos fallidos, se bloquea temporalmente
        loginAttemptService.verificarBloqueo(ip);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            loginAttemptService.registrarFallo(ip); // cuenta este intento fallido
            throw new CrendencialesInvalidasException("Correo o contraseña incorrectos");
        }

        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CrendencialesInvalidasException("Correo o contraseña incorrectos"));

        if (!user.isActivo()) {
            throw new CrendencialesInvalidasException("Esta cuenta ha sido desactivada");
        }

        // Login exitoso: limpia el historial de intentos de esa IP
        loginAttemptService.limpiarIntentos(ip);

        // Multi-dispositivo: NO se borran los refresh tokens previos (la web y el
        // APK conviven sin tumbarse la sesion entre si). Solo se recortan los mas
        // viejos si el usuario acumula demasiadas sesiones abiertas.
        limitarSesionesPorUsuario(user.getId());

        String accessToken = jwtUtil.generateToken(user.getEmail());

        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(LocalDateTime.now().plusDays(refreshExpirationDays))
                .build();
        refreshTokenRepository.save(refreshToken);

        List<String> roles = rolRepository.findByUsuarioId(user.getId())
                .stream()
                .map(r -> r.getTipo().name())
                .toList();

        return new AuthResponseDTO(
                accessToken,
                refreshToken.getToken(),
                "Bearer",
                user.getId(),
                user.getEmail(),
                user.getNombre(),
                roles,
                user.getFotoUrl()
        );
    }

    // Tope de sesiones abiertas (refresh tokens) por usuario.
    private static final int MAX_SESIONES_POR_USUARIO = 5;

    // Si el usuario ya esta en el tope, elimina los tokens mas proximos a expirar
    // (los mas viejos) para dejar lugar al que se va a crear. Los expirados los
    // limpia ademas el scheduler de las 2am.
    private void limitarSesionesPorUsuario(Long userId) {
        List<RefreshTokenEntity> tokens = refreshTokenRepository.findByUser_IdOrderByExpiryDateDesc(userId);
        if (tokens.size() >= MAX_SESIONES_POR_USUARIO) {
            refreshTokenRepository.deleteAll(tokens.subList(MAX_SESIONES_POR_USUARIO - 1, tokens.size()));
        }
    }

    // Genera nuevo accessToken usando el refreshToken sin pedir contraseña
    @Transactional
    public AuthResponseDTO refresh(RefreshTokenRequestDTO request) {
        RefreshTokenEntity refreshToken = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(() -> new CrendencialesInvalidasException("Token de refresco inválido"));

        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new CrendencialesInvalidasException("El token de refresco ha expirado, inicia sesión de nuevo");
        }

        // Expiración deslizante: cada renovación empuja la fecha de expiración hacia adelante.
        // Así, mientras el usuario siga usando la app dentro de la ventana, su sesión no caduca
        // (comportamiento tipo redes sociales). Si deja de usarla N días, recién ahí expira.
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(refreshExpirationDays));
        refreshTokenRepository.save(refreshToken);

        UserEntity user = refreshToken.getUser();
        String nuevoAccessToken = jwtUtil.generateToken(user.getEmail());

        List<String> roles = rolRepository.findByUsuarioId(user.getId())
                .stream()
                .map(r -> r.getTipo().name())
                .toList();

        return new AuthResponseDTO(
                nuevoAccessToken,
                refreshToken.getToken(),
                "Bearer",
                user.getId(),
                user.getEmail(),
                user.getNombre(),
                roles,
                user.getFotoUrl()
        );
    }

    // Elimina el refreshToken de la DB — cierra sesión de forma segura
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    // Crea cuenta de usuario — ADMIN_CENTRAL puede crear cualquier tipo, SUPER_ADMIN solo PROFESOR y ADMIN
    @Transactional
    public UsuarioResponseDTO registrar(RegisterRequestDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SUPER_ADMIN"));

        if (esSuperAdmin && (request.getTipo() == TipoRol.SUPER_ADMIN || request.getTipo() == TipoRol.ADMIN_CENTRAL)) {
            throw new OperacionNoPermitidaException("Un SUPER_ADMIN solo puede crear cuentas de tipo PROFESOR o ADMIN");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailYaRegistradoException("El correo " + request.getEmail() + " ya está registrado");
        }

        UserEntity user = UserEntity.builder()
                .email(request.getEmail())
                .nombre(request.getNombre() + " " + request.getApellidos())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        UserEntity saved = userRepository.save(user);

        RolEntity.RolEntityBuilder rolBuilder = RolEntity.builder()
                .usuario(saved)
                .tipo(request.getTipo());

        if (request.getTipo() != TipoRol.ADMIN_CENTRAL && request.getFacultadId() == null) {
            throw new OperacionNoPermitidaException("La facultad es obligatoria para el rol " + request.getTipo());
        }

        FacultadEntity facultad = null;
        if (request.getFacultadId() != null) {
            facultad = facultadRepository.findById(request.getFacultadId())
                    .orElseThrow(() -> new FacultadNoEncontradaException("Facultad con id " + request.getFacultadId() + " no encontrada"));
            rolBuilder.facultad(facultad);
        }

        if (request.getTurno() != null) {
            rolBuilder.turno(request.getTurno());
        }

        // Aulas asignadas — solo aplica para ADMIN
        if (request.getTipo() == TipoRol.ADMIN
                && request.getAulaIds() != null && !request.getAulaIds().isEmpty()) {
            List<AulaEntity> aulas = aulaRepository.findAllById(request.getAulaIds());
            rolBuilder.aulas(aulas);
        }

        rolRepository.save(rolBuilder.build());

        String detalleFacultad = facultad != null
                ? " en " + facultad.getNombre() + " (" + facultad.getClave() + ")"
                : "";
        actividadLogService.registrarAccionActual(
                "CREAR_USUARIO", "usuario", saved.getId(),
                "Usuario creado: " + saved.getNombre() + " (" + saved.getEmail() + ")"
                        + " — Rol: " + request.getTipo() + detalleFacultad);

        return new UsuarioResponseDTO(
                saved.getId(),
                saved.getEmail(),
                saved.getNombre(),
                saved.getFotoUrl(),
                saved.isActivo(),
                saved.getCreatedAt()
        );
    }

    // Envía email con token de recuperación válido 30 minutos.
    // Si el correo no existe, responde igual (204) sin revelar nada (anti-enumeración).
    @Transactional
    public void enviarEmailRecuperacion(ForgotPasswordRequestDTO request, String ip) {
        // Limite por IP: se registra TODA solicitud (exista o no el correo) para
        // que el rate-limit tampoco revele si un email esta registrado.
        loginAttemptService.verificarYRegistrarRecuperacion(ip);

        Optional<UserEntity> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) return;

        UserEntity user = userOpt.get();
        passwordResetTokenRepository.deleteByUser_Id(user.getId());

        PasswordResetTokenEntity resetToken = PasswordResetTokenEntity.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(30))
                .build();
        passwordResetTokenRepository.save(resetToken);

        // El envío de email no debe tumbar el endpoint: si el SMTP falla, se registra
        // el error en consola pero el endpoint responde igual (no revela si el email existe).
        try {
            emailService.enviarEmailRecuperacion(user.getEmail(), user.getNombre(), resetToken.getToken());
        } catch (Exception e) {
            log.error("Fallo al enviar email de recuperación a {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    // Valida el token del email y cambia la contraseña
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO request) {
        PasswordResetTokenEntity resetToken = passwordResetTokenRepository
                .findByToken(request.getToken())
                .orElseThrow(() -> new CrendencialesInvalidasException("Token de recuperación inválido"));

        if (resetToken.isUsado()) {
            throw new CrendencialesInvalidasException("Este token ya fue utilizado");
        }

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new CrendencialesInvalidasException("El token de recuperación ha expirado, solicita uno nuevo");
        }

        UserEntity user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNuevaPassword()));
        userRepository.save(user);

        resetToken.setUsado(true);
        passwordResetTokenRepository.save(resetToken);

        refreshTokenRepository.deleteByUser_Id(user.getId());
    }

    // Cambia la contraseña del usuario AUTENTICADO validando su contraseña actual.
    // No usa email — es el flujo directo desde Configuraciones estando dentro de la sesión.
    @Transactional
    public void cambiarPassword(CambiarPasswordRequestDTO request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsuarioNoEncontradoException("Usuario no encontrado"));

        // La contraseña actual debe coincidir con la guardada
        if (!passwordEncoder.matches(request.getPasswordActual(), user.getPasswordHash())) {
            throw new CrendencialesInvalidasException("La contraseña actual es incorrecta");
        }

        // La nueva no puede ser igual a la actual
        if (passwordEncoder.matches(request.getNuevaPassword(), user.getPasswordHash())) {
            throw new OperacionNoPermitidaException("La nueva contraseña debe ser diferente a la actual");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNuevaPassword()));
        userRepository.save(user);

        // Invalida los refresh tokens existentes — fuerza re-login en otros dispositivos por seguridad
        refreshTokenRepository.deleteByUser_Id(user.getId());
    }
}
