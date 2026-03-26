import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpServer;

public class recipeserver {

    // Load API key from .env
    static String loadApiKey() throws IOException {
        for (String line : Files.readAllLines(Path.of(".env"))) {
            if (line.startsWith("GEMINI_API_KEY=")) {
                return line.substring("GEMINI_API_KEY=".length()).trim();
            }
        }
        throw new RuntimeException("GEMINI_API_KEY not found in .env");
    }

    public static void main(String[] args) throws IOException {
        String apiKey = loadApiKey();
        HttpServer server = HttpServer.create(new InetSocketAddress(3000), 0);

        // Serve index.html
        server.createContext("/", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            File file = new File("index.html");
            byte[] bytes = Files.readAllBytes(file.toPath());

            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        // API endpoint
        server.createContext("/api/analyze-ingredients", exchange -> {

            // CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                // Read request body
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                // FIXED ingredient parsing
                String ingredients = body
                        .replaceAll(".*\\[", "")
                        .replaceAll("].*", "")
                        .replace("\"", "");

                String prompt = "You are a creative chef. Given these leftover ingredients: " + ingredients +
                        ", suggest ONE practical recipe. Include: recipe name, prep time, " +
                        "step-by-step numbered instructions, and helpful tips. Keep it concise but easy to follow.";

                String requestBody = "{\"contents\":[{\"parts\":[{\"text\":\"" +
                    prompt.replace("\"", "\\\\\"").replace("\n", "\\n") + "\"}]}]}";

                HttpClient client = HttpClient.newHttpClient();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                        "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=" + apiKey
                        ))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();   

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    String error = "{\"error\": \"API returned status " + response.statusCode() + "\"}";
                    byte[] bytes = error.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    exchange.getResponseBody().close();
                    return;
                }

                String responseBody = response.body();

                // DEBUG (optional)
                System.out.println(responseBody);

                // FIXED safe extraction
                String recipe = "No recipe generated.";

                int index = responseBody.indexOf("\"text\":");
                if (index != -1) {
                    int start = responseBody.indexOf("\"", index + 7) + 1;
                    int end = responseBody.indexOf("\"", start);
                    if (start > 0 && end > start) {
                        recipe = responseBody.substring(start, end);
                    }
                }

                // FIXED: send "recipes" not "recipe"
                String jsonResponse = "{ \"recipes\": \"" + recipe.replace("\"", "\\\"") + "\" }";

                byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();

            } catch (Exception e) {
                String error = "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
                byte[] bytes = error.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
            }
        });

        server.start();
        System.out.println("✅ Leftover Lab running at http://localhost:3000");
    }
}