# ShopEasy - an online shop in plain Java

A small Amazon-style website: customers browse, search, add to cart, change quantities,
remove items and check out. An admin logs in to add, edit and delete products and see orders.
Pure Java (JDK 17 or newer). No libraries, no database, nothing to download.

## Run it in VS Code (one click)
1. Install the **Extension Pack for Java** (VS Code will suggest it) and a JDK 17+.
2. File > Open Folder > choose the `shopeasy` folder.
3. Press **F5** (or the green Run button). The shop builds, starts and opens in your browser.
   To run the tests instead, pick **Run Tests** in the Run and Debug dropdown.

## Run it with a double-click (no IDE)
- Windows: double-click **run.bat**. Mac/Linux: run `./run.sh`.

## Run it in Eclipse (2024-12 or 2025-26)

1. Make sure Eclipse uses a JDK 17+ (Window > Preferences > Java > Installed JREs).
2. File > Import > General > **Existing Projects into Workspace** > select the `shopeasy` folder > Finish.
3. Open `src/com/shop/ShopApp.java`, right-click > **Run As > Java Application**.
4. Open http://localhost:8080 in your browser.

If Eclipse shows a "JRE" error on the project: right-click the project > Properties >
Java Build Path > Libraries > replace the JRE with your JDK 17+.

To run the automated test: right-click `test/com/shop/SmokeTest.java` > Run As > Java Application.
You should see a list of PASS lines and `ALL TESTS PASSED`.

### Without an IDE
```
javac -encoding UTF-8 -d bin src/com/shop/*.java
java -cp bin com.shop.ShopApp
```

## Logins
| Role     | Username | Password   |
|----------|----------|------------|
| Admin    | admin    | admin123   |
| Customer | (click Register and make your own) | |

## Adding product images
Log in as admin, then either:
- **Use a web link:** paste a picture link (starting with `https://`) in the "Image" box when adding a product.
- **Use your own picture file:** copy the file (`.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`) into the `images` folder
  in the project (in Eclipse: drag it onto the `images` folder, choose *Copy files*), then type
  `/images/yourfile.jpg` in the Image box. File names: letters, digits, `.`, `_`, `-` only.
You can change a product's image any time with the Image box in the admin product table, then press Save.
A sample file `images/sample.png` is included to try.

## Project layout
```
src/com/shop/
  ShopApp.java      web layer: server, URL routes, login check, pages
  Store.java        business logic + data (products, users, carts, orders)
  Html.java         page layout, HTML escaping, money format
  Product.java  User.java  CartLine.java  Order.java  Role.java   data classes
  ShopException.java   "a rule was broken" error shown to the user
images/                        picture files served at /images/...
test/com/shop/SmokeTest.java   end-to-end test (41 checks)
```

## How a request flows
```
Browser --HTTP--> ShopApp.handle()
                    1. read cookie -> who is logged in?
                    2. route = METHOD + path   e.g. "POST /cart/add"
                    3. guard: requireCustomer() / requireAdmin()
                    4. call Store (business rules, stock check)
                    5. redirect (after POST) or render HTML
```

## Feature -> where the code is
| Feature                      | URL                         | Code                                   |
|------------------------------|-----------------------------|----------------------------------------|
| Product list + search        | GET /                       | ShopApp.home, Store.search             |
| Register / Login / Logout    | /register /login /logout    | ShopApp.doRegister/doLogin, Store.register/login |
| Add to cart                  | POST /cart/add              | Store.addToCart (checks stock)         |
| Change quantity              | POST /cart/update           | Store.setQuantity                      |
| Delete from cart             | POST /cart/remove           | Store.removeFromCart                   |
| Checkout                     | POST /checkout              | Store.checkout (reduces stock, saves order) |
| Admin: add product           | POST /admin/products/add    | Store.addProduct                       |
| Admin: edit price/stock      | POST /admin/products/update | Store.updateProduct                    |
| Admin: delete product        | POST /admin/products/delete | Store.deleteProduct (also clears carts)|

## Design decisions to mention
- **Passwords** are stored as salted SHA-256 hashes, never as plain text.
- **Sessions**: a random token in an HttpOnly cookie maps to the username on the server.
- **Admin protection** is checked on the server for every admin request, not just by hiding buttons.
- **Public registration can only create customers**; the admin account is created at start-up.
- **Stock safety**: Store methods are `synchronized`, so two buyers cannot take the last item at once.
- **XSS protection**: every user-supplied value is passed through `Html.esc`.
- **Money** uses `BigDecimal`, not `double`.
- **Post/Redirect/Get**: after each form POST the server redirects, so refreshing cannot repeat an action.
- **Deleting a product** also removes it from every cart.

## What a production version would add (good to say honestly)
- A real database (MySQL/PostgreSQL) via JDBC or JPA/Hibernate instead of in-memory maps
- Spring Boot + Spring Security (BCrypt, CSRF tokens, roles) instead of the built-in JDK server
- Product images, payment gateway, order history page for customers, input pagination
