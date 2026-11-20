package com.shop;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * All business logic and data lives here (products, users, carts, orders).
 *
 * Data is kept in memory so the project runs with no database. In a real
 * product these maps would be database tables accessed through JDBC / JPA.
 *
 * Every public method is "synchronized": only one thread runs at a time, so two
 * customers can never buy the last item simultaneously.
 */
public class Store {

    private static final BigDecimal MAX_PRICE = new BigDecimal("10000000");

    private final Map<Integer, Product> products = new HashMap<>();
    private final Map<String, User> users = new HashMap<>();                    // key = lower-case username
    private final Map<String, Map<Integer, Integer>> carts = new HashMap<>();   // username -> (productId -> qty)
    private final List<Order> orders = new ArrayList<>();
    private final SecureRandom random = new SecureRandom();
    private int nextProductId = 1;
    private int nextOrderId = 1;

    // ------------------------------------------------------------------ products

    public synchronized Product addProduct(String name, String description, BigDecimal price, int stock, String imageUrl) {
        validateProduct(name, price, stock);
        String image = cleanImage(imageUrl);
        Product p = new Product(nextProductId++, name.trim(), description == null ? "" : description.trim(), price, stock, image);
        products.put(p.id(), p);
        return p;
    }

    public synchronized void updateProduct(int id, BigDecimal price, int stock, String imageUrl) {
        Product old = requireProduct(id);
        validateProduct(old.name(), price, stock);
        products.put(id, new Product(id, old.name(), old.description(), price, stock, cleanImage(imageUrl)));
    }

    /**
     * An image is either "" (none), a file in our images folder ("/images/shoe.jpg"),
     * or a web link ("https://..."). Anything else (e.g. "javascript:...") is rejected.
     */
    private static String cleanImage(String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) {
            return "";
        }
        boolean ok = u.length() <= 500 && !u.contains("..")
                && (u.matches("/images/[A-Za-z0-9._-]+")
                    || u.matches("https?://[^\\s\"'<>]+"));
        if (!ok) {
            throw new ShopException("Image must be a link starting with https:// or a file like /images/photo.jpg");
        }
        return u;
    }

    /** Deleting a product also removes it from every customer's cart. */
    public synchronized void deleteProduct(int id) {
        requireProduct(id);
        products.remove(id);
        for (Map<Integer, Integer> cart : carts.values()) {
            cart.remove(id);
        }
    }

    public synchronized List<Product> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return products.values().stream()
                .filter(p -> q.isEmpty()
                        || p.name().toLowerCase(Locale.ROOT).contains(q)
                        || p.description().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparingInt(Product::id))
                .toList();
    }

    private Product requireProduct(int id) {
        Product p = products.get(id);
        if (p == null) {
            throw new ShopException("Product not found.");
        }
        return p;
    }

    private void validateProduct(String name, BigDecimal price, int stock) {
        if (name == null || name.isBlank() || name.trim().length() > 80) {
            throw new ShopException("Product name is required (max 80 characters).");
        }
        if (price == null || price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0) {
            throw new ShopException("Price must be greater than 0 and at most 10,000,000.");
        }
        if (stock < 0 || stock > 1_000_000) {
            throw new ShopException("Stock must be between 0 and 1,000,000.");
        }
    }

    // --------------------------------------------------------------------- users

    public synchronized User register(String username, String password, Role role) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,20}")) {
            throw new ShopException("Username must be 3-20 letters, digits or underscores.");
        }
        if (password == null || password.length() < 6) {
            throw new ShopException("Password must be at least 6 characters.");
        }
        String key = username.toLowerCase(Locale.ROOT);
        if (users.containsKey(key)) {
            throw new ShopException("That username is already taken.");
        }
        String salt = randomHex(16);
        User user = new User(username, salt, hash(salt, password), role);
        users.put(key, user);
        return user;
    }

    /** Returns the user if the credentials are right, otherwise null. */
    public synchronized User login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        User user = users.get(username.toLowerCase(Locale.ROOT));
        if (user == null) {
            return null;
        }
        byte[] expected = user.passwordHash().getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(user.salt(), password).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual) ? user : null;   // constant-time comparison
    }

    public synchronized User findUser(String username) {
        return username == null ? null : users.get(username.toLowerCase(Locale.ROOT));
    }

    private String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static String hash(String salt, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest(password.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------------- cart

    public synchronized void addToCart(String username, int productId, int qty) {
        Product p = requireProduct(productId);
        if (qty < 1 || qty > 99) {
            throw new ShopException("Quantity must be between 1 and 99.");
        }
        Map<Integer, Integer> cart = carts.computeIfAbsent(username, k -> new LinkedHashMap<>());
        int newQty = cart.getOrDefault(productId, 0) + qty;
        if (newQty > p.stock()) {
            throw new ShopException(p.stock() == 0
                    ? p.name() + " is out of stock."
                    : "Only " + p.stock() + " of " + p.name() + " left in stock.");
        }
        cart.put(productId, newQty);
    }

    /** Sets the quantity of an item already in the cart. Quantity 0 removes it. */
    public synchronized void setQuantity(String username, int productId, int qty) {
        Map<Integer, Integer> cart = carts.get(username);
        if (cart == null || !cart.containsKey(productId)) {
            throw new ShopException("That item is not in your cart.");
        }
        if (qty <= 0) {
            cart.remove(productId);
            return;
        }
        Product p = requireProduct(productId);
        if (qty > 99 || qty > p.stock()) {
            throw new ShopException("Only " + p.stock() + " of " + p.name() + " left in stock.");
        }
        cart.put(productId, qty);
    }

    public synchronized void removeFromCart(String username, int productId) {
        Map<Integer, Integer> cart = carts.get(username);
        if (cart == null || cart.remove(productId) == null) {
            throw new ShopException("That item is not in your cart.");
        }
    }

    public synchronized List<CartLine> getCart(String username) {
        List<CartLine> lines = new ArrayList<>();
        Map<Integer, Integer> cart = carts.get(username);
        if (cart != null) {
            for (Map.Entry<Integer, Integer> e : cart.entrySet()) {
                Product p = products.get(e.getKey());
                if (p != null) {
                    lines.add(new CartLine(p, e.getValue()));
                }
            }
        }
        return lines;
    }

    public synchronized int cartCount(String username) {
        return getCart(username).stream().mapToInt(CartLine::qty).sum();
    }

    public static BigDecimal total(List<CartLine> lines) {
        return lines.stream().map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------ checkout

    /** Checks stock again, reduces it, records the order and empties the cart. */
    public synchronized Order checkout(String username) {
        List<CartLine> lines = getCart(username);
        if (lines.isEmpty()) {
            throw new ShopException("Your cart is empty.");
        }
        for (CartLine l : lines) {
            if (l.qty() > l.product().stock()) {
                throw new ShopException("Not enough stock for " + l.product().name()
                        + " (only " + l.product().stock() + " left). Please update your cart.");
            }
        }
        for (CartLine l : lines) {
            Product p = l.product();
            products.put(p.id(), new Product(p.id(), p.name(), p.description(), p.price(), p.stock() - l.qty(), p.imageUrl()));
        }
        Order order = new Order(nextOrderId++, username, total(lines),
                lines.stream().mapToInt(CartLine::qty).sum(), Instant.now());
        orders.add(order);
        carts.remove(username);
        return order;
    }

    /** Newest orders first. */
    public synchronized List<Order> recentOrders(int limit) {
        List<Order> copy = new ArrayList<>(orders);
        java.util.Collections.reverse(copy);
        return copy.size() > limit ? copy.subList(0, limit) : copy;
    }
}
