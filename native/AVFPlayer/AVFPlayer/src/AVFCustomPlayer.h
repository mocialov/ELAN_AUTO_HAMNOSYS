//
//  AVFBasePlayer.h
//  AVFPlayer
//
//  Created by Han Sloetjes on 01/02/18.
//  Copyright (c) 2018 MPI. All rights reserved.
//
#ifndef AVFPlayer_AVFCustomPlayer_h
#define AVFPlayer_AVFCustomPlayer_h

#import "AVFBasePlayer.h"
#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>

/*
 * A class that encapsulates an AVPlayer and an AVURLAsset from which the first video track
 * is used to load decoded video frames from by means of a AVAssetReader.
 * The clock and the audio of the AVPlayer are used for basic media player behavior.
 */
@class AVPlayer;
@class AVURLAsset;
//@class AVPlayerLayer;
// Could consider not to use @property and @synthesize but instead declare members
// directly in the interface (public, protected etc) and create getters (and setters?)
// "manually"

/*
 * Specialization for a player from which arrays of decoded video images are extracted sequentially
 * for rendering externally.
 * The name "AVFCustomPlayer" stems from a time before there were two variants of this
 * native player (one that delivers decoded images for rendering elsewhere, one that creates a
 * PlayerLayer to be added to a host layer).
 */
@interface AVFCustomPlayer : AVFBasePlayer {
    
    @private
        AVAssetTrack *videoTrack;
        NSMutableDictionary *outputSettings;
}

@property (nonatomic, readonly) size_t  videoImageWidth;
@property (nonatomic, readonly) size_t  videoImageHeight;

@property (nonatomic, readonly) UInt32 bytesPerPixel;
@property (nonatomic, readonly) size_t bytesPerRow;
@property (nonatomic, readonly) size_t bytesPerFrame;

/*
 * Sets the pixel format to be used by the Asset Reader
 */
- (BOOL) setPixelFormat: (NSString *) formatString;

/*
 * Returns the current pixel format as a short string (24RGB, 32ARGB etc)
 */
- (NSString *) getPixelFormat;

/*
 * A function to load a sequence of frame image based on the specified start and end time in milliseconds
 * and to store the loaded bytes in the specified output buffer and the corresponding frame start times in
 * the array of indices.
 */
- (long) getFrameSequenceFromTime: (long)time1 toTime:(long)time2 outputBuffer:(char *)bufferAddress bufferSize:(int)bufferSize outputIndexArray:(double [])outIndices outputArraySize:(int)arraySize;

/*
 * A function to load a sequence of frame image based on the specified start and end time in seconds
 * and to store the loaded bytes in the specified output buffer and the corresponding frame start times in
 * the array of indices.
 */
- (long) getFrameSequenceFromTimeSeconds: (double)time1 toTime:(double)time2 outputBuffer:(char *)bufferAddress bufferSize:(int)bufferSize outputIndexArray:(double [])outIndices outputArraySize:(int)arraySize;

@end

#endif
