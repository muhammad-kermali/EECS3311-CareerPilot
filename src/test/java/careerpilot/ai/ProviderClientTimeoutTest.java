package careerpilot.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** A reply that sends its headers and then stalls must still be cut off at the time limit. */
class ProviderClientTimeoutTest {
    @Test
    void stalledReplyIsCutOff() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            ex.sendResponseHeaders(200, 0); // headers now, body later
            OutputStream out = ex.getResponseBody();
            out.write("{\"choices\":".getBytes());
            out.flush();
            try { Thread.sleep(8000); } catch (InterruptedException ignored) { }
            try { out.write("[]}".getBytes()); out.close(); } catch (Exception ignored) { }
        });
        server.start();
        try {
            ProviderClient client = new ProviderClient("http://127.0.0.1:" + server.getAddress().getPort(), "k", Duration.ofSeconds(1));
            long start = System.nanoTime();
            LLMException e = assertThrows(LLMException.class, () -> client.post("/chat/completions", "{}"));
            long ms = (System.nanoTime() - start) / 1_000_000;
            assertTrue(e.getMessage().contains("timed out"), e.getMessage());
            assertTrue(e.isRetryable());
            assertTrue(ms < 6000, "took " + ms + "ms");
        } finally {
            server.stop(0);
        }
    }
}
