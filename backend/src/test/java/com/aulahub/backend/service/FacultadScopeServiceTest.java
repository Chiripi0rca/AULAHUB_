package com.aulahub.backend.service;

import com.aulahub.backend.exception.OperacionNoPermitidaException;
import com.aulahub.backend.model.FacultadEntity;
import com.aulahub.backend.model.RolEntity;
import com.aulahub.backend.model.UserEntity;
import com.aulahub.backend.model.enums.TipoRol;
import com.aulahub.backend.repository.RolRepository;
import com.aulahub.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// RLS por facultad: el SUPER_ADMIN solo ve y gestiona su propia facultad.
// Este componente esta centralizado y lo usan Aula/Materia/Grupo/ReservaService.
@ExtendWith(MockitoExtension.class)
class FacultadScopeServiceTest {

    private static final String EMAIL = "superadmin@uas.edu.mx";

    @Mock private UserRepository userRepository;
    @Mock private RolRepository rolRepository;

    @InjectMocks private FacultadScopeService service;

    @BeforeEach
    void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(EMAIL, null, List.of()));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void usuarioConRoles(RolEntity... roles) {
        UserEntity user = UserEntity.builder().id(7L).email(EMAIL).nombre("Usuario").activo(true).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(rolRepository.findByUsuarioId(7L)).thenReturn(List.of(roles));
    }

    private RolEntity rolSuperAdmin(long facultadId) {
        return RolEntity.builder()
                .tipo(TipoRol.SUPER_ADMIN)
                .facultad(FacultadEntity.builder().id(facultadId).nombre("FIC").build())
                .build();
    }

    @Test
    void usuarioSinRolSuperAdminNoTieneScopeDeFacultad() {
        usuarioConRoles(RolEntity.builder().tipo(TipoRol.PROFESOR).build());

        assertThat(service.getFacultadIdSuperAdmin()).isNull();
    }

    @Test
    void superAdminObtieneSuFacultad() {
        usuarioConRoles(rolSuperAdmin(1L));

        assertThat(service.getFacultadIdSuperAdmin()).isEqualTo(1L);
    }

    @Test
    void superAdminPuedeGestionarSuPropiaFacultad() {
        usuarioConRoles(rolSuperAdmin(1L));

        assertThatCode(() -> service.validarEscopePropia(1L)).doesNotThrowAnyException();
    }

    @Test
    void superAdminNoPuedeGestionarOtraFacultad() {
        usuarioConRoles(rolSuperAdmin(1L));

        assertThatThrownBy(() -> service.validarEscopePropia(2L))
                .isInstanceOf(OperacionNoPermitidaException.class);
    }

    @Test
    void adminCentralNoTieneRestriccionDeFacultad() {
        // ADMIN_CENTRAL no tiene facultad asignada (rol global)
        usuarioConRoles(RolEntity.builder().tipo(TipoRol.ADMIN_CENTRAL).build());

        assertThat(service.getFacultadIdSuperAdmin()).isNull();
        assertThatCode(() -> service.validarEscopePropia(99L)).doesNotThrowAnyException();
    }
}
