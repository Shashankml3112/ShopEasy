package com.shop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static com.shop.Html.esc;
import static com.shop.Html.money;

public class ShopApp {

    private final Store store = new Store();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();   // cookie token -> username
    private final SecureRandom random = new SecureRandom();

    /** Everything a handler needs to know about the current request. */
    private record Req(HttpExchange ex, User user, Map<String, String> query, Map<String, String> form) {
    }

    public ShopApp() {
        // Change this password before ever deploying! It can be set with the ADMIN_PASSWORD env variable.
        String adminPassword = System.getenv().getOrDefault("ADMIN_PASSWORD", "admin123");
        store.register("admin", adminPassword, Role.ADMIN);
        seedProducts();
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        new ShopApp().start(port);
        System.out.println("ShopEasy is running at http://localhost:" + port);
        System.out.println("Admin login -> username: admin   password: admin123");
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));   // handle several users at once
        server.start();
        return server;
    }

    private void seedProducts() {
        store.addProduct("Wireless Earbuds", "Bluetooth 5.3, 24-hour battery", new BigDecimal("1499.00"), 25, "https://m.media-amazon.com/images/I/719kzqTTcgL._AC_SL1500_.jpg");
        store.addProduct("Mechanical Keyboard", "Blue switches, USB-C", new BigDecimal("3299.00"), 10, "https://kreo-tech.com/cdn/shop/files/Frame_1000007111.png?v=1781185664");
        store.addProduct("Laptop Backpack", "Water resistant, fits 15.6 inch", new BigDecimal("1899.00"), 15, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQSnD1opIgCsFIB2sb58YddvkCUgSoIAk0m9GwjkXqlJSNi3YSTk-Fx0L_g&s=10");
        store.addProduct("Steel Water Bottle", "1 litre, keeps drinks cold for 24 hours", new BigDecimal("499.00"), 40, "https://rukminim3.flixcart.com/image/480/640/xif0q/shopsy-bottle/y/h/h/300-300-ml-hot-cold-stainless-steel-thermos-flask-water-bottle-original-imahptm4hz2rtces.jpeg?q=20");
        store.addProduct("LED Desk Lamp", "Touch dimmer, 3 colour modes", new BigDecimal("899.00"), 5, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSdjgKfMy_PSZugbzz_vnPxKDCcGstXzUhrg-f5QrYkSw&s=10");
        store.addProduct("Notebook Pack (5)", "A5 ruled, 200 pages each", new BigDecimal("249.00"), 100, "https://solo.in/cdn/shop/files/5-subjects-notebook-pack-of-4-pcs-a5-na553-436198.webp?v=1742021571");
        store.addProduct("USB-C Cable 1m", "Fast charging, braided", new BigDecimal("399.00"), 60, "https://cdn.zeptonow.com/production/tr:w-640,ar-1500-1807.2289156626507,pr-true,f-auto,q-80/cms/product_variant/87a7f474-1da5-4934-bea8-6ab4aff8d942_aspect_ratios=3x4.jpeg");
        store.addProduct("Bluetooth Speaker", "Portable, 12-hour playtime", new BigDecimal("2199.00"), 8, "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSncVK5eIEDW6dDMsjmaXHX78CLBKvOxerAqxtAS3dEiWX13GvRVWswMzw&s=10");
    }

    

    private void handle(HttpExchange ex) throws IOException {
        try {
            Req r = new Req(ex, currentUser(ex), parse(ex.getRequestURI().getRawQuery()), readForm(ex));
            String route = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
            if (route.startsWith("GET /images/")) {
                serveImage(ex, ex.getRequestURI().getPath().substring("/images/".length()));
                return;
            }
            switch (route) {
                case "GET /" -> home(r);
                case "GET /login" -> loginPage(r);
                case "POST /login" -> doLogin(r);
                case "GET /register" -> registerPage(r);
                case "POST /register" -> doRegister(r);
                case "POST /logout" -> doLogout(r);

                case "GET /cart" -> cartPage(r);
                case "POST /cart/add" -> cartAdd(r);
                case "POST /cart/update" -> cartUpdate(r);
                case "POST /cart/remove" -> cartRemove(r);
                case "POST /checkout" -> checkout(r);

                case "GET /admin" -> adminPage(r);
                case "POST /admin/products/add" -> adminAdd(r);
                case "POST /admin/products/update" -> adminUpdate(r);
                case "POST /admin/products/delete" -> adminDelete(r);

                default -> sendHtml(ex, 404, Html.page("Not found", r.user(), cartCount(r.user()), null, null,
                        "<h2>404 - Page not found</h2><p><a href='/'>Back to the shop</a></p>"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            byte[] msg = "Something went wrong on the server.".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(500, msg.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(msg);
            }
        } finally {
            ex.close();
        }
    }

    

    private void home(Req r) throws IOException {
        String q = r.query().getOrDefault("q", "").trim();
        List<Product> products = store.search(q);

        StringBuilder sb = new StringBuilder();
        sb.append("<form class='search' method='get' action='/'><input name='q' placeholder='Search products' value='")
                .append(esc(q)).append("'><button>Search</button></form>");
        if (products.isEmpty()) {
            sb.append("<p>No products found.</p>");
        }
        sb.append("<div class='grid'>");
        for (Product p : products) {
            sb.append("<div class='card'>").append(imageTag(p)).append("<h3>").append(esc(p.name())).append("</h3>")
                    .append("<p class='muted'>").append(esc(p.description())).append("</p>")
                    .append("<p class='price'>").append(money(p.price())).append("</p>")
                    .append(p.stock() > 0
                            ? "<p class='ok'>In stock (" + p.stock() + ")</p>"
                            : "<p class='bad'>Out of stock</p>");
            if (r.user() == null || !r.user().isAdmin()) {
                sb.append("<form class='inline' method='post' action='/cart/add'>")
                        .append("<input type='hidden' name='productId' value='").append(p.id()).append("'>")
                        .append("<input type='number' name='qty' value='1' min='1' max='99'>")
                        .append("<button").append(p.stock() > 0 ? "" : " disabled").append(">Add to Cart</button></form>");
            }
            sb.append("</div>");
        }
        sb.append("</div>");
        render(r, "Home", sb.toString());
    }

    private void loginPage(Req r) throws IOException {
        render(r, "Login", "<h2>Login</h2><form class='stack' method='post' action='/login'>"
                + "<input name='username' placeholder='Username' required>"
                + "<input name='password' type='password' placeholder='Password' required>"
                + "<button>Login</button></form>"
                + "<p>New here? <a href='/register'>Create an account</a></p>");
    }

    private void doLogin(Req r) throws IOException {
        User user = store.login(r.form().get("username"), r.form().get("password"));
        if (user == null) {
            redirect(r.ex(), "/login", "Invalid username or password.", true);
            return;
        }
        String token = randomToken();                       // brand-new token on every login
        sessions.put(token, user.username());
        r.ex().getResponseHeaders().add("Set-Cookie", "SID=" + token + "; Path=/; HttpOnly; SameSite=Lax");
        redirect(r.ex(), user.isAdmin() ? "/admin" : "/", "Welcome, " + user.username() + "!", false);
    }

    private void registerPage(Req r) throws IOException {
        render(r, "Register", "<h2>Create an account</h2><form class='stack' method='post' action='/register'>"
                + "<input name='username' placeholder='Username (3-20 letters/digits)' required>"
                + "<input name='password' type='password' placeholder='Password (min 6 characters)' required>"
                + "<button>Register</button></form>");
    }

    private void doRegister(Req r) throws IOException {
        try {
            // Public sign-up can only ever create CUSTOMER accounts, never admins.
            store.register(r.form().get("username"), r.form().get("password"), Role.CUSTOMER);
            redirect(r.ex(), "/login", "Account created. Please log in.", false);
        } catch (ShopException e) {
            redirect(r.ex(), "/register", e.getMessage(), true);
        }
    }

    private void doLogout(Req r) throws IOException {
        String token = cookieValue(r.ex(), "SID");
        if (token != null) {
            sessions.remove(token);
        }
        r.ex().getResponseHeaders().add("Set-Cookie", "SID=; Path=/; Max-Age=0");
        redirect(r.ex(), "/", "You have been logged out.", false);
    }

  

    private void cartPage(Req r) throws IOException {
        if (!requireCustomer(r)) {
            return;
        }
        List<CartLine> lines = store.getCart(r.user().username());
        if (lines.isEmpty()) {
            render(r, "Your Cart", "<h2>Your cart</h2><p>Your cart is empty. <a href='/'>Continue shopping</a></p>");
            return;
        }
        StringBuilder sb = new StringBuilder("<h2>Your cart</h2><table><tr><th>Product</th><th>Price</th>"
                + "<th>Quantity</th><th>Subtotal</th><th></th></tr>");
        for (CartLine l : lines) {
            Product p = l.product();
            sb.append("<tr><td>").append(p.imageUrl().isEmpty() ? ""
                            : "<img class='mini' src='" + esc(p.imageUrl()) + "' alt=''>")
                    .append(esc(p.name())).append("</td><td>").append(money(p.price())).append("</td>")
                    .append("<td><form class='inline' method='post' action='/cart/update'>")
                    .append("<input type='hidden' name='productId' value='").append(p.id()).append("'>")
                    .append("<input type='number' name='qty' value='").append(l.qty()).append("' min='0' max='99'>")
                    .append("<button>Update</button></form></td>")
                    .append("<td>").append(money(l.lineTotal())).append("</td>")
                    .append("<td><form class='inline' method='post' action='/cart/remove'>")
                    .append("<input type='hidden' name='productId' value='").append(p.id()).append("'>")
                    .append("<button class='danger'>Remove</button></form></td></tr>");
        }
        sb.append("</table><p class='total'>Total: <b>").append(money(Store.total(lines))).append("</b></p>")
                .append("<form method='post' action='/checkout' style='text-align:right'>")
                .append("<button class='primary'>Place order</button></form>");
        render(r, "Your Cart", sb.toString());
    }

    private void cartAdd(Req r) throws IOException {
        if (!requireCustomer(r)) {
            return;
        }
        try {
            store.addToCart(r.user().username(), intOf(r.form().get("productId"), -1), intOf(r.form().get("qty"), 1));
            redirect(r.ex(), "/cart", "Added to your cart.", false);
        } catch (ShopException e) {
            redirect(r.ex(), "/", e.getMessage(), true);
        }
    }

    private void cartUpdate(Req r) throws IOException {
        if (!requireCustomer(r)) {
            return;
        }
        try {
            store.setQuantity(r.user().username(), intOf(r.form().get("productId"), -1), intOf(r.form().get("qty"), 1));
            redirect(r.ex(), "/cart", "Cart updated.", false);
        } catch (ShopException e) {
            redirect(r.ex(), "/cart", e.getMessage(), true);
        }
    }

    private void cartRemove(Req r) throws IOException {
        if (!requireCustomer(r)) {
            return;
        }
        try {
            store.removeFromCart(r.user().username(), intOf(r.form().get("productId"), -1));
            redirect(r.ex(), "/cart", "Item removed from your cart.", false);
        } catch (ShopException e) {
            redirect(r.ex(), "/cart", e.getMessage(), true);
        }
    }

    private void checkout(Req r) throws IOException {
        if (!requireCustomer(r)) {
            return;
        }
        try {
            Order o = store.checkout(r.user().username());
            redirect(r.ex(), "/", "Order #" + o.id() + " placed! Total " + money(o.total()) + ". Thank you!", false);
        } catch (ShopException e) {
            redirect(r.ex(), "/cart", e.getMessage(), true);
        }
    }

    // ---------------------------------------------------------------- admin pages

    private void adminPage(Req r) throws IOException {
        if (!requireAdmin(r)) {
            return;
        }
        StringBuilder sb = new StringBuilder("<h2>Admin dashboard</h2>");

        sb.append("<h3>Add a product</h3><form class='stack' method='post' action='/admin/products/add'>")
                .append("<input name='name' placeholder='Product name' required maxlength='80'>")
                .append("<input name='description' placeholder='Description' maxlength='200'>")
                .append("<input name='price' type='number' step='0.01' min='0.01' placeholder='Price' required>")
                .append("<input name='stock' type='number' min='0' placeholder='Stock quantity' required>")
                .append("<input name='imageUrl' placeholder='Image link (https://...) or /images/photo.jpg' maxlength='500'>")
                .append("<button>Add product</button></form>");

        sb.append("<h3>Products</h3><table><tr><th>ID</th><th>Name</th><th>Price</th><th>Stock</th>"
                + "<th>Edit price / stock / image</th><th></th></tr>");
        for (Product p : store.search("")) {
            sb.append("<tr><td>").append(p.id()).append("</td><td>")
                    .append(p.imageUrl().isEmpty() ? "" : "<img class='mini' src='" + esc(p.imageUrl()) + "' alt=''>")
                    .append(esc(p.name())).append("</td>")
                    .append("<td>").append(money(p.price())).append("</td><td>").append(p.stock()).append("</td>")
                    .append("<td><form class='inline' method='post' action='/admin/products/update'>")
                    .append("<input type='hidden' name='productId' value='").append(p.id()).append("'>")
                    .append("<input name='price' type='number' step='0.01' min='0.01' value='")
                    .append(p.price().toPlainString()).append("' required>")
                    .append("<input name='stock' type='number' min='0' value='").append(p.stock()).append("' required>")
                    .append("<input name='imageUrl' placeholder='Image' maxlength='500' value='").append(esc(p.imageUrl())).append("'>")
                    .append("<button>Save</button></form></td>")
                    .append("<td><form class='inline' method='post' action='/admin/products/delete' ")
                    .append("onsubmit='return confirm(\"Delete this product?\")'>")
                    .append("<input type='hidden' name='productId' value='").append(p.id()).append("'>")
                    .append("<button class='danger'>Delete</button></form></td></tr>");
        }
        sb.append("</table>");

        sb.append("<h3>Recent orders</h3>");
        List<Order> orders = store.recentOrders(10);
        if (orders.isEmpty()) {
            sb.append("<p>No orders yet.</p>");
        } else {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault());
            sb.append("<table><tr><th>Order</th><th>Customer</th><th>Items</th><th>Total</th><th>Placed</th></tr>");
            for (Order o : orders) {
                sb.append("<tr><td>#").append(o.id()).append("</td><td>").append(esc(o.username())).append("</td><td>")
                        .append(o.items()).append("</td><td>").append(money(o.total())).append("</td><td>")
                        .append(fmt.format(o.placedAt())).append("</td></tr>");
            }
            sb.append("</table>");
        }
        render(r, "Admin", sb.toString());
    }

    private void adminAdd(Req r) throws IOException {
        if (!requireAdmin(r)) {
            return;
        }
        try {
            Product p = store.addProduct(r.form().get("name"), r.form().get("description"),
                    parsePrice(r.form().get("price")), intOf(r.form().get("stock"), -1),
                    r.form().get("imageUrl"));
            redirect(r.ex(), "/admin", "Added \"" + p.name() + "\".", false);
        } catch (ShopException e) {
            redirect(r.ex(), "/admin", e.getMessage(), true);
        }
    }

    private void adminUpdate(Req r) throws IOException {
        if (!requireAdmin(r)) {
            return;
        }
        try {
            store.updateProduct(intOf(r.form().get("productId"), -1),
                    parsePrice(r.form().get("price")), intOf(r.form().get("stock"), -1),
                    r.form().get("imageUrl"));
            redirect(r.ex(), "/admin", "Product updated.", false);
        } catch (ShopException e) {
            redirect(r.ex(), "/admin", e.getMessage(), true);
        }
    }

    private void adminDelete(Req r) throws IOException {
        if (!requireAdmin(r)) {
            return;
        }
        try {
            store.deleteProduct(intOf(r.form().get("productId"), -1));
            redirect(r.ex(), "/admin", "Product deleted.", false);
        } catch (ShopException e) {
            redirect(r.ex(), "/admin", e.getMessage(), true);
        }
    }

    
    private boolean requireCustomer(Req r) throws IOException {
        if (r.user() == null) {
            redirect(r.ex(), "/login", "Please log in first.", true);
            return false;
        }
        if (r.user().isAdmin()) {
            redirect(r.ex(), "/admin", "Admins manage the store; they don't have a cart.", true);
            return false;
        }
        return true;
    }

    
    private boolean requireAdmin(Req r) throws IOException {
        if (r.user() == null || !r.user().isAdmin()) {
            redirect(r.ex(), "/login", "Admin login required.", true);
            return false;
        }
        return true;
    }

    

    private User currentUser(HttpExchange ex) {
        String token = cookieValue(ex, "SID");
        String username = token == null ? null : sessions.get(token);
        return username == null ? null : store.findUser(username);
    }

    private static String cookieValue(HttpExchange ex, String name) {
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return null;
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private String randomToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private int cartCount(User user) {
        return user == null || user.isAdmin() ? 0 : store.cartCount(user.username());
    }

    private static Map<String, String> readForm(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            return Map.of();
        }
        byte[] body = ex.getRequestBody().readNBytes(65_536);   // cap the size of what we accept
        return parse(new String(body, StandardCharsets.UTF_8));
    }

  
    private static Map<String, String> parse(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, value);
        }
        return map;
    }

    private static int intOf(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static BigDecimal parsePrice(String s) {
        try {
            return new BigDecimal(s.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw new ShopException("Please enter a valid price.");
        }
    }

    
    private static String imageTag(Product p) {
        if (p.imageUrl().isEmpty()) {
            return "<div class='thumb ph'>" + esc(p.name().substring(0, 1).toUpperCase()) + "</div>";
        }
        return "<img class='thumb' loading='lazy' src='" + esc(p.imageUrl()) + "' alt='" + esc(p.name()) + "'>";
    }

   
    private static void serveImage(HttpExchange ex, String fileName) throws IOException {
        Path folder = Path.of("images").toAbsolutePath().normalize();
        Path file = folder.resolve(fileName).normalize();
        String lower = fileName.toLowerCase();
        String type = lower.endsWith(".png") ? "image/png"
                : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg"
                : lower.endsWith(".gif") ? "image/gif"
                : lower.endsWith(".webp") ? "image/webp" : null;
        if (type == null || !file.startsWith(folder) || !Files.isRegularFile(file)) {   // blocks ../ tricks
            ex.sendResponseHeaders(404, -1);
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=300");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    
    private void render(Req r, String title, String body) throws IOException {
        String html = Html.page(title, r.user(), cartCount(r.user()),
                r.query().get("msg"), r.query().get("err"), body);
        sendHtml(r.ex(), 200, html);
    }

    private static void sendHtml(HttpExchange ex, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    
    private static void redirect(HttpExchange ex, String to, String message, boolean error) throws IOException {
        String url = to;
        if (message != null) {
            url += (to.contains("?") ? "&" : "?") + (error ? "err=" : "msg=")
                    + URLEncoder.encode(message, StandardCharsets.UTF_8);
        }
        ex.getResponseHeaders().set("Location", url);
        ex.sendResponseHeaders(303, -1);
    }
}
