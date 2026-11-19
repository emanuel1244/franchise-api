package com.example.franchise.infrastructure.adapter.in.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class Requests {

    private Requests() {
    }

    public record NameRequest(@NotBlank @Size(max = 200) String name) {
    }

    public record CreateProductRequest(@NotBlank @Size(max = 200) String name,
                                       @NotNull @Min(0) Integer stock) {
    }

    public record StockRequest(@NotNull @Min(0) Integer stock) {
    }
}
