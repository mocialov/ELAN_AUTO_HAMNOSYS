package nl.mpi.avf.player;
/**
 * A class for JAVFPLayer exceptions. Does not add anything to
 * the {@link Exception} implementation.
 * 
 * @author Han Sloetjes
 */
@SuppressWarnings("serial")
public class JAVFPlayerException extends Exception {

	public JAVFPlayerException() {
		super();
	}

	public JAVFPlayerException(String arg0) {
		super(arg0);
	}

	public JAVFPlayerException(Throwable arg0) {
		super(arg0);
	}

	public JAVFPlayerException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

}
