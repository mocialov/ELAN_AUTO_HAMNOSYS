/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class nl_mpi_avf_player_AVFBasePlayer */

#ifndef _Included_nl_mpi_avf_player_AVFBasePlayer
#define _Included_nl_mpi_avf_player_AVFBasePlayer
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    setJAVFLogLevel
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_setJAVFLogLevel
  (JNIEnv *, jclass, jint);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getJAVFLogLevel
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getJAVFLogLevel
  (JNIEnv *, jclass);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    initPlayer
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_initPlayer
  (JNIEnv *, jobject, jstring);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getPlayerLoadStatus
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getPlayerLoadStatus
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getPlayerError
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getPlayerError
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    deletePlayer
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_deletePlayer
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    start
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_start
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    stop
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_stop
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    pause
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_pause
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    isPlaying
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_isPlaying
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getState
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getState
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    setRate
 * Signature: (JF)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_setRate
  (JNIEnv *, jobject, jlong, jfloat);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getRate
 * Signature: (J)F
 */
JNIEXPORT jfloat JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getRate
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    setVolume
 * Signature: (JF)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_setVolume
  (JNIEnv *, jobject, jlong, jfloat);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getVolume
 * Signature: (J)F
 */
JNIEXPORT jfloat JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getVolume
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getMediaTime
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getMediaTime
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getMediaTimeSeconds
 * Signature: (J)D
 */
JNIEXPORT jdouble JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getMediaTimeSeconds
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    setMediaTime
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_setMediaTime
  (JNIEnv *, jobject, jlong, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    setMediaTimeSeconds
 * Signature: (JD)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_setMediaTimeSeconds
  (JNIEnv *, jobject, jlong, jdouble);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    hasVideo
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_hasVideo
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    hasAudio
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_hasAudio
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getDuration
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getDuration
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getDurationSeconds
 * Signature: (J)D
 */
JNIEXPORT jdouble JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getDurationSeconds
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getFrameRate
 * Signature: (J)D
 */
JNIEXPORT jdouble JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getFrameRate
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getTimePerFrame
 * Signature: (J)D
 */
JNIEXPORT jdouble JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getTimePerFrame
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getAspectRatio
 * Signature: (J)F
 */
JNIEXPORT jfloat JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getAspectRatio
  (JNIEnv *, jobject, jlong);

/*
 * Class:     nl_mpi_avf_player_AVFBasePlayer
 * Method:    getOriginalSize
 * Signature: (J)Ljava/awt/Dimension;
 */
JNIEXPORT jobject JNICALL Java_nl_mpi_avf_player_AVFBasePlayer_getOriginalSize
  (JNIEnv *, jobject, jlong);

#ifdef __cplusplus
}
#endif
#endif
