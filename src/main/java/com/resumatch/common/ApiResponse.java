package com.resumatch.common;

import java.util.List;

public class ApiResponse<T> {
    public boolean success;
    public String message;
    public T data;
    public List<String> errors;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }
    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = ok(data);
        r.message = message;
        return r;
    }
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.message = message;
        return r;
    }
    public static <T> ApiResponse<T> errors(String message, List<String> errors) {
        ApiResponse<T> r = error(message);
        r.errors = errors;
        return r;
    }
}
