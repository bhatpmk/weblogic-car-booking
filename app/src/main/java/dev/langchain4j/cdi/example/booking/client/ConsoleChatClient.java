package dev.langchain4j.cdi.example.booking.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Scanner;

/**
 * Simple console client for ChatAiService.
 *
 * It talks to the existing REST endpoint exposed by CarBookingResource:
 *   GET /car-booking/api/car-booking/chat?question=...
 *
 * Chat history is retained on the server by ChatAiService (shared memory).
 * This client simply sends each user input as the next turn in the conversation.
 *
 * Usage:
 *   - Default base URL: http://localhost:7001/car-booking/api/car-booking/chat
 *   - Override base URL with the first argument:
 *       java ... ConsoleChatClient http://host:port/car-booking/api/car-booking/chat
 *
 * Quit:
 *   - Type /quit on a line by itself, or hit Enter on an empty line.
 */
public class ConsoleChatClient {

    private static final String DEFAULT_ENDPOINT = "http://localhost:7001/car-booking/api/car-booking/chat";

    public static void main(String[] args) {
        String endpoint = args != null && args.length > 0 && args[0] != null && !args[0].isBlank()
                ? args[0].trim()
                : DEFAULT_ENDPOINT;

        System.out.println("Miles of Smiles - Console Chat Client");
        System.out.println("Endpoint: " + endpoint);
        System.out.println("Type your message and press Enter.");
        System.out.println("Commands: /quit to exit.");
        System.out.println();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("You> ");
                String line = scanner.nextLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (line.isEmpty() || "/quit".equalsIgnoreCase(line)) {
                    System.out.println("Exiting chat.");
                    break;
                }

                // Encode question parameter
                String question = URLEncoder.encode(line, StandardCharsets.UTF_8);
                String url = endpoint + "?question=" + question;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .GET()
                        .build();

                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    int status = response.statusCode();
                    String body = response.body();
                    if (status >= 200 && status < 300) {
                        System.out.println("Assistant> " + (body == null ? "" : body));
                    } else {
                        System.out.println("Assistant> [HTTP " + status + "] " + (body == null ? "" : body));
                    }
                } catch (Exception e) {
                    System.out.println("Assistant> [Error] " + e.getMessage());
                }
            }
        }
    }
}
