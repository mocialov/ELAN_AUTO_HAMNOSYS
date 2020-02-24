package mpi.eudico.client.annotator.player;

/**
 * Exception that is thrown when it was not possible to create an
 * ElanMediaPlayer
 */
@SuppressWarnings("serial")
public class NoPlayerException extends Exception {
    /**
     * Creates a new NoPlayerException instance
     *
     * @param message the message describing (the reason for) the exception
     */
    public NoPlayerException(String message) {
        super(message);
    }
}
