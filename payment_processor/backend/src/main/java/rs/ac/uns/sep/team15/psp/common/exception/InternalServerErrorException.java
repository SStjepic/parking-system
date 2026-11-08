package rs.ac.uns.sep.team15.psp.common.exception;

public class InternalServerErrorException extends RuntimeException {

    public InternalServerErrorException() {
        super("Internal server error");
    }

    public InternalServerErrorException(String message) {
        super(message);
    }

    public InternalServerErrorException(String message, Throwable cause) {
        super(message, cause);
    }

}
