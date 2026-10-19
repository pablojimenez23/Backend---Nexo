package com.nexo.msusers.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class CognitoUserInfoClient {

    private final RestClient restClient =
        RestClient.create(System.getenv().getOrDefault("COGNITO_DOMAIN", "https://us-east-1mipjia45q.auth.us-east-1.amazoncognito.com"));

    public Map<String, Object> obtenerUserInfo(String accessToken) {
        return restClient.get()
                .uri("/oauth2/userInfo")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}