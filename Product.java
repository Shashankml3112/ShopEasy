package com.shop;

import java.math.BigDecimal;

/**
 * A product for sale. It is immutable (a record): to change price or stock we
 * replace it with a new Product, which keeps concurrent reads safe.
 * BigDecimal is used for money because double has rounding errors.
 */
public record Product(int id, String name, String description, BigDecimal price, int stock, String imageUrl) {
}
