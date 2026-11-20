package com.shop;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Small helpers that build the HTML shell shared by every page. */
public final class Html {

    private Html() {
    }

    /** Escapes user-supplied text so it can never be run as HTML/JavaScript (prevents XSS). */
    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Formats money as Indian rupees, e.g. 1499 -> "₹1499.00". */
    public static String money(BigDecimal v) {
        return "₹" + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String page(String title, User user, int cartCount, String notice, String error, String body) {
        StringBuilder nav = new StringBuilder();
        nav.append("<a href='/'>Home</a>");
        if (user == null) {
            nav.append("<a href='/login'>Login</a><a href='/register'>Register</a>");
        } else {
            if (user.isAdmin()) {
                nav.append("<a href='/admin'>Admin</a>");
            } else {
                nav.append("<a href='/cart'>Cart (").append(cartCount).append(")</a>");
            }
            nav.append("<form class='inline' method='post' action='/logout'><button class='link'>Logout (")
                    .append(esc(user.username())).append(")</button></form>");
        }

        StringBuilder flash = new StringBuilder();
        if (notice != null && !notice.isBlank()) {
            flash.append("<div class='flash ok'>").append(esc(notice)).append("</div>");
        }
        if (error != null && !error.isBlank()) {
            flash.append("<div class='flash err'>").append(esc(error)).append("</div>");
        }

        return "<!doctype html><html lang='en'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + esc(title) + " - MY Store</title><style>" + CSS + "</style></head><body>"
                + "<header><a class='brand' href='/'>MY Store</a><nav>" + nav + "</nav></header>"
                + "<main>" + flash + body + "</main></body></html>";
    }

    private static final String CSS = """
            *{box-sizing:border-box}
            body{margin:0;font-family:system-ui,Segoe UI,Arial,sans-serif;background:#f3f4f6;color:#1f2937}
            header{background:#1b2a41;color:#fff;display:flex;justify-content:space-between;align-items:center;padding:12px 24px}
            .brand{color:#f5a623;font-weight:700;font-size:1.4rem;text-decoration:none}
            nav{display:flex;gap:18px;align-items:center}
            nav a,button.link{color:#fff;text-decoration:none;background:none;border:0;font:inherit;cursor:pointer;padding:0}
            main{max-width:1000px;margin:24px auto;padding:0 16px}
            .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:16px}
            .card{background:#fff;border-radius:8px;padding:16px;box-shadow:0 1px 3px rgba(0,0,0,.1)}
            .card h3{margin:0 0 6px}
            .muted{color:#6b7280;font-size:.9rem}
            .price{font-size:1.25rem;font-weight:700}
            .ok{color:#15803d}.bad{color:#b91c1c}
            table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden;margin-bottom:16px}
            th,td{padding:10px;border-bottom:1px solid #e5e7eb;text-align:left;vertical-align:middle}
            th{background:#e5e7eb}
            input{padding:8px;border:1px solid #cbd5e1;border-radius:6px;font:inherit}
            input[type=number]{width:90px}
            button{padding:8px 14px;border:0;border-radius:6px;background:#f5a623;color:#1f2937;font:inherit;font-weight:600;cursor:pointer}
            button:disabled{background:#d1d5db;cursor:not-allowed}
            button.danger{background:#dc2626;color:#fff}
            button.primary{background:#1b2a41;color:#fff;padding:12px 22px}
            form.inline{display:inline-flex;gap:6px;align-items:center;margin:0}
            form.stack{display:flex;flex-direction:column;gap:10px;max-width:380px;margin-bottom:24px}
            form.search{margin-bottom:16px;display:flex;gap:8px}
            form.search input{flex:1}
            .flash{padding:10px 14px;border-radius:6px;margin-bottom:16px}
            .flash.ok{background:#dcfce7;color:#166534}
            .flash.err{background:#fee2e2;color:#991b1b}
            .total{font-size:1.2rem;text-align:right}
            .thumb{width:100%;height:160px;object-fit:contain;background:#fff;border-radius:6px;margin-bottom:8px;display:block}
            .thumb.ph{display:flex;align-items:center;justify-content:center;background:#e5e7eb;color:#6b7280;font-size:3rem;font-weight:700}
            img.mini{width:48px;height:48px;object-fit:contain;vertical-align:middle;margin-right:8px;border-radius:4px}
            """;
}
