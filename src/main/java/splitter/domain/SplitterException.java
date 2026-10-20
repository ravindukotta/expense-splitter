package splitter.domain;

/** Signals a user-facing error; its message is printed after "Error: ". */
public class SplitterException extends RuntimeException {
    public SplitterException(String message) {
        super(message);
    }
}
