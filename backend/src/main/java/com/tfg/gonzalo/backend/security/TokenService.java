package com.tfg.gonzalo.backend.security;

import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.EventGroup;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String EVENT_ID_CLAIM = "eventId";
    private final String jwtSecret;
    private final long expirationMinutes;

    public TokenService(@Value("${app.security.jwt.secret}") String jwtSecret,
                        @Value("${app.security.jwt.expiration-minutes}") long expirationMinutes) {
        this.jwtSecret = jwtSecret;
        this.expirationMinutes = expirationMinutes;
    }

    public String createOrganizerToken(AppUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationMinutes * 60L);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getUsername());
        payload.put("uid", user.getId());
        payload.put("role", "ORGANIZER");
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", exp.getEpochSecond());
        return createToken(payload);
    }

    public String createRepresentativeToken(EventGroup group) {
        return createGroupToken(group, "GROUP_REPRESENTATIVE", "REPRESENTATIVE");
    }

    public String createObserverToken(EventGroup group) {
        return createGroupToken(group, "GROUP_OBSERVER", "OBSERVER");
    }

    public AuthPrincipal parseAndValidate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BusinessRuleException("Token inválido.");
            }

            String signed = parts[0] + "." + parts[1];
            String expectedSignature = sign(signed);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new BusinessRuleException("Firma de token inválida.");
            }

            byte[] payloadBytes = URL_DECODER.decode(parts[1]);
            Map<String, String> payload = deserializePayload(new String(payloadBytes, StandardCharsets.UTF_8));
            long exp = Long.parseLong(payload.get("exp"));
            if (Instant.now().getEpochSecond() >= exp) {
                throw new BusinessRuleException("Token expirado.");
            }

            return new AuthPrincipal(
                    asLong(payload.get("uid")),
                    asString(payload.get("sub")),
                    asString(payload.get("role")),
                    asLong(payload.get(EVENT_ID_CLAIM)),
                    asLong(payload.get("groupId")),
                    asString(payload.get("codeKind"))
            );
        } catch (BusinessRuleException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessRuleException("No se ha podido validar el token.");
        }
    }

    public long getExpirationSeconds() {
        return expirationMinutes * 60L;
    }

    private String createGroupToken(EventGroup group, String role, String codeKind) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationMinutes * 60L);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", role + ":" + group.getId());
        payload.put("role", role);
        payload.put(EVENT_ID_CLAIM, group.getEvent().getId());
        payload.put("groupId", group.getId());
        payload.put("codeKind", codeKind);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", exp.getEpochSecond());
        return createToken(payload);
    }

    private String createToken(Map<String, Object> payload) {
        try {
            String headerJson = "alg=HS256&typ=JWT";
            String payloadJson = serializePayload(payload);
            String encodedHeader = URL_ENCODER.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = URL_ENCODER.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signed = encodedHeader + "." + encodedPayload;
            String signature = sign(signed);
            return signed + "." + signature;
        } catch (Exception exception) {
            throw new BusinessRuleException("No se ha podido generar el token de acceso.");
        }
    }

    private String sign(String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signatureBytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(signatureBytes);
        } catch (Exception exception) {
            throw new BusinessRuleException("No se ha podido firmar el token.");
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String serializePayload(Map<String, Object> payload) {
        return payload.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + safeValue(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static Map<String, String> deserializePayload(String payload) {
        Map<String, String> values = new LinkedHashMap<>();
        if (payload == null || payload.isBlank()) {
            return values;
        }
        String[] pairs = payload.split("&");
        for (String pair : pairs) {
            String[] segments = pair.split("=", 2);
            String key = segments[0];
            String value = segments.length > 1 ? segments[1] : "";
            values.put(key, value);
        }
        return values;
    }

    private static String safeValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).replace("&", "%26").replace("=", "%3D");
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
