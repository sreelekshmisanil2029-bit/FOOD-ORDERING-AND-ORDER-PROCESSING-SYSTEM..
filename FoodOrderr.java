import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core model for the Food Ordering and Order Processing System.
 * Demonstrates a FIFO queue: the first order placed is processed first.
 */
public class FoodOrder {
    public static final class MenuItem {
        public final int id;
        public final String name;
        public final String category;
        public final double price;
        public final String emoji;
        public final String description;

        public MenuItem(int id, String name, String category, double price,
                        String emoji, String description) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.price = price;
            this.emoji = emoji;
            this.description = description;
        }
    }

    public static final class Order {
        public final int id;
        public final String customer;
        public final Map<Integer, Integer> items;
        public final double total;
        public final String createdAt;
        public String status;

        public Order(int id, String customer, Map<Integer, Integer> items,
                     double total, String createdAt) {
            this.id = id;
            this.customer = customer;
            this.items = new LinkedHashMap<>(items);
            this.total = total;
            this.createdAt = createdAt;
            this.status = "Pending";
        }
    }

    private final Deque<Order> pendingQueue = new ArrayDeque<>();
    private final List<Order> orderHistory = new ArrayList<>();
    private int nextOrderId = 1001;

    private final List<MenuItem> menu;
    private final Map<Integer, MenuItem> menuById = new LinkedHashMap<>();

    public FoodOrder() {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(1, "Classic Burger", "Burgers", 149, "🍔",
                "Juicy patty, fresh lettuce and house sauce."));
        items.add(new MenuItem(2, "Margherita Pizza", "Pizza", 249, "🍕",
                "Cheesy pizza with tomato and basil."));
        items.add(new MenuItem(3, "Creamy Pasta", "Pasta", 199, "🍝",
                "Creamy sauce with herbs and parmesan."));
        items.add(new MenuItem(4, "Crispy Fries", "Sides", 89, "🍟",
                "Golden, crunchy and perfectly salted."));
        items.add(new MenuItem(5, "Chocolate Shake", "Drinks", 119, "🥤",
                "A chilled chocolate treat."));
        items.add(new MenuItem(6, "Fresh Salad Bowl", "Healthy", 169, "🥗",
                "Fresh greens, crunchy veggies and dressing."));
        menu = Collections.unmodifiableList(items);
        for (MenuItem item : menu) menuById.put(item.id, item);
    }

    public List<MenuItem> getMenu() {
        return menu;
    }

    public synchronized Order placeOrder(String customer, Map<Integer, Integer> requestedItems) {
        if (customer == null || customer.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter the customer name.");
        }
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("Please add at least one food item.");
        }

        Map<Integer, Integer> validItems = new LinkedHashMap<>();
        double total = 0;
        for (Map.Entry<Integer, Integer> entry : requestedItems.entrySet()) {
            MenuItem item = menuById.get(entry.getKey());
            int quantity = entry.getValue();
            if (item == null || quantity < 1 || quantity > 50) {
                throw new IllegalArgumentException("One of the selected items is invalid.");
            }
            validItems.put(item.id, quantity);
            total += item.price * quantity;
        }

        Order order = new Order(nextOrderId++, customer.trim(), validItems, total,
                java.time.LocalDateTime.now().withNano(0).toString());
        pendingQueue.addLast(order); // enqueue at the rear
        orderHistory.add(order);
        return order;
    }

    public synchronized Order processNextOrder() {
        Order order = pendingQueue.pollFirst(); // dequeue from the front (FIFO)
        if (order != null) order.status = "Completed";
        return order;
    }

    public synchronized List<Order> getOrders() {
        return new ArrayList<>(orderHistory);
    }

    public synchronized int getPendingCount() {
        return pendingQueue.size();
    }

    public synchronized int getCompletedCount() {
        int count = 0;
        for (Order order : orderHistory) {
            if ("Completed".equals(order.status)) count++;
        }
        return count;
    }

    public synchronized double getCompletedSales() {
        double sales = 0;
        for (Order order : orderHistory) {
            if ("Completed".equals(order.status)) sales += order.total;
        }
        return sales;
    }

    public MenuItem getMenuItem(int id) {
        return menuById.get(id);
    }
}
