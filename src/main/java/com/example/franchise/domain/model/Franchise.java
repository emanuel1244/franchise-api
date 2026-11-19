package com.example.franchise.domain.model;

import java.util.List;

public record Franchise(String id, String name, List<Branch> branches) {

    public Franchise {
        requireValidName(name);
        branches = List.copyOf(branches);
    }

    public static String requireValidName(String name) {
        return requireText(name, "El nombre de la franquicia es obligatorio");
    }

    static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
