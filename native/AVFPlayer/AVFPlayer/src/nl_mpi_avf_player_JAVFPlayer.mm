#import "nl_mpi_avf_player_JAVFPlayer.h"
#import "AVFBasePlayer.h"
#import "AVFCustomPlayer.h"
#import "AVFLog.h"
#import <jni.h>
#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

/*
 * This variant of the AV based player decodes video in AV and returns the images'
 * bytes to the caller (a player in Java). The name of this class stems from the time
 * when there was only one AVFoundation based player (this one) which delegates rendering
 * to the Java client. The variant with a AVPlayerLayer which is added to a Java
 * Canvas, was developed later (AVFNativePlayer).
 * Creates a AVFCustomPlayer for the specified media url (url as a string). This
 * player can decode sequences of video frames and transfer them to the Java part
 * of the application for the rendering.
 * Returns an id which is used for store and retrieve of the player in a global 
 * dictionary. If a player cannot be created 0 is returned.
 * Note: in case of a local file, remove file: protocol (otherwise there is a
 * problem with white spaces in the path)
 *
 * Class:     nl_mpi_avf_player_JAVFPlayer
 * Method:    initPlayer
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_nl_mpi_avf_player_JAVFPlayer_initPlayer
(JNIEnv *env, jobject callerObject, jstring mediaURL) {
    jlong nextId = 0;
    jboolean isCopy;
    const char *mediaURLChars = env->GetStringUTFChars(mediaURL, &isCopy);
    // convert jstring to NSString
    NSString *urlString = [NSString stringWithUTF8String:mediaURLChars];
    if ([AVFLog isLoggable:AVFLogInfo]) {
        NSLog(@"JAVFPlayer_initPlayer: media URL: %@", urlString);
    }

    NSURL *mediaNSURL = [NSURL fileURLWithPath:urlString isDirectory:NO];
    //NSURL *mediaNSURL = [NSURL URLWithString:urlString]; // works with http:// url's as opposed to the above
    //NSURL *mediaNSURL = [NSURL fileURLWithFileSystemRepresentation:mediaURLChars isDirectory:NO relativeToURL:NULL];
    //NSURL *mediaFileURL = [mediaNSURL filePathURL];

    
    id avfPlayer = [[AVFCustomPlayer alloc] initWithURL:mediaNSURL];
    // check success
    if (avfPlayer == NULL || (![avfPlayer hasAudio] && ![avfPlayer hasVideo])) {
        // throw file not supported exception
        //jclass exClass;
        const char *mess = "Could not create an AV Foundation player: the file has no audio or video tracks.";
        jclass exClass = env->FindClass("nl/mpi/avf/player/JAVFPlayerException");
            
        if (exClass != NULL) {
            env->ThrowNew(exClass, mess);
        }
        if (avfPlayer != NULL) {
            [avfPlayer releasePlayer];
        }
    }
    // store in global map
    if (avfPlayer != NULL) {
        if ([avfPlayer lastError] != NULL) {//??
            if ([AVFLog isLoggable:AVFLogWarning]) {
                NSLog(@"JAVFPlayer_initPlayer: error while creating player: %@", [avfPlayer lastError]);
            }
            nextId = 0;
            [avfPlayer releasePlayer];// clean up?
        } else {
            nextId = [AVFCustomPlayer createIdAndStorePlayer:avfPlayer];
        }
    }
    
    env->ReleaseStringUTFChars(mediaURL, mediaURLChars);
    if ([AVFLog isLoggable:AVFLogInfo]) {
        NSLog(@"JAVFPlayer_initPlayer: id of new player: %ld, has audio: %@, has video: %@", nextId, ([avfPlayer hasAudio] ? @"Y" : @"N"), ([avfPlayer hasVideo] ? @"Y" : @"N"));
    }
    return nextId;
}

/*
 * Removes the player from the player map and initiates release of resources.
 * Class:     nl_mpi_avf_player_JAVFPlayer
 * Method:    deletePlayer
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_JAVFPlayer_deletePlayer
(JNIEnv *env, jobject callerObject, jlong playerId) {
    AVFCustomPlayer *avfPlayer = [AVFCustomPlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL) {
        if ([AVFLog isLoggable:AVFLogInfo]) {
            NSLog(@"JAVFPlayer_deletePlayer: deleting player with id: %ld", playerId);
        }
        // remove from global map
        [AVFCustomPlayer removePlayerWithId:playerId];
        [avfPlayer releasePlayer];
        //delete(&avfPlayer);
    }
}

/*
 * Returns the number of bytes occupied by the pixels of one row of a video image.
 * The value is based on the number of pixels per row (the width) and the number 
 * of bytes per pixel.
 * Returns 0 in case there is no video or the value could not be extracted.
 *
 * Class:     nl_mpi_avf_player_JAVFPlayer
 * Method:    getBytesPerRow
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_nl_mpi_avf_player_JAVFPlayer_getBytesPerRow
(JNIEnv *env, jobject callerObject, jlong playerId) {
    AVFCustomPlayer *avfPlayer = [AVFCustomPlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL && [avfPlayer hasVideo]) {
        return (jint) [avfPlayer bytesPerRow];
    }
    
    return 0;
}

/*
 * Returns the number of bytes used to encode one pixel, this depends on
 * the color model, transparency etc.
 *
 * Class:     nl_mpi_avf_player_JAVFPlayer
 * Method:    getBytesPerPixel
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_nl_mpi_avf_player_JAVFPlayer_getBytesPerPixel
(JNIEnv *env, jobject callerObject, jlong playerId) {
    AVFCustomPlayer *avfPlayer = [AVFCustomPlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL && [avfPlayer hasVideo]) {
        return (jint) [avfPlayer bytesPerPixel];
    }
    
    return 1;
}

/*
 * Returns the width of a video image.
 *
 * Class:     nl_mpi_avf_player_JAVFPlayer
 * Method:    getVideoImageWidth
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_nl_mpi_avf_player_JAVFPlayer_getVideoImageWidth
(JNIEnv *env, jobject callerObject, jlong playerId) {
    AVFCustomPlayer *avfPlayer = [AVFCustomPlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL && [avfPlayer hasVideo]) {
        return [avfPlayer videoImageWidth];
    }
    
    return 0;
}

/*
 * Returns the height of a video imge.
 *
 * Class:     nl_mpi_avf_player_JAVFPlayer
 * Method:    getVideoImageHeight/Users/Shared/MPI/demo/pear/pear.wav
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_nl_mpi_avf_player_JAVFPlayer_getVideoImageHeight
(JNIEnv *env, jobject callerObject, jlong playerId) {
    AVFCustomPlayer *avfPlayer = [AVFCustomPlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL && [avfPlayer hasVideo]) {
        return [avfPlayer videoImageHeight];
    }
    
    return 0;
}

/*
 * Returns a description of the pixel format if there is video, NULL otherwise.
 * Not implemented yet. Could return a string representation of the 
 * current output setting for kCVPixelBufferPixelFormatTypeKey e.g.
 * kCVPixelFormatType_32BGRA
 *
 * Class:     nl_mpi_avf_player_JAVFPlayer
 * Method:    getPixelFormat
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_nl_mpi_avf_player_JAVFPlayer_getPixelFormat
(JNIEnv *env, jobject callerObject, jlong playerId) {
    AVFCustomPlayer *avfPlayer = [AVFCustomPlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL && [avfPlayer hasVideo]) {
        NSString *formatStringNS = [avfPlayer getPixelFormat];
        const char *formatChars = [formatStringNS UTF8String];
        jstring formatJString = env->NewStringUTF(formatChars);
        
        return formatJString;
    }
    
    return NULL;
}

/*
 * Sets the pixel format to use when decoding video frames. 
 * Returns true if the change succeeded, false otherwise.
 *
 * Class:     nl_mpi_avf_player_JAVFPlayer
 * Method:    setPixelFormat
 * Signature: (JLjava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_nl_mpi_avf_player_JAVFPlayer_setPixelFormat
(JNIEnv *env, jobject callerObject, jlong playerId, jstring formatString) {
    AVFCustomPlayer *avfPlayer = [AVFCustomPlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL && [avfPlayer hasVideo]) {
        jboolean isCopy;
        const char *formatChars = env->GetStringUTFChars(formatString, &isCopy);
        // convert jstring to NSString
        NSString *formatStringNS = [NSString stringWithUTF8String:formatChars];
        
        BOOL successFlag = [avfPlayer setPixelFormat: formatStringNS];
        
        env->ReleaseStringUTFChars(formatString, formatChars);
        
        return (jboolean) successFlag;
    }
    
    return JNI_FALSE;
}

/*
 * Retrieves a sequence of video frames for the interval beginTime - endTime, time values in milliseconds.
 * The bytes of the decoded images are stored in the specified byteBuffer, a direct java.nio.ByteBuffer instance.
 * The buffer should be created by the caller and its capacity should be sufficient for storing the images.
 * The required capacity can be calculated based on the number of bytes per frame, on the number of frames per second (the frame duration) and the length of the interval.
 * 
 * Returns an array of time values in seconds, each double corresponds to the start time of a frame in the buffer.
 *
 * Class:     nl_mpi_avf_player_JAVFPlayer
 * Method:    getVideoFrameSequence
 * Signature: (JJJLjava/nio/ByteBuffer;)[D
 */
