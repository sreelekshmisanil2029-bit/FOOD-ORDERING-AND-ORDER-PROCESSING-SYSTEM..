import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small Java HTTP server for the project. Keep this file beside index.html and style.css.
 * Run with: javac FoodOrder.java FoodOrderServer.java
 *           java FoodOrderServer
 */
public class FoodOrderServer {
    private static final FoodOrder app = new FoodOrder();
    private static final Path ROOT = Paths.get(".").toAbsolutePath().normalize();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/menu", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            StringBuilder json = new StringBuilder("{\"menu\":[");
            List<FoodOrder.MenuItem> menu = app.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                FoodOrder.MenuItem item = menu.get(i);
                if (i > 0) json.append(",");
                json.append("{")
                    .append("\"id\":").append(item.id).append(",")
                    .append("\"name\":").append(quote(item.name)).append(",")
                    .append("\"category\":").append(quote(item.category)).append(",")
                    .append("\"price\":").append(item.price).append(",")
                    .append("\"emoji\":").append(quote(item.emoji)).append(",")
                    .append("\"description\":").append(quote(item.description))
                    .append("}");
            }
            json.append("]}");
            sendJson(exchange, 200, json.toString());
        });

        server.createContext("/api/orders", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 200, ordersJson());
            } else if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    Map<String, String> form = parseForm(new String(
                            exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    String customer = form.getOrDefault("customer", "");
                    String rawItems = form.getOrDefault("items", "");
                    Map<Integer, Integer> items = parseItems(rawItems);
                    FoodOrder.Order order = app.placeOrder(customer, items);
                    sendJson(exchange, 201, "{\"message\":\"Order placed successfully\","
                            + "\"orderId\":" + order.id + ",\"total\":" + order.total + "}");
                } catch (IllegalArgumentException ex) {
                    sendJson(exchange, 400, "{\"error\":" + quote(ex.getMessage()) + "}");
                } catch (Exception ex) {
                    sendJson(exchange, 400, "{\"error\":\"Could not place the order. Check your selection.\"}");
                }
            } else {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        });

        server.createContext("/api/process", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            FoodOrder.Order order = app.processNextOrder();
            if (order == null) {
                sendJson(exchange, 409, "{\"error\":\"There are no pending orders to process.\"}");
            } else {
                sendJson(exchange, 200, "{\"message\":\"Order processed\","
                        + "\"orderId\":" + order.id + "}");
            }
        });

        server.createContext("/", exchange -> serveStatic(exchange));
        server.start();
        System.out.println("Food Ordering System is running at http://localhost:8080");
        System.out.println("Keep index.html and style.css in this folder.");
    }

    private static Map<Integer, Integer> parseItems(String raw) {
        Map<Integer, Integer> items = new LinkedHashMap<>();
        if (raw == null || raw.trim().isEmpty()) return items;
        for (String pair : raw.split(",")) {
            String[] parts = pair.split(":");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid cart data.");
            int id = Integer.parseInt(parts[0]);
            int quantity = Integer.parseInt(parts[1]);
            items.put(id, quantity);
        }
        return items;
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1
                    ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private static String ordersJson() {
        List<FoodOrder.Order> orders = app.getOrders();
        StringBuilder json = new StringBuilder("{\"orders\":[");
        for (int i = 0; i < orders.size(); i++) {
            FoodOrder.Order order = orders.get(i);
            if (i > 0) json.append(",");
            json.append("{\"id\":").append(order.id)
                .append(",\"customer\":").append(quote(order.customer))
                .append(",\"total\":").append(order.total)
                .append(",\"status\":").append(quote(order.status))
                .append(",\"createdAt\":").append(quote(order.createdAt))
                .append(",\"items\":[");
            int j = 0;
            for (Map.Entry<Integer, Integer> entry : order.items.entrySet()) {
                FoodOrder.MenuItem item = app.getMenuItem(entry.getKey());
                if (j++ > 0) json.append(",");
                json.append("{\"name\":").append(quote(item.name))
                    .append(",\"quantity\":").append(entry.getValue()).append("}");
            }
            json.append("]}");
        }
        json.append("],\"pending\":").append(app.getPendingCount())
            .append(",\"completed\":").append(app.getCompletedCount())
            .append(",\"sales\":").append(app.getCompletedSales()).append("}");
        return json.toString();
    }

    private static void serveStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed", "text/plain; charset=UTF-8");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = ROOT.resolve("." + path).normalize();
        if (!file.startsWith(ROOT) || !Files.isRegularFile(file)) {
            sendText(exchange, 404, "File not found", "text/plain; charset=UTF-8");
            return;
        }
        String name = file.getFileName().toString().toLowerCase();
        String type = name.endsWith(".css") ? "text/css; charset=UTF-8"
                : name.endsWith(".html") ? "text/html; charset=UTF-8"
                : "application/octet-stream";
        byte[] data = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        sendText(exchange, status, body, "application/json; charset=UTF-8");
    }

    private static void sendText(HttpExchange exchange, int status, String body, String type)
            throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private static String quote(String value) {
        if (value == null) return "\"\"";
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 32) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
            }
        }
        return out.append("\"").toString();
    }
}
