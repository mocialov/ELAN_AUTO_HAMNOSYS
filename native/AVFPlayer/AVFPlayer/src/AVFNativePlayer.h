//
//  AVFNativePlayer.h
//  AVFPlayer
//
//  Created by Han Sloetjes on 02/02/19.
//  Copyright (c) 2019 MPI. All rights reserved.
//
#ifndef AVFPlayer_AVFNativePlayer_h
#define AVFPlayer_AVFNativePlayer_h

#import <AVFoundation/AVFoundation.h>
#import "AVFBasePlayer.h"

/*
 * A class that encapsulates an AVPlayer and an AVPlayerLayer.
 * The player layer can be added to a CALayer provided by a client.
 */

//@class AVPlayer;
//@class AVURLAsset;
@class AVPlayerLayer;
@class CALayer;
@protocol CAAction;

// Could consider not to use @property and @synthesize but instead declare members
// directly in the interface (public, protected etc) and create getters (and setters?)
// "manually"

/*
 * Specialization for a player with a player layer which can be added to a CALayer.
 */
@interface AVFNativePlayer : AVFBasePlayer

@property (retain, nullable, readonly) AVPlayerLayer *playerLayer;
@property (retain, nullable) CALayer *hostLayer;
@property (nonatomic) BOOL firstConnectionToCanvas;

@end

/*
 * The interface for an empty action which can used to disable (or override)
 * default animation behaviour for the player layer. The animations interfere
 * with resize and zoom and pan actions of the client.
 */
@interface NoLayerAction : NSObject <CAAction>

@end
#endif
