package io.qalipsis.plugins.jackson.config

import io.qalipsis.api.annotations.Spec
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.validation.constraints.NotNull

/**
 * Configuration describing the resource to read to receive the data.
 *
 * @author Eric Jessé
 */
@Spec
internal data class SourceConfiguration(
        var url: @NotNull URL? = null,
        var encoding: @NotNull Charset = StandardCharsets.UTF_8
)