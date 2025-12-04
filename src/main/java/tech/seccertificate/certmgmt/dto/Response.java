package tech.seccertificate.certmgmt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.seccertificate.certmgmt.exception.ErrorResponse;

import java.util.List;

/**
 * Unified API Response structure for all HTTP endpoints.
 * 
 * <p>This class provides a consistent response format across all API endpoints,
 * supporting both success and error scenarios.
 * 
 * <p>Success Response (200/201):
 * <pre>{@code
 * {
 *   "success": true,
 *   "message": "Operation completed successfully",
 *   "data": { ... },
 *   "details": null
 * }
 * }</pre>
 * 
 * <p>Error Response (400/401/403/404/500):
 * <pre>{@code
 * {
 *   "success": false,
 *   "message": "Specific error description",
 *   "error": {
 *     "errorCode": "APP_SPECIFIC_CODE",
 *     "details": null,
 *     "data": null
 *   },
 *   "details": null
 * }
 * }</pre>
 * 
 * <p>Advanced Error Response (with validation details):
 * <pre>{@code
 * {
 *   "success": false,
 *   "message": "Specific error description",
 *   "error": {
 *     "type": "Validation Error",
 *     "errorCode": "APP_SPECIFIC_CODE",
 *     "details": ["email is required", "phone number is invalid"],
 *     "data": { ... }
 *   },
 *   "details": ["Additional context information"]
 * }
 * }</pre>
 * 
 * <p>Note: The error field uses {@link ErrorResponse} which has been extended
 * to support the unified API response format while maintaining backward compatibility
 * with RFC 7807 Problem Details format.
 * 
 * @param <T> The type of data payload in the response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response<T> {
    
    /**
     * Indicates whether the operation was successful.
     * <ul>
     *   <li>{@code true} for successful operations (HTTP 200, 201)</li>
     *   <li>{@code false} for error scenarios (HTTP 400, 401, 403, 404, 500)</li>
     * </ul>
     */
    private Boolean success;
    
    /**
     * Human-readable message describing the operation result or error.
     * <p>
     * Examples:
     * <ul>
     *   <li>Success: "Operation completed successfully", "Customer created successfully"</li>
     *   <li>Error: "Customer not found", "Validation failed", "Unauthorized access"</li>
     * </ul>
     */
    private String message;
    
    /**
     * The response payload data (only present for successful operations).
     * <p>
     * This can be:
     * <ul>
     *   <li>A single object (e.g., CustomerResponse, CertificateResponse)</li>
     *   <li>A list of objects (e.g., List&lt;CustomerResponse&gt;)</li>
     *   <li>Any other data structure relevant to the endpoint</li>
     * </ul>
     * <p>
     * This field is {@code null} for error responses.
     */
    private T data;
    
    /**
     * Error details (only present for error responses).
     * <p>
     * Uses the extended {@link ErrorResponse} class which supports both
     * RFC 7807 format and the unified API response format.
     * This field is {@code null} for successful responses.
     */
    private ErrorResponse error;
    
    /**
     * Additional details or context information (optional).
     * <p>
     * Can be used for:
     * <ul>
     *   <li>Success responses: Additional metadata, warnings, or informational messages</li>
     *   <li>Error responses: Additional context or supplementary information</li>
     * </ul>
     * <p>
     * This can be a List of strings, a Map, or any other object structure.
     * Examples:
     * <ul>
     *   <li>["Warning: Resource will expire in 7 days", "Consider upgrading plan"]</li>
     *   <li>{"totalRecords": 150, "filteredRecords": 25}</li>
     * </ul>
     */
    private Object details;
    
    /**
     * Creates a successful response with data.
     * 
     * @param <T> The type of data
     * @param message Success message
     * @param data Response data
     * @return Response instance
     */
    public static <T> Response<T> success(String message, T data) {
        return Response.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }
    
    /**
     * Creates a successful response with default message.
     * 
     * @param <T> The type of data
     * @param data Response data
     * @return Response instance
     */
    public static <T> Response<T> success(T data) {
        return success("Operation completed successfully", data);
    }
    
    /**
     * Creates a successful response with data and details.
     * 
     * @param <T> The type of data
     * @param message Success message
     * @param data Response data
     * @param details Additional details or context
     * @return Response instance
     */
    public static <T> Response<T> success(String message, T data, Object details) {
        return Response.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .details(details)
                .build();
    }
    
    /**
     * Creates an error response.
     * 
     * @param <T> The type parameter (unused for errors)
     * @param message Error message
     * @param errorCode Application-specific error code
     * @return Response instance
     */
    public static <T> Response<T> error(String message, String errorCode) {
        return Response.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorResponse.builder()
                        .errorCode(errorCode)
                        .build())
                .build();
    }
    
    /**
     * Creates an error response with details.
     * 
     * @param <T> The type parameter (unused for errors)
     * @param message Error message
     * @param errorCode Application-specific error code
     * @param details Additional details or context
     * @return Response instance
     */
    public static <T> Response<T> error(String message, String errorCode, Object details) {
        return Response.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorResponse.builder()
                        .errorCode(errorCode)
                        .build())
                .details(details)
                .build();
    }
    
    /**
     * Creates an advanced error response with type and details.
     * 
     * @param <T> The type parameter (unused for errors)
     * @param message Error message
     * @param errorCode Application-specific error code
     * @param type Error type (e.g., "Validation Error", "Business Logic Error")
     * @param errorDetails List of detailed error messages (for error object)
     * @return Response instance
     */
    public static <T> Response<T> error(String message, String errorCode, String type, List<String> errorDetails) {
        return Response.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorResponse.builder()
                        .errorCode(errorCode)
                        .errorType(type)
                        .errorDetails(errorDetails)
                        .build())
                .build();
    }
    
    /**
     * Creates an advanced error response with type, error details, and top-level details.
     * 
     * @param <T> The type parameter (unused for errors)
     * @param message Error message
     * @param errorCode Application-specific error code
     * @param type Error type
     * @param errorDetails List of detailed error messages (for error object)
     * @param details Top-level additional details or context
     * @return Response instance
     */
    public static <T> Response<T> error(String message, String errorCode, String type, 
                                           List<String> errorDetails, Object details) {
        return Response.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorResponse.builder()
                        .errorCode(errorCode)
                        .errorType(type)
                        .errorDetails(errorDetails)
                        .build())
                .details(details)
                .build();
    }
    
    /**
     * Creates an advanced error response with type, error details, error data, and top-level details.
     * 
     * @param <T> The type parameter (unused for errors)
     * @param message Error message
     * @param errorCode Application-specific error code
     * @param type Error type
     * @param errorDetails List of detailed error messages (for error object)
     * @param errorData Additional error data in error object (e.g., field-level errors)
     * @param details Top-level additional details or context
     * @return Response instance
     */
    public static <T> Response<T> error(String message, String errorCode, String type, 
                                           List<String> errorDetails, Object errorData, Object details) {
        return Response.<T>builder()
                .success(false)
                .message(message)
                .error(ErrorResponse.builder()
                        .errorCode(errorCode)
                        .errorType(type)
                        .errorDetails(errorDetails)
                        .errorData(errorData)
                        .build())
                .details(details)
                .build();
    }
}
