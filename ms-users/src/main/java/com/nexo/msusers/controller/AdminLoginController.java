package com.nexo.msusers.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminLoginController {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginController.class);
    private static final String MENSAJE_CREDENCIALES = "Email o contraseña incorrectos";

    @Value("${cognito.user-pool-id}")
    private String userPoolId;

    @Value("${cognito.client-id}")
    private String clientId;

    private final CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
            .region(Region.US_EAST_1)
            .build();

    public record LoginRequest(String email, String password) {}

    public record LoginResponse(String accessToken, String idToken, String refreshToken) {}

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            String username = resolverUsername(request.email());

            AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                    .userPoolId(userPoolId)
                    .clientId(clientId)
                    .authParameters(Map.of(
                            "USERNAME", username,
                            "PASSWORD", request.password()
                    ))
                    .build();

            AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(authRequest);

            var result = response.authenticationResult();
            if (result == null) {
                // Cognito pidió un paso extra (cambio de contraseña, MFA): este login no lo soporta.
                log.warn("Login de admin: Cognito devolvió un desafío ({}) en lugar de tokens", response.challengeNameAsString());
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, MENSAJE_CREDENCIALES);
            }

            return ResponseEntity.ok(new LoginResponse(
                    result.accessToken(),
                    result.idToken(),
                    result.refreshToken()
            ));

        } catch (CognitoIdentityProviderException e) {
            String detalle = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : e.getClass().getSimpleName();
            log.warn("Login de admin rechazado por Cognito: {}", detalle);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, MENSAJE_CREDENCIALES);
        }
    }

    /**
     * El "username" real en Cognito puede no coincidir con el email, y una misma persona puede tener
     * varios usuarios con el mismo correo:
     * - Usuarios creados manualmente (admin create-user): username = sub o email, tienen contraseña.
     * - Usuarios via proveedor externo (Google): username = "google_<id>", NO tienen contraseña.
     * Este login por contraseña solo sirve para los primeros, así que se ignoran los EXTERNAL_PROVIDER.
     */
    private String resolverUsername(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, MENSAJE_CREDENCIALES);
        }

        String emailSeguro = email.trim().replace("\\", "\\\\").replace("\"", "\\\"");
        ListUsersRequest listRequest = ListUsersRequest.builder()
                .userPoolId(userPoolId)
                .filter("email = \"" + emailSeguro + "\"")
                .build();

        ListUsersResponse listResponse = cognitoClient.listUsers(listRequest);
        List<UserType> usuarios = listResponse.users();

        return usuarios.stream()
                .filter(u -> u.userStatus() != UserStatusType.EXTERNAL_PROVIDER)
                .map(UserType::username)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, MENSAJE_CREDENCIALES));
    }
}