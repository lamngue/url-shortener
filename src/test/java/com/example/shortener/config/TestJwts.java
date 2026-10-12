package com.example.shortener.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Test-only stand-in for the identity provider: signs real JWTs with an RSA key made at startup,
 * and builds a decoder with the same checks as production (SecurityConfig) but with that public
 * key instead of a JWKS URI. Lets tests send genuine signed tokens, including bad ones.
 */
public final class TestJwts {

    public static final JwtProperties PROPERTIES =
            new JwtProperties("http://unused.test/jwks.json", "https://issuer.test", "url-shortener");

    private static final KeyPair KEYS = newRsaKeyPair();
    private static final KeyPair OTHER_KEYS = newRsaKeyPair();

    private TestJwts() {
    }

    /** A decoder like production's: RS256 only, plus SecurityConfig's validators. */
    public static JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(SecurityConfig.tokenValidator(PROPERTIES));
        return decoder;
    }

    /** Valid claims: right issuer and audience, subject "alice", expires in 5 minutes. Adjust with the customizer. */
    public static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(PROPERTIES.issuer())
                .audience(List.of(PROPERTIES.audience()))
                .subject("alice")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    /** RS256 token signed with the trusted key. */
    public static String rs256(Consumer<JWTClaimsSet.Builder> customize) {
        return sign(KEYS, customize);
    }

    /** RS256 token signed with a key the decoder does not trust. */
    public static String rs256WithUntrustedKey(Consumer<JWTClaimsSet.Builder> customize) {
        return sign(OTHER_KEYS, customize);
    }

    /** HS256 token (shared-secret algorithm), which the pinned decoder must refuse. */
    public static String hs256(Consumer<JWTClaimsSet.Builder> customize) {
        try {
            JWTClaimsSet.Builder claims = validClaims();
            customize.accept(claims);
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef".getBytes()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Unsigned token ("alg": "none"), which must always be refused. */
    public static String unsigned(Consumer<JWTClaimsSet.Builder> customize) {
        JWTClaimsSet.Builder claims = validClaims();
        customize.accept(claims);
        return new PlainJWT(claims.build()).serialize();
    }

    private static String sign(KeyPair keys, Consumer<JWTClaimsSet.Builder> customize) {
        try {
            JWTClaimsSet.Builder claims = validClaims();
            customize.accept(claims);
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims.build());
            jwt.sign(new RSASSASigner(keys.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair newRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
