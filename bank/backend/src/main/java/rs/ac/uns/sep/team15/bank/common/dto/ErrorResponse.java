package rs.ac.uns.sep.team15.bank.common.dto;

import java.util.Map;

public record ErrorResponse(int code, String message, Map<String, String> errors) {

    public ErrorResponse(int code, String message) {
        this(code, message, Map.of());
    }
}
