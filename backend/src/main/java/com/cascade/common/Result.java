package com.cascade.common;

import java.util.Optional;
import java.util.function.Function;

public sealed interface Result<T> permits Result.Success, Result.Failure {

    boolean isSuccess();

    Optional<T> value();

    Optional<String> error();

    <U> Result<U> map(Function<T, U> fn);

    record Success<T>(T val) implements Result<T> {
        public boolean isSuccess() { return true; }
        public Optional<T> value() { return Optional.of(val); }
        public Optional<String> error() { return Optional.empty(); }
        public <U> Result<U> map(Function<T, U> fn) { return Result.ok(fn.apply(val)); }
    }

    record Failure<T>(String message) implements Result<T> {
        public boolean isSuccess() { return false; }
        public Optional<T> value() { return Optional.empty(); }
        public Optional<String> error() { return Optional.of(message); }
        @SuppressWarnings("unchecked")
        public <U> Result<U> map(Function<T, U> fn) { return (Result<U>) this; }
    }

    static <T> Result<T> ok(T value) { return new Success<>(value); }

    static <T> Result<T> fail(String message) { return new Failure<>(message); }
}
