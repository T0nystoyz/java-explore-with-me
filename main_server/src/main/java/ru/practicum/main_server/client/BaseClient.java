package ru.practicum.main_server.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
public class BaseClient {
    protected final RestTemplate rest;

    public BaseClient(RestTemplate rest) {
        this.rest = rest;
    }

    private static ResponseEntity<Object> prepareResponse(ResponseEntity<Object> response) {
        if (response.getStatusCode().is2xxSuccessful()) {
            return response;
        }
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(response.getStatusCode());
        if (response.hasBody()) {
            return responseBuilder.body(response.getBody());
        }
        log.info(":::::BaseClient prepareResponse-> response: {}", responseBuilder.build());
        return responseBuilder.build();
    }

    protected ResponseEntity<Object> get(String path, @Nullable Map<String, Object> parameters) {
        log.info(":::::BaseClient get-> path: {}, param: {}", path, parameters);
        return makeAndSendRequest(HttpMethod.GET, path, parameters, null);
    }

    protected <T> void post(String path, T body) {
        makeAndSendRequest(HttpMethod.POST, path, null, body);
    }

    private <T> ResponseEntity<Object> makeAndSendRequest(HttpMethod method, String path,
                                                          @Nullable Map<String, Object> parameters, @Nullable T body) {
        HttpEntity<T> requestEntity = new HttpEntity<>(body, defaultHeaders());
        log.info(":::::BaseClient makeAndSendRequest-> path: {}, param: {}", path, parameters);
        ResponseEntity<Object> responseEntity;
        try {
            responseEntity = (parameters != null) ? rest.exchange(path, method, requestEntity, Object.class, parameters)
                    : rest.exchange(path, method, requestEntity, Object.class);

        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsByteArray());
        }
        return prepareResponse(responseEntity);
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }
}
