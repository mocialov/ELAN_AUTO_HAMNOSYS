//
//  AVFBasePlayer.mm
//  AVFPlayer
//
//  Created by Han Sloetjes.
//  Copyright (c) 2019 MPI. All rights reserved.
//  The base implementation of a AVFoundation based player, contains
//  creation of the player, storing in the map and deletion of the player
//
#import "AVFBasePlayer.h"
#import "AVFLog.h"
#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>
#import <map>

// the scope of these fields is this class / this file
static long customPlayerId = 10000;
static std::map<long, id> customPlayerMap;

@implementation AVFBasePlayer

@synthesize player;
@synthesize mediaAsset;
@synthesize hasAudio;
@synthesize hasVideo;
@synthesize videoWidth;
@synthesize videoHeight;
@synthesize nominalFrameRate;
@synthesize frameDurationMs;
@synthesize frameDurationSeconds;
@synthesize lastError;

/*
 * Creates a AVURLAsset, AVPlayerItem and AVPlayer
 */
- (id) initWithURL: (NSURL *) url {
    self = [super init];
    // initialize some fields
    videoWidth = 0;
    videoHeight = 0;
    
    if ([AVFLog isLoggable:AVFLogInfo]) {
        NSLog(@"AVFBasePlayer_initWithURL: creating player for URL: %@", [url absoluteString]);
    }
    NSDictionary *initOptions = @{ AVURLAssetPreferPreciseDurationAndTimingKey : @YES };
    mediaAsset = [AVURLAsset URLAssetWithURL:url options:initOptions];
    CMTime assDuration = [mediaAsset duration];
    if ([AVFLog isLoggable:AVFLogInfo]) {
        NSLog(@"AVFBasePlayer_initWithURL: media asset duration: %lld, %d", assDuration.value, assDuration.timescale);
    }

    AVPlayerItem *playerItem = [AVPlayerItem playerItemWithAsset:mediaAsset];
    // Create the AVPlayer
    player = [AVPlayer playerWithPlayerItem:playerItem];
    [player setActionAtItemEnd:AVPlayerActionAtItemEndPause];
    
    CMTime medDuration = [[player currentItem] duration];
    if ([AVFLog isLoggable:AVFLogInfo]) {
        NSLog(@"AVFBasePlayer_initWithURL: player duration: %lld, %d", medDuration.value, medDuration.timescale);
    }
    [self detectTracks];
    
    return self;
}

/*
 * Detects whether there is an audio track and, if so, what the frame rate is.
 * Detects whether there is a video track and, if so, stores frame rate and natural size.
 */
- (void) detectTracks {
    if (mediaAsset != nil) {
        
        // check audio tracks
        NSArray *audioTrackArray = [mediaAsset tracksWithMediaType:AVMediaTypeAudio];
        
        if ([audioTrackArray count] > 0) {
            AVAssetTrack *firstTrack = audioTrackArray[0];
            nominalFrameRate = [firstTrack nominalFrameRate];
            hasAudio = TRUE;
            
            if ([AVFLog isLoggable:AVFLogInfo]) {
                NSLog(@"AVFBasePlayer_detectTracks: Audio track frame rate: %f", nominalFrameRate);
            }
        } else {
            hasAudio = FALSE;
        }
        
        // videoTrack = [[mediaAsset tracksWithMediaType:AVMediaTypeVideo] firstObject];
        // if (videoTrack != NULL) { etc.
        NSArray *videoTrackArray = [mediaAsset tracksWithMediaType:AVMediaTypeVideo];
        if ([videoTrackArray count] > 0) {
            AVAssetTrack *videoTrack = videoTrackArray[0];
            hasVideo = TRUE;
            
            CGSize natSize = [videoTrack naturalSize];
            videoWidth = natSize.width;
            videoHeight = natSize.height;
            if ([AVFLog isLoggable:AVFLogInfo]) {
                NSLog(@"AVFBasePlayer_detectTracks: Video track 0: width: %f, height %f", videoWidth, videoHeight);
            }
            /*for (int i = 0; i < [videoTrackArray count]; i++) {
             AVAssetTrack *vidTrack = videoTrackArray[i];
             CGSize naturalSize = [vidTrack naturalSize];
             NSLog(@"Video track %d: width: %f, height %f", i, naturalSize.width, naturalSize.height);
             }*/
            // if there is video, its frame rate is used
            nominalFrameRate = [videoTrack nominalFrameRate];
            if ([AVFLog isLoggable:AVFLogInfo]) {
                NSLog(@"AVFBasePlayer_detectTracks: Video track frame rate: %f", nominalFrameRate);
            }
        } else {
            hasVideo = FALSE;
        }
        
        if (nominalFrameRate != 0) {
            frameDurationMs = 1000 / nominalFrameRate;
            frameDurationSeconds = 1 / nominalFrameRate;
            
            if ([AVFLog isLoggable:AVFLogInfo]) {
                NSLog(@"AVFBasePlayer_detectTracks: Frame duration (ms): %f, (sec): %f", frameDurationMs, frameDurationSeconds);
            }
        }
    } else {
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFBasePlayer_detectTracks: media asset is null");
        }
    }
}

/*
 * Sets some fields to NULL (no direct deallocation in ARC).
 */
- (void) releasePlayer {
    //videoTrack = NULL;
    //outputSettings = NULL;
    player = NULL;
    mediaAsset = NULL;
}

/*
 * Class methods for creating an id for a player and storing it in and
 * retrieving it from the player map.
 */
+ (long) createIdAndStorePlayer: (id) avfPlayer {
    if (avfPlayer != NULL) {
        long nextId = customPlayerId++;
        customPlayerMap[nextId] = avfPlayer;
        return nextId;
    }
    return 0;
}

/*
 * Returns the player for the specified player id.
 */
+ (id) getPlayerWithId: (long) playerId {
    return customPlayerMap[playerId];// can be null
}

/*
 * Removes the player with the specified id from the map.
 */
+ (void) removePlayerWithId: (long) playerId {
    customPlayerMap.erase(playerId);
}

@end