JNIEXPORT jdoubleArray JNICALL Java_nl_mpi_avf_player_JAVFPlayer_getVideoFrameSequence
(JNIEnv *env, jobject callerObject, jlong playerId, jlong beginTime, jlong endTime, jobject byteBuffer) {
    AVFCustomPlayer *avfPlayer = [AVFCustomPlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL && [avfPlayer hasVideo]) {
        int numFrames = ((endTime - beginTime) / [avfPlayer frameDurationMs]) + 2;// add some extra
        double outIndexArray [numFrames];
        // initialize with -1?
        for (int i = 0; i < numFrames; i++) {
            outIndexArray[i] = -1;
        }
        
        char * bufAddress = (char *) env->GetDirectBufferAddress(byteBuffer);
        jlong bufferLength = env->GetDirectBufferCapacity(byteBuffer);
        
        long bytesRead = [avfPlayer getFrameSequenceFromTime:beginTime toTime:endTime outputBuffer:bufAddress bufferSize:bufferLength outputIndexArray:outIndexArray outputArraySize:numFrames];
        
        if ([AVFLog isLoggable:AVFLogFine]) {
          NSLog(@"JAVFPlayer_getVideoFrameSequence: Number of bytes read for sequence (%ld - %ld): %ld", beginTime, endTime, bytesRead);
        }
        
        if (bytesRead < [avfPlayer bytesPerFrame]) {
            return NULL;
        }
        
        jdoubleArray outjdArray = env->NewDoubleArray(numFrames);
        env->SetDoubleArrayRegion(outjdArray, 0, numFrames, outIndexArray);
        
        return outjdArray;
    }
    
    return NULL;
}

/*
 * Retrieves a sequence of video frames for the interval beginTime - endTime, time values in seconds.
 * The bytes of the decoded images are stored in the specified byteBuffer, a direct java.nio.ByteBuffer instance.
 * The buffer should be created by the caller and its capacity should be sufficient for storing the images.
 * The required capacity can be calculated based on the number of bytes per frame, on the number of frames per second (the frame duration) and the length of the interval.
 *
 * Returns an array of time values in seconds, each double corresponds to the start time of a frame in the buffer.
 *
 * Class:     nl_mpi_avf_player_JAVFPlayer
 * Method:    getVideoFrameSequenceSeconds
 * Signature: (JDDLjava/nio/ByteBuffer;)[D
 */
JNIEXPORT jdoubleArray JNICALL Java_nl_mpi_avf_player_JAVFPlayer_getVideoFrameSequenceSeconds
(JNIEnv *env, jobject callerObject, jlong playerId, jdouble beginTime, jdouble endTime, jobject byteBuffer) {
    AVFCustomPlayer *avfPlayer = [AVFCustomPlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL && [avfPlayer hasVideo]) {
        int numFrames = ((endTime - beginTime) / [avfPlayer frameDurationSeconds]) + 2;// add some extra
        double outIndexArray [numFrames];
        // initialize with -1?
        for (int i = 0; i < numFrames; i++) {
            outIndexArray[i] = -1;
        }
        
        char * bufAddress = (char *) env->GetDirectBufferAddress(byteBuffer);
        jlong bufferLength = env->GetDirectBufferCapacity(byteBuffer);
        
        long bytesRead = [avfPlayer getFrameSequenceFromTimeSeconds:beginTime toTime:endTime outputBuffer:bufAddress bufferSize:bufferLength outputIndexArray:outIndexArray outputArraySize:numFrames];
        
        if ([AVFLog isLoggable:AVFLogFine]) {
            NSLog(@"JAVFPlayer_getVideoFrameSequenceSeconds: Number of bytes read for sequence (%f - %f): %ld", beginTime, endTime, bytesRead);
        }
        
        if (bytesRead < [avfPlayer bytesPerFrame]) {
            return NULL;
        }
        
        jdoubleArray outjdArray = env->NewDoubleArray(numFrames);
        env->SetDoubleArrayRegion(outjdArray, 0, numFrames, outIndexArray);
        
        return outjdArray;
    }
    
    return NULL;
}
