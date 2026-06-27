package com.pps.profilesystem.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.util.HashMap;
import java.util.Map;

/**
 * Ensures /api/** errors return JSON with a message (not an HTML error page).
 */
@RestControllerAdvice(annotations = RestController.class)
public class RestApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("API error", e);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", rootMessage(e));
        return ResponseEntity.internalServerError().body(body);
    }

    /**
     * Handle SSE client disconnects silently.
     * This prevents HttpMessageNotWritableException when trying to write JSON
     * to a text/event-stream response that has already been closed.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncDisconnect(AsyncRequestNotUsableException ex) {
        log.debug("SSE client disconnected asynchronously: {}", ex.getMessage());
    }

    private static String rootMessage(Throwable t) {
        if (t == null) return "Unknown error";
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : root.getClass().getSimpleName();
    }
}
