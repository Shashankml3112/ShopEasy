package com.shop;

/**
 * A registered account. We never store the real password, only a salted SHA-256 hash.
 */
public record User(String username, String salt, String passwordHash, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
