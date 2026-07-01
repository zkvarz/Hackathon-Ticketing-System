package com.dataart.tickets.config;

import com.dataart.tickets.common.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI / Swagger configuration (HTS-050). This is <em>code-first</em>: springdoc introspects the
 * existing {@code @RestController}s to build the spec; nothing here re-describes the endpoints. Two
 * things the automatic scan can't infer are added:
 *
 * <ol>
 *   <li>API {@link Info} (title/version) plus a description of the cookie-session + CSRF scheme; and</li>
 *   <li>the standardized {@link ApiError} model — it is produced by the exception handler /
 *       security filter chain, never as a controller return type, so springdoc would not otherwise
 *       see it. The customizer registers its schema and attaches the common 4xx responses to every
 *       operation, so the generated contract (and the frontend's generated types) include it.</li>
 * </ol>
 *
 * <p>The docs endpoints are disabled in the {@code prod} profile (see {@code application-prod.yml})
 * and are only added to the security allowlist while enabled (see {@code SecurityConfig}).
 */
@Configuration
public class OpenApiConfig {

    private static final String ERROR_REF = "#/components/schemas/ApiError";

    @Bean
    OpenAPI ticketsOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Hackathon Ticketing System API")
                .version("v1")
                .description("""
                        REST API for the Kanban ticket tracker. Authentication is a session cookie \
                        established by POST /api/auth/login; state-changing requests (POST/PUT/PATCH/\
                        DELETE) must echo the XSRF-TOKEN cookie back in the X-XSRF-TOKEN header \
                        (CSRF). Every 4xx/5xx response uses the standardized ApiError body — branch \
                        on its stable `code`."""));
    }

    /**
     * Register the {@link ApiError} schema, attach the common error responses to every operation,
     * and mark response-schema properties as required (records always serialize every field, so the
     * generated client can treat them as always-present; nullable ones stay nullable).
     */
    @Bean
    OpenApiCustomizer standardErrorsCustomizer() {
        return openApi -> {
            Components components = openApi.getComponents();

            // ApiError + its nested FieldError are not referenced by any controller return type, so
            // register them explicitly from the class.
            Map<String, Schema> errorSchemas = ModelConverters.getInstance().readAll(ApiError.class);
            errorSchemas.forEach(components::addSchemas);

            Content errorContent = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref(ERROR_REF)));

            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(op -> {
                        ApiResponses responses = op.getResponses();
                        addError(responses, "400", "Validation failed (VALIDATION_FAILED)", errorContent);
                        addError(responses, "401", "Authentication required (UNAUTHENTICATED)", errorContent);
                        addError(responses, "403", "Forbidden, incl. CSRF failure (FORBIDDEN)", errorContent);
                        addError(responses, "404", "Not found (NOT_FOUND)", errorContent);
                        addError(responses, "409", "Conflict (e.g. NAME_TAKEN, TEAM_HAS_CHILDREN)", errorContent);
                    }));

            components.getSchemas().forEach((name, schema) -> {
                if (name.endsWith("Response") || name.equals("ApiError") || name.equals("FieldError")) {
                    markAllPropertiesRequired(schema);
                }
            });
        };
    }

    private static void addError(ApiResponses responses, String code, String desc, Content content) {
        if (!responses.containsKey(code)) {
            responses.addApiResponse(code, new ApiResponse().description(desc).content(content));
        }
    }

    // A record always serializes every component, so every property key is present in the JSON
    // (nullable fields carry null). Marking them all `required` makes generated clients treat the
    // keys as always-present while `nullable` still governs whether the value may be null.
    @SuppressWarnings("rawtypes")
    private static void markAllPropertiesRequired(Schema schema) {
        Map<String, Schema> props = schema.getProperties();
        if (props != null && !props.isEmpty()) {
            schema.setRequired(new ArrayList<>(props.keySet()));
        }
    }
}
