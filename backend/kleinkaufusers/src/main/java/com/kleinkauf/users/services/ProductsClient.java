package com.kleinkauf.users.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.kleinkauf.common.constants.Headers;
import com.kleinkauf.common.security.InternalTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/*
Calls to products. Today only the store name (its public settings), used as the sender name of the emails.
If products does not answer it returns null and the caller uses the name derived from the identifier.
 */
@Slf4j
@Component
public class ProductsClient {

    private static final String SERVICE_NAME = "users";

    @Value("${products.url:http://products-microservice/}")
    private String productsUrl;

    @Autowired
    private InternalTokenService internalTokenService;

    @Autowired
    private RestTemplate restTemplate;

    public String getStoreName(String tenant) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(Headers.INTERNAL_REQUEST, Boolean.toString(true));
        headers.set(Headers.INTERNAL_TOKEN, internalTokenService.generateMicroServiceToken(SERVICE_NAME));
        headers.set(Headers.FORWARDED_HOST, tenant);
        try {
            JsonNode store = restTemplate.exchange(
                    productsUrl + "api/v1/catalog/store",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class
            ).getBody();
            String name = store == null ? null : store.path("name").asText(null);
            return name == null || name.isBlank() ? null : name.trim();
        } catch (RuntimeException e) {
            log.warn("No se pudo obtener el nombre de la tienda {}: {}", tenant, e.toString());
            return null;
        }
    }
}
