package nl.mpi.avf.player;

public interface JAVFLogLevel {
	
	public static final int ALL = 0;
	
	public static final int FINE = 3;
	
	public static final int INFO = 5;
	
	public static final int WARNING = 8;
	
	public static final int OFF = 10;

	/*
	ALL(0),
	FINE(3),
	INFO(5),
	WARNING(8),
	OFF(10);
	
	public int level;
	private JAVFLogLevel(int level) {
		this.level = level;
	}
	*/
}