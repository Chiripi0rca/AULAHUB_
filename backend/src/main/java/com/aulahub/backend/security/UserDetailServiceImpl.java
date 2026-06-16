package com.aulahub.backend.security;

import com.aulahub.backend.model.UserEntity;
import com.aulahub.backend.repository.RolRepository;
import com.aulahub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final RolRepository rolRepository;

    // Spring Security llama este método al autenticar; recibe el email como username
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        // Carga los roles del usuario y los convierte en authorities para Spring Security
        List<GrantedAuthority> authorities = rolRepository.findByUsuarioId(user.getId())
                .stream()
                .map(rol -> new SimpleGrantedAuthority(rol.getTipo().name()))
                .collect(Collectors.toList());

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .build();
    }
}
