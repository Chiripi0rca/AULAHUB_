package com.aulahub.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailServiceImpl userDetailService;

    @Override protected void doFilterInternal(HttpServletRequest request,
                                              HttpServletResponse response,
                                              FilterChain filterChain) throws ServletException, IOException{
        String autherHeader = request.getHeader("Authorization");// extraemos las credenciales del usuario

        //verificamos si el token del usuario esta verificado con el rango correcto
        if (autherHeader == null || !autherHeader.startsWith("Bearer ")){
            //primero preguntamos si la peticion trajo el header "Authorization
            //despues pregunata si el header no empieza por Baerer
            filterChain.doFilter(request,response); //si se cumple las condiciones quiere decir que es un usuario no verificado
            // y lo mandamos al siguiente capa de seguridad
            return;
        }

        // Procesa el token dentro de try-catch: si viene malformado, expirado o con
        // firma inválida, NO autenticamos y dejamos que Spring responda 401/403 limpio.
        // Sin esto, un token basura lanzaría una excepción no controlada (error 500).
        try {
            String token = autherHeader.substring(7);
            String email = jwtUtil.extractEmail(token);

            // Solo autentica si aún no hay autenticación en el contexto y el token es válido
            if (email != null
                    && SecurityContextHolder.getContext().getAuthentication() == null
                    && jwtUtil.isTokenValid(token, email)) {

                UserDetails userDetails = userDetailService.loadUserByUsername(email); // datos del usuario desde la DB

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                ); // objeto de autenticación con datos y permisos

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            // Token presente pero inválido o expirado: cortamos aquí con 401 (no 403).
            // Así el frontend distingue "sesión caída" (401 → intenta refrescar y, si no
            // puede, avisa y manda al login) de "sin permiso para este recurso" (403, que
            // ocurre cuando el token SÍ es válido pero el rol no alcanza).
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Token inválido o expirado\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
