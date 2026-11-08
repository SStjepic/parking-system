package rs.ac.uns.sep.team15.parkingsystem.common.exception;

public class BadRequestException extends RuntimeException {

    public BadRequestException() {
        super("Bad request");
    }

    public BadRequestException(String message) {
        super(message);
    }

}
