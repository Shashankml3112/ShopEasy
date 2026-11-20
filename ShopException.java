package com.shop;

/**
 * Thrown when a user does something the business rules do not allow
 * (e.g. adding more items than are in stock). The message is safe to show to the user.
 */
public class ShopException extends RuntimeException {

    public ShopException(String message) {
        super(message);
    }
}
