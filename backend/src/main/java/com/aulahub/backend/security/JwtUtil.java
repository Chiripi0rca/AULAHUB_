package com.aulahub.backend.security;


import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtUtil {

    // clave secreta para firmar y validad el JWT
    @Value("${jwt.secret}")
    private String secretKey;

    // este es el tiempo de expiracion del token
    @Value("${jwt.expiration}")
    private Long expiration;

    /*
      aqui se genera la firma usando secretkey
      hmacShaKeyFor que crea un clave compatible en HS256
     */
    private SecretKey getSignKey(){
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    /*
    aqui se genera un JWT
      - subject -> email del usuario
      - fecha de creacion
      - fecha de expiracion
      - firma digital
     */

    public String generateToken(String email){
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey())
                .compact();
    }

    /*
    se extrae el email aguardo que esta dentro del token

    parseSignedClaims(token) esto valida que sea correcto
    y este firamdo
     */

    public String extractEmail(String token){
        return Jwts.parser()
                .verifyWith((SecretKey) getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /*
    aqui se valida el token
    la primera es que email coincida con el que esta dentro de toklen
    y la segfunda que no este expirado
     */
    public boolean isTokenValid (String token, String email){
        try{
            return extractEmail(token).equals(email) && !isTokenExpired(token);

        } catch (Exception e){
            return false;
        }
    }

    /*
    verifica que el token no este expirado
     */
    public boolean isTokenExpired (String token){
        return Jwts.parser()
                .verifyWith((SecretKey) getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .before(new Date());
    }

}
