package com.aulahub.backend.config;

import com.aulahub.backend.security.JwtFilter;
import com.aulahub.backend.security.UserDetailServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final UserDetailServiceImpl userDetailsService; // se inyecta esta clase para poder buscar a los usuarios para poder autenticarlos
    private final JwtFilter jwtAuthFilter; // se inyecta esta clase para verificar que usarios teiene permite que endponits

    // Orígenes permitidos por CORS — vienen de la property cors.allowed-origins (coma-separada).
    // En dev por defecto es "*"; en prod se restringe al dominio real.
    @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:*}")
    private String allowedOrigins;


    //con @bean se le dice a spring que este metodo produce un objeto que el debe administrar e inyectar donde sea
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();//este es un algoritmo para encriptar las contrasenas
    }



    /*lo que hace "DaoAuthenticationProvider" es la implementacion de Spring que autentica
      usuarios desde la DB, le decimos como buscarlo pasando el "userDetailsService" y
      con "passwordEncoder" como verificar la contrasena
     */
    @Bean
    public AuthenticationProvider authenticationProvider (){
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider((UserDetailsService) userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable) //aqui desactiva la proteccion CSRF ya que con JWT no se necesita
                .authorizeHttpRequests(auth -> auth
                        // Preflight CORS (OPTIONS) — siempre permitido, necesario para Live Server / front en otro origen
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Endpoints públicos — no requieren token
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
                        // Swagger / OpenAPI — solo existe en dev (en prod springdoc esta
                        // deshabilitado por properties y estas rutas devuelven 404)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Health check para monitoreo (systemd / UptimeRobot): solo devuelve UP/DOWN
                        .requestMatchers(HttpMethod.GET, "/api/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/usuarios").hasAnyAuthority("SUPER_ADMIN", "ADMIN_CENTRAL")
                        // Todo lo demás requiere autenticación (incluyendo /api/auth/registrar y /api/auth/logout)
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)//aqui le decimos a Spring que no aguarde sesiones en el servidor
                )
                .authenticationProvider(authenticationProvider())// aqui se registra el provider
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);//aqui se registra el JwtAuthFilter para que se ejecuten antes
        // del filtro de autentitcacion por defecto de Spring
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(){
        CorsConfiguration config = new CorsConfiguration();
        // setAllowedOriginPatterns permite usar "*" junto con allowCredentials = true
        // setAllowedOrigins("*") + allowCredentials = true lanza IllegalArgumentException en arranque.
        // Los orígenes vienen de la property cors.allowed-origins (coma-separada).
        List<String> origenes = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
        config.setAllowedOriginPatterns(origenes.isEmpty() ? List.of("*") : origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

