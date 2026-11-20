package com.shop;

import java.math.BigDecimal;

/** One row in a customer's cart: a product and how many of it. */
public record CartLine(Product product, int qty) {

    public BigDecimal lineTotal() {
        return product.price().multiply(BigDecimal.valueOf(qty));
    }
}
