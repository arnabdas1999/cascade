package com.cascade.domain.step;

import com.cascade.common.StepException;
import com.cascade.domain.model.ExecutionContext;
import com.cascade.domain.retry.NoRetry;
import com.cascade.domain.retry.RetryPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Makes an outbound HTTP call and stores the parsed JSON response body.
 * Method defaults to GET; set body to trigger a POST.
 *
 * Compensation (saga): if a compensationUrl is configured, a DELETE is issued
 * against it when the run is rolled back — undoing whatever this step created.
 * Example: a POST that creates a resource would set compensationUrl to the
 * resource's self-link so it can be deleted on failure.
 */
public final class HttpStep extends AbstractStep {

    private static final Logger log = LoggerFactory.getLogger(HttpStep.class);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String url;
    private final String method;
    private final String body;             // optional JSON body for POST/PUT
    private final String compensationUrl;  // nullable; DELETE issued here on compensation

    public HttpStep(String id, String url, String method, String body,
                    String compensationUrl, RetryPolicy retryPolicy) {
        super(id, retryPolicy);
        this.url = url;
        this.method = method.toUpperCase();
        this.body = body;
        this.compensationUrl = compensationUrl;
    }

    public HttpStep(String id, String url, String method, String body, RetryPolicy retryPolicy) {
        this(id, url, method, body, null, retryPolicy);
    }

    public HttpStep(String id, String url) {
        this(id, url, "GET", null, null, NoRetry.INSTANCE);
    }

    @Override
    protected void validate(ExecutionContext ctx) throws StepException {
        if (url == null || url.isBlank()) {
            throw new StepException(id(), "HttpStep requires a URL", false);
        }
    }

    @Override
    protected StepResult doExecute(ExecutionContext ctx) throws StepException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json");

            if ("POST".equals(method) || "PUT".equals(method)) {
                String requestBody = body != null ? body : "{}";
                builder.method(method, HttpRequest.BodyPublishers.ofString(requestBody))
                       .header("Content-Type", "application/json");
            } else {
                builder.GET();
            }

            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status >= 400) {
                throw new StepException(id(),
                        "HTTP " + method + " " + url + " returned " + status + ": " + response.body(),
                        status >= 500); // 5xx are retryable, 4xx are not
            }

            Object parsedBody;
            try {
                parsedBody = MAPPER.readValue(response.body(), Object.class);
            } catch (Exception e) {
                parsedBody = response.body();
            }

            return StepResult.success(Map.of("statusCode", status, "body", parsedBody));

        } catch (StepException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StepException(id(), "HTTP request interrupted", e, false);
        } catch (Exception e) {
            throw new StepException(id(), "HTTP request failed: " + e.getMessage(), e, true);
        }
    }

    /**
     * Saga compensation: issue an HTTP DELETE to compensationUrl (if configured).
     * This undoes a resource that was created by the forward execution.
     */
    @Override
    public void compensate(ExecutionContext ctx) {
        if (compensationUrl == null || compensationUrl.isBlank()) return;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(compensationUrl))
                    .timeout(Duration.ofSeconds(30))
                    .DELETE()
                    .build();
            HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("[step:{}] compensation DELETE {} → {}", id(), compensationUrl, response.statusCode());
        } catch (Exception e) {
            log.warn("[step:{}] compensation request failed: {}", id(), e.getMessage());
        }
    }

    public String url() { return url; }
    public String method() { return method; }
    public String compensationUrl() { return compensationUrl; }
}
