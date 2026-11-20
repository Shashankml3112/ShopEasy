package com.shop;

import java.math.BigDecimal;
import java.time.Instant;

/** A placed order (created at checkout). */
public record Order(int id, String username, BigDecimal total, int items, Instant placedAt) {
}
