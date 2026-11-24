package careerpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Adaptee: a raw HTTP client for an OpenAI-compatible API. It knows nothing about our request types.
 * The API key is only ever placed in the Authorization header.
 */
public class ProviderClient {
    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProviderClient(String baseUrl, String apiKey, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /** POSTs a JSON body and returns the JSON response body. */
    public String post(String path, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response;
        // The request timeout above only covers the wait for the response headers. A reply that stalls after that
        // could hang for minutes (seen with the Gemini free tier), so the whole call also gets a hard limit.
        java.util.concurrent.CompletableFuture<HttpResponse<String>> pending = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            response = pending.get(timeout.toMillis() + 2000, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            pending.cancel(true);
            throw new LLMException("The AI service timed out after " + timeout.toSeconds() + " seconds", true);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpTimeoutException) {
                throw new LLMException("The AI service timed out after " + timeout.toSeconds() + " seconds", true);
            }
            throw new LLMException("Could not reach the AI service (" + (cause == null ? "unknown error" : cause.getClass().getSimpleName()) + ")", true);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new LLMException("The AI request was interrupted", false);
        }
        int status = response.statusCode();
        if (status / 100 != 2) {
            String detail = "";
            try {
                JsonNode root = mapper.readTree(response.body());
                if (root != null && root.isArray() && root.size() > 0) root = root.get(0); // Gemini wraps errors in a list
                detail = root == null ? "" : root.path("error").path("message").asText("");
            } catch (IOException ignored) { }
            boolean retryable = status == 429 || status >= 500;
            if (detail.length() > 200) detail = detail.substring(0, 200) + "...";
            String hint = status == 401 ? " Check LLM_API_KEY in your .env file." : "";
            throw new LLMException("AI service returned HTTP " + status + (detail.isBlank() ? "" : ": " + detail) + hint, retryable);
        }
        return response.body();
    }
}
