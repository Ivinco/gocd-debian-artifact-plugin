package io.artifactkeeper.gocd.debian;

import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.request.DefaultGoApiRequest;

import java.util.function.Consumer;

/**
 * Streams progress lines into the job console via the
 * {@code go.processor.artifact.console-log} processor. Best-effort: a
 * console line is a convenience, not part of the plugin's correctness
 * contract, so any failure to reach the processor is swallowed rather than
 * failing the publish/fetch it was only trying to narrate.
 */
final class ConsoleLogger implements Consumer<String> {

    private final GoApplicationAccessor accessor;
    private final GoPluginIdentifier pluginIdentifier;

    ConsoleLogger(GoApplicationAccessor accessor, GoPluginIdentifier pluginIdentifier) {
        this.accessor = accessor;
        this.pluginIdentifier = pluginIdentifier;
    }

    @Override
    public void accept(String message) {
        try {
            DefaultGoApiRequest request = new DefaultGoApiRequest(
                    "go.processor.artifact.console-log", "1.0", pluginIdentifier);
            request.setRequestBody("{\"logLevel\":\"INFO\",\"message\":" + JsonUtil.quote(message) + "}");
            accessor.submit(request);
        } catch (RuntimeException ignored) {
            // Best-effort only, see class comment.
        }
    }
}
