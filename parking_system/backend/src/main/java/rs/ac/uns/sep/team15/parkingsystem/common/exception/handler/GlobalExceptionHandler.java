package rs.ac.uns.sep.team15.parkingsystem.common.exception.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import rs.ac.uns.sep.team15.parkingsystem.common.dto.ErrorResponse;
import rs.ac.uns.sep.team15.parkingsystem.common.exception.BadRequestException;
import rs.ac.uns.sep.team15.parkingsystem.common.exception.InternalServerErrorException;
import rs.ac.uns.sep.team15.parkingsystem.common.exception.NotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, exception.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(400, exception.getMessage()));
    }

    @ExceptionHandler({InternalServerErrorException.class, Exception.class})
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Internal server error", exception);
        return ResponseEntity.internalServerError().body(new ErrorResponse(500, "Internal server error"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.put(error.getObjectName(), error.getDefaultMessage()));
        return handleExceptionInternal(exception,
                new ErrorResponse(status.value(), "Validation failed", errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        // Keep Spring MVC statuses and headers (404, 405, 415, etc.) in our JSON format.
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        String message = httpStatus != null ? httpStatus.getReasonPhrase() : "Request failed";
        Object response = body instanceof ErrorResponse ? body : new ErrorResponse(status.value(), message);
        return super.handleExceptionInternal(exception, response, headers, status, request);
    }
}
