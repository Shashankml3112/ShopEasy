package com.shop;

import com.sun.net.httpserver.HttpServer;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * End-to-end test: starts the real server on a random port and drives it like
 * browsers would (login, cart, checkout, admin add/delete...). Run it as a normal Java application.
 */
public class SmokeTest {

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (!ok) {
            failures++;
        }
    }

    /** A fake browser with its own cookies, so we can have a customer and an admin at the same time. */
    private static class Client {
        final HttpClient http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        final String base;

        Client(String base) {
            this.base = base;
        }

        String get(String path) throws Exception {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + path)).GET().build();
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        }

        String post(String path, String... keyValues) throws Exception {
            StringBuilder form = new StringBuilder();
            for (int i = 0; i < keyValues.length; i += 2) {
                if (i > 0) {
                    form.append('&');
                }
                form.append(URLEncoder.encode(keyValues[i], StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(keyValues[i + 1], StandardCharsets.UTF_8));
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        }
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = new ShopApp().start(0);
        String base = "http://localhost:" + server.getAddress().getPort();
        Client guest = new Client(base);
        Client customer = new Client(base);
        Client admin = new Client(base);
        String page;

        // ---- guest
        page = guest.get("/");
        check("Guest sees the product list", page.contains("Wireless Earbuds") && page.contains("₹1499.00"));
        page = guest.get("/?q=keyboard");
        check("Search filters products", page.contains("Mechanical Keyboard") && !page.contains("Wireless Earbuds"));
        page = guest.post("/cart/add", "productId", "1", "qty", "1");
        check("Guest cannot add to cart (sent to login)", page.contains("Please log in first."));
        page = guest.get("/admin");
        check("Guest cannot open admin page", page.contains("Admin login required."));

        // ---- customer: register + login
        page = customer.post("/register", "username", "ravi", "password", "x");
        check("Short password is rejected", page.contains("at least 6 characters"));
        customer.post("/register", "username", "ravi", "password", "secret1");
        page = customer.post("/register", "username", "RAVI", "password", "secret1");
        check("Duplicate username is rejected", page.contains("already taken"));
        page = customer.post("/login", "username", "ravi", "password", "wrongpass");
        check("Wrong password is rejected", page.contains("Invalid username or password."));
        page = customer.post("/login", "username", "ravi", "password", "secret1");
        check("Customer can log in", page.contains("Welcome, ravi!"));

        // ---- customer: cart
        page = customer.post("/cart/add", "productId", "1", "qty", "2");
        check("Add to cart works", page.contains("Wireless Earbuds") && page.contains("Cart (2)"));
        check("Cart total is correct (2 x 1499)", page.contains("₹2998.00"));
        page = customer.post("/cart/add", "productId", "5", "qty", "6");
        check("Cannot add more than the stock", page.contains("Only 5 of LED Desk Lamp left in stock."));
        page = customer.post("/cart/update", "productId", "1", "qty", "1");
        check("Update quantity works", page.contains("₹1499.00") && page.contains("Cart (1)"));
        page = customer.post("/cart/remove", "productId", "1");
        check("Delete from cart works", page.contains("Item removed") && page.contains("Your cart is empty"));
        page = customer.post("/checkout");
        check("Checkout of empty cart is refused", page.contains("Your cart is empty."));

        // ---- customer: checkout
        customer.post("/cart/add", "productId", "1", "qty", "2");
        customer.post("/cart/add", "productId", "5", "qty", "1");
        page = customer.post("/checkout");
        check("Checkout places the order", page.contains("Order #1 placed!") && page.contains("₹3897.00"));
        check("Stock is reduced after checkout", page.contains("In stock (23)") && page.contains("In stock (4)"));
        page = customer.get("/cart");
        check("Cart is empty after checkout", page.contains("Your cart is empty"));

        // ---- customer must not reach admin features
        page = customer.get("/admin");
        check("Customer cannot open admin page", page.contains("Admin login required."));
        page = customer.post("/admin/products/add", "name", "Hacked", "description", "x", "price", "1", "stock", "1");
        check("Customer cannot add products", !customer.get("/").contains("Hacked"));
        page = customer.post("/admin/products/delete", "productId", "2");
        check("Customer cannot delete products", customer.get("/").contains("Mechanical Keyboard"));

        // ---- admin
        page = admin.post("/login", "username", "admin", "password", "admin123");
        check("Admin can log in and sees dashboard", page.contains("Admin dashboard"));
        check("Admin sees the customer's order", page.contains("#1") && page.contains("ravi"));

        page = admin.post("/admin/products/add", "name", "Test Gadget", "description", "demo", "price", "99.5", "stock", "3");
        check("Admin can add a product", page.contains("Test Gadget") && page.contains("₹99.50"));
        page = admin.post("/admin/products/add", "name", "Bad", "description", "", "price", "-5", "stock", "1");
        check("Negative price is rejected", page.contains("Price must be greater than 0"));
        page = admin.post("/admin/products/add", "name", "<script>alert(1)</script>", "description", "", "price", "10", "stock", "1");
        String home = guest.get("/");
        check("HTML in product names is escaped (no XSS)",
                home.contains("&lt;script&gt;alert(1)&lt;/script&gt;") && !home.contains("<script>alert(1)</script>"));
        check("New product is visible to customers", guest.get("/").contains("Test Gadget"));

        page = admin.post("/admin/products/update", "productId", "2", "price", "2999", "stock", "7");
        check("Admin can edit price and stock", page.contains("₹2999.00") && page.contains("Product updated."));

        // customer puts product 9 in the cart, then the admin deletes it
        customer.post("/cart/add", "productId", "9", "qty", "1");
        check("Customer has Test Gadget in cart", customer.get("/cart").contains("Test Gadget"));
        page = admin.post("/admin/products/delete", "productId", "9");
        check("Admin can delete a product", page.contains("Product deleted.") && !page.contains("Test Gadget"));
        check("Deleted product disappears from customer carts", !customer.get("/cart").contains("Test Gadget"));
        check("Deleted product disappears from the shop", !guest.get("/").contains("Test Gadget"));

        // ---- product images
        page = admin.post("/admin/products/add", "name", "Photo Item", "description", "has image", "price", "10", "stock", "5",
                "imageUrl", "/images/sample.png");
        check("Admin can add a product with an image", page.contains("src='/images/sample.png'"));
        check("Shop page shows the product image", guest.get("/").contains("class='thumb' loading='lazy' src='/images/sample.png'"));
        check("Image file is served", guest.get("/images/sample.png").length() > 100);
        page = admin.post("/admin/products/add", "name", "Web Img", "description", "", "price", "10", "stock", "5",
                "imageUrl", "https://example.com/pic.jpg");
        check("Web image link is accepted", page.contains("https://example.com/pic.jpg"));
        page = admin.post("/admin/products/add", "name", "Evil", "description", "", "price", "10", "stock", "5",
                "imageUrl", "javascript:alert(1)");
        check("Unsafe image link is rejected", page.contains("Image must be a link"));
        page = admin.post("/admin/products/add", "name", "Evil2", "description", "", "price", "10", "stock", "5",
                "imageUrl", "/images/../src/com/shop/Store.java");
        check("Path tricks in image link are rejected", page.contains("Image must be a link"));
        check("Cannot read files outside images folder",
                !guest.get("/images/..%2Fsrc%2Fcom%2Fshop%2FStore.java").contains("class Store"));
        check("Products without image show a letter box", guest.get("/").contains("class='thumb ph'"));

        // ---- admin has no cart, logout works
        page = admin.post("/cart/add", "productId", "1", "qty", "1");
        check("Admin cannot use the cart", page.contains("have a cart"));
        customer.post("/logout");
        page = customer.get("/cart");
        check("After logout the cart needs login again", page.contains("Please log in first."));

        server.stop(0);
        System.out.println();
        System.out.println(failures == 0 ? "ALL TESTS PASSED" : failures + " TEST(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
