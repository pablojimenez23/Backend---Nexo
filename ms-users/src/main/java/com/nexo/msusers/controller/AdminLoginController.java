package com.nexo.msusers.controller;

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
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminLoginController {

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

            return ResponseEntity.ok(new LoginResponse(
                    result.accessToken(),
                    result.idToken(),
                    result.refreshToken()
            ));

        } catch (CognitoIdentityProviderException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos");
        }
    }

    /**
     * El "username" real en Cognito puede no coincidir con el email:
     * - Usuarios creados manualmente (admin create-user): username = email
     * - Usuarios via proveedor externo (Google): username = "google_<id>"
     * Este método busca el usuario por email y devuelve su username real de Cognito.
     */
    private String resolverUsername(String email) {
        ListUsersRequest listRequest = ListUsersRequest.builder()
                .userPoolId(userPoolId)
                .filter("email = \"" + email + "\"")
                .limit(1)
                .build();

        ListUsersResponse listResponse = cognitoClient.listUsers(listRequest);
        List<UserType> usuarios = listResponse.users();

        if (usuarios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos");
        }

        return usuarios.get(0).username();
    }
}