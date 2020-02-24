package mpi.eudico.client.annotator.player;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A thread class to handle the playing of a selection.
 * It regularly checks the media player and stops play back the 
 * moment the interval end time is reached.
 */
public class PlaySelectionThread extends Thread {
	private ElanMediaPlayer player;
	private AtomicBoolean playSelectionFlag;
	private final long selectionStopTime;
	private final int sleepTime;
	
	/**
	 * Constructor.
	 * @param player the player to check
	 * @param playSelectionFlag a flag indicating whether the player is already or still playing
	 * the selection 
	 * @param selectionStopTime the end time of the interval, the position where to stop the player
	 * @param sleepTime number of milliseconds to wait before checking the player again.
	 */
	public PlaySelectionThread(ElanMediaPlayer player, AtomicBoolean playSelectionFlag, 
			long selectionStopTime, int sleepTime) {
		this.player = player;
		this.playSelectionFlag = playSelectionFlag;
		this.selectionStopTime = selectionStopTime;
		this.sleepTime = sleepTime;
	}

	@Override
	public void run() {
		// wait until the player is started
		while (!playSelectionFlag.get()) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException ie) {}
		}
		
		while (!isInterrupted() && playSelectionFlag.get()) {
			if (player.getMediaTime() >= selectionStopTime) {
				player.stop();
				
				if (player.getMediaTime() != selectionStopTime) {
					player.setMediaTime(selectionStopTime);
				}
				break;//?? stop() should set the flag to false
			}
			
			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException ie) {
				
			}
		}
		
		player = null;
		playSelectionFlag = null;
	}
	
	
}