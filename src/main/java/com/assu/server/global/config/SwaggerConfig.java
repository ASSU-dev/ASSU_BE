package com.assu.server.global.config;

import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.apiPayload.code.status.SwaggerErrorCodes;
import com.assu.server.global.exception.annotation.ApiErrorCodeExamples;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI stewAPI() {
        Info info = new Info()
                .title("ASSU API")
                .description("ASSU API 명세서")
                .version("1.0.0");

        String jwtSchemeName = "JWT TOKEN";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);
        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        return new OpenAPI()
                .addServersItem(new Server().url("/"))
                .info(info)
                .addSecurityItem(securityRequirement)
                .components(components);
    }

    @Bean
    public OperationCustomizer customExceptionOperationCustomizer() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            ApiErrorCodeExamples description = handlerMethod.getMethodAnnotation(ApiErrorCodeExamples.class);
            if (description != null) {
                addErrorResponseExamples(operation, description.value());
            }
            return operation;
        };
    }

    private void addErrorResponseExamples(Operation operation, SwaggerErrorCodes swaggerErrorCodes) {
        ApiResponses responses = operation.getResponses();

        Map<Integer, List<ExampleHolder>> byHttpStatus = swaggerErrorCodes.getErrorCodes().stream()
                .map(errorStatus -> new ExampleHolder(
                        toExample(errorStatus),
                        errorStatus.name(),
                        errorStatus.getHttpStatus().value()))
                .collect(groupingBy(ExampleHolder::httpStatus));

        byHttpStatus.forEach((httpStatus, holders) -> {
            MediaType mediaType = new MediaType();
            holders.forEach(holder -> mediaType.addExamples(holder.name(), holder.example()));

            Content content = new Content().addMediaType("application/json", mediaType);
            responses.addApiResponse(String.valueOf(httpStatus), new ApiResponse().description("").content(content));
        });
    }

    private Example toExample(ErrorStatus errorStatus) {
        Example example = new Example();
        example.description(errorStatus.getMessage());
        example.setValue(Map.of(
                "isSuccess", false,
                "code", errorStatus.getCode(),
                "message", errorStatus.getMessage()
        ));
        return example;
    }

    private record ExampleHolder(Example example, String name, int httpStatus) {
    }
}
