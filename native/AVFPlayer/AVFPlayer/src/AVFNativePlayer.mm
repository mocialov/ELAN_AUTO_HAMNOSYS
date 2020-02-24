//
//  AVFNativePlayer.mm
//  AVFPlayer
//
//  Created by Han Sloetjes on 02/02/19.
//  Copyright (c) 2019 MPI. All rights reserved.
//
#import "AVFNativePlayer.h"
#import "AVFLog.h"
#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>

@implementation AVFNativePlayer

@synthesize playerLayer;
@synthesize hostLayer;
@synthesize firstConnectionToCanvas;

/*
 * Creates a player layer for the player created by the super class
 * and sets some properties and actions for the layer.
 */
- (id) initWithURL: (NSURL *) url {
    self = [super initWithURL:url];
    firstConnectionToCanvas = FALSE;
    // create layer but only if there is video
    if ([super hasVideo]) {
        playerLayer = [AVPlayerLayer playerLayerWithPlayer:[super player]];
        
        if (playerLayer != NULL) {
            if ([AVFLog isLoggable:AVFLogInfo]) {
                NSLog(@"AVFNativePlayer_initWithURL: successfully created the PlayerLayer.");
            }
            // leave the values for setAutoresizingMask and setVideoGravity under control
            // of the calling JNI Java_nl_mpi_avf_player_AVFNativePlayer class, so that
            // scaling and panning of the video image can be completely handled there
//            [playerLayer setAutoresizingMask:kCALayerWidthSizable | kCALayerHeightSizable | kCALayerMinXMargin | kCALayerMaxXMargin | kCALayerMinYMargin | kCALayerMaxYMargin];
            [playerLayer setNeedsDisplayOnBoundsChange:YES];// no effect?
            // add a stub action to remove/disable the default animation when the bounds
            // of the layer change
            NoLayerAction *noAction = [NoLayerAction alloc];
            NSDictionary *actionDictionary = @{@"bounds" : noAction, @"position" : noAction, kCATransition : noAction};
            [playerLayer setActions:actionDictionary];
        } else {
            if ([AVFLog isLoggable:AVFLogInfo]) {
                NSLog(@"AVFNativePlayer_initWithURL: could not create the PlayerLayer.");
            }
        }
    }
    
    return self;
}

/*
 * Clean up of resources (as much as possible and/or necessary).
 */
- (void) releasePlayer {
    [super releasePlayer];

    playerLayer = NULL;
    hostLayer = NULL;
}

@end

@implementation NoLayerAction
/*
 * Empty, stub implementation of a layer action to make sure no animations are run
 * (e.g. when the bounds or position property changes).
 */
- (void)runActionForKey:(nonnull NSString *)event object:(nonnull id)anObject arguments:(nullable NSDictionary *)dict {
    //NSLog(@"No action for this key");
}

@end
