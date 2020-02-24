package mpi.eudico.client.annotator.player;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
z * A class that checks at regular intervals if a player has reached the end
 * of the media at and then calls the player's {@link ElanMediaPlayer#stop()} 
 * method.
 * An end of media buffer can be specified, so that the player can be stopped
 * before the actual end of media to prevent the player from either repeating
 * play back from the start or unloading the (native) player.
 */
public class EndOfMediaWatcher extends Thread {
	private final int SLEEP;
	private ElanMediaPlayer player;
	private ReentrantLock playLock;
	private Condition playCondition;
	private AtomicBoolean playingFlag;
	private long endOfMediaBufferMs = 0;
	
	/**
	 * A thread that checks if the player reaches the end of the media 
	 * to stop the player (and therefore connected controllers) at that point.
	 * 
	 * @param player the ELAN media player
	 * @param playLock a lock to acquire
	 * @param playCondition the play condition, signaled by the player
	 * @param playingFlag a flag that reflects the 'is playing' state according
	 *  to the ELAN media player 
	 * @param sleepTime the sleep time for the thread, determining how often the 
	 * media time should be checked
	 */
	public EndOfMediaWatcher(ElanMediaPlayer player,
			ReentrantLock playLock, Condition playCondition, 
			AtomicBoolean playingFlag, int sleepTime) {
		this.player = player;
		this.playLock = playLock;
		this.playCondition = playCondition;
		this.playingFlag = playingFlag;
		SLEEP = sleepTime;
	}
	
	/**
	 * Sets the number of milliseconds to stop play back before the 
	 * end-of-media. This will only work more or less reliably if
	 * the value is greater than or equal to the sleep time of this
	 * thread.
	 *  
	 * @param bufferMs the number of milliseconds to stop before the 
	 * end of media is reached
	 */
	public void setEndOfMediaBufferMs(long bufferMs) {
		endOfMediaBufferMs = bufferMs;
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			// before acquiring the lock (again, after an unlock), wait a moment
			try {
				Thread.sleep(40);
			} catch (InterruptedException iie) {}
			
			playLock.lock();
			try {
				if (!playingFlag.get()) {
					// stopped? check if the player is playing or should be stopped
					try {
						playCondition.await();
						
						while (playingFlag.get()) {
							
							if (player.getMediaTime() >= player.getMediaDuration() - endOfMediaBufferMs) {
								player.stop();
								//break;
							}
							
							try {
								Thread.sleep(SLEEP);
							} catch (InterruptedException iie) {
								// log...
							}
						}// end of play loop, the player flag has been set to false						
					} catch (InterruptedException ie) {
						// log
					}
				} else {
					// the player flag is true but the playCondition has not been signaled yet?
					try {
						Thread.sleep(40);
					} catch (InterruptedException iie) {
					}
				}
			} finally {
				playLock.unlock();
			}
		}
	}

}
