package io.artifactkeeper.gocd.debian;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;

/** Shared Gson instance plus tiny helpers used across the plugin's handlers. */
final class JsonUtil {

    static final Gson GSON = new GsonBuilder().create();

    private JsonUtil() {
    }

    /** JSON-escapes and quotes a single string value, e.g. for hand-built JSON bodies. */
    static String quote(String value) {
        return new JsonPrimitive(value).toString();
    }
}
