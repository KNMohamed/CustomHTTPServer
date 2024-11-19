package com.http;

import java.util.Map;
import java.util.TreeMap;
import java.util.Set;

public class Headers implements Cloneable {

    private final Map<String, String> storage;

    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String USER_AGENT = "User-Agent";

    public Headers() {
        this.storage = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    public Headers(Headers headers) {
        this();

        this.storage.putAll(headers.storage);
    }

    public int contentLength() {
        return getAsInteger(CONTENT_LENGTH, 0);
    }

    public String userAgent() {
        return storage.get(USER_AGENT);
    }

    public String acceptEncoding() {
        return storage.get(ACCEPT_ENCODING);
    }

    public Headers put(String key, String value) {
        storage.put(key, value);
        return this;
    }

    public Set<Map.Entry<String, String>> entrySet() {
        return storage.entrySet();
    }

    private int getAsInteger(String key, int defaultValue) {
        final var raw = storage.get(key);
        return raw != null ? Integer.parseInt(raw) : defaultValue;
    }

    @Override
    public Headers clone() {
        return new Headers(this);
    }
}
