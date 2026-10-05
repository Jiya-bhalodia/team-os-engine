package com.placement.teama.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private T data;
    private ApiError error;
    private MetaResponse meta;

    public static <T> ApiResponse<T> success(T data, String correlationId) {
        return ApiResponse.<T>builder()
                .data(data)
                .meta(new MetaResponse(correlationId, "v1"))
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, String correlationId) {
        return ApiResponse.<T>builder()
                .error(new ApiError(code, message, java.util.Collections.emptyList()))
                .meta(new MetaResponse(correlationId, "v1"))
                .build();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MetaResponse {
        private String correlationId;
        private String apiVersion;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ApiError {
        private String code;
        private String message;
        private java.util.List<Object> details;
    }
}