
#import "nl_mpi_avf_player_AVFNativePlayer.h"
#import "AVFNativePlayer.h"
#import "AVFLog.h"
#import <jawt.h>
#import <jawt_md.h>

/*
 * Creates a AVFNativePlayer for the specified media url (url as a string).
 * Returns an id which is used for store and retrieve of the player in a global
 * dictionary. If a player cannot be created 0 is returned. This player can
 * be used with a Java Component as "parent surface" to which the AVPLayerLayer
 * of this player can be added.
 * Note: in case of a local file, remove file: protocol (otherwise there is a
 * problem with white spaces in the path)
 *
 * Class:     nl_mpi_avf_player_AVFNativePlayer
 * Method:    initPlayer
 * Signature: (Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_nl_mpi_avf_player_AVFNativePlayer_initPlayer
(JNIEnv *env, jobject callerObject, jstring mediaURL) {
    jlong nextId = 0;
    jboolean isCopy;
    const char *mediaURLChars = env->GetStringUTFChars(mediaURL, &isCopy);
    // convert jstring to NSString
    NSString *urlString = [NSString stringWithUTF8String:mediaURLChars];
    if ([AVFLog isLoggable:AVFLogInfo]) {
        NSLog(@"AVFNativePlayer_initPlayer: media URL: %@", urlString);
    }
    
    NSURL *mediaNSURL = [NSURL fileURLWithPath:urlString isDirectory:NO];
    //NSURL *mediaNSURL = [NSURL URLWithString:urlString]; // works with http:// url's as opposed to the above
    //NSURL *mediaNSURL = [NSURL fileURLWithFileSystemRepresentation:mediaURLChars isDirectory:NO relativeToURL:NULL];
    //NSURL *mediaFileURL = [mediaNSURL filePathURL];
    
    
    id avfPlayer = [[AVFNativePlayer alloc] initWithURL:mediaNSURL];
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
                NSLog(@"AVFNativePlayer_initPlayer: error while creating player: %@", [avfPlayer lastError]);
            }
            nextId = 0;
            [avfPlayer releasePlayer];// clean up?
        } else {
            nextId = [AVFNativePlayer createIdAndStorePlayer:avfPlayer];
        }
    }
    
    env->ReleaseStringUTFChars(mediaURL, mediaURLChars);
    if ([AVFLog isLoggable:AVFLogInfo]) {
        NSLog(@"AVFNativePlayer_initPlayer: id of new player: %ld, has audio: %@, has video: %@", nextId, ([avfPlayer hasAudio] ? @"Y" : @"N"), ([avfPlayer hasVideo] ? @"Y" : @"N"));
    }
    return nextId;
}

/*
 * Remove player and free resources.
 * Class:     nl_mpi_avf_player_AVFNativePlayer
 * Method:    deletePlayer
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFNativePlayer_deletePlayer
(JNIEnv *env, jobject callerObject, jlong playerId) {
    AVFBasePlayer *avfPlayer = [AVFBasePlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL) {
        // remove from global map
        [AVFBasePlayer removePlayerWithId:playerId];
        // clean up
        [avfPlayer releasePlayer];
    }
}

/*
 * Provides the visual component to which to add the player layer.
 * The component should conform to the JAWT_DrawingSurface protocol.
 * In order to enable video zoom and pan, an additional "host" layer is added
 * to which the player layer is added as a sublayer. Without this in between
 * layer it is not possible (so it seems, at least much harder) to implement
 * custom scaling and repositioning of the video image, relative to the canvas.
 *
 * Class:     nl_mpi_avf_player_AVFNativePlayer
 * Method:    setVisualComponent
 * Signature: (JLjava/awt/Component;)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFNativePlayer_setVisualComponent
(JNIEnv *env, jobject callerObject, jlong playerId, jobject visualComponent) {
    AVFNativePlayer *avfPlayer = [AVFNativePlayer getPlayerWithId:playerId];
 
    if (avfPlayer == NULL) {
        // log and return
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFNativePlayer_setVisualComponent: no player with id %ld in the map.", playerId);
        }
        return;
    }
    
    // get the AWT
    JAWT awt;
    awt.version = JAWT_VERSION_9; // the latest version
    
    jboolean result = JAWT_GetAWT(env, &awt);
    if (result == JNI_FALSE) {
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFNativePlayer_setVisualComponent: unable to get JAWT.");
        }
        return; // CALayer support unavailable
    }
    
    // get the drawing surface
    JAWT_DrawingSurface *ds = awt.GetDrawingSurface(env, visualComponent);
    if (ds == NULL) {
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFNativePlayer_setVisualComponent: the JAWT_DrawingSurface for the component is null.");
        }
        return;
    }
    
    // lock the drawing surface
    jint lock = ds->Lock(ds);
    
    if ((lock & JAWT_LOCK_ERROR) != 0) {
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFNativePlayer_setVisualComponent: unable to lock the JAWT Drawing Surface.");
        }
        return;
    }
    
    // get the drawing surface info
    JAWT_DrawingSurfaceInfo *dsi = ds->GetDrawingSurfaceInfo(ds);
    
    // Check DrawingSurfaceInfo. This can be NULL on Mac OS X if the native
    // component hierachy has not been made visible yet on the AppKit thread.
    
    if (dsi != NULL) {
        if ([AVFLog isLoggable:AVFLogFine]) {
            NSLog(@"AVFNativePlayer_setVisualComponent: got the JAWT Drawing Surface Info.");
        }
        // attach the "root layer" to the AWT Canvas surface layers
        // might need to use CFBridgingRelease ?
        id <JAWT_SurfaceLayers> surfaceLayers = (__bridge id <JAWT_SurfaceLayers>)dsi->platformInfo;
        if ([AVFLog isLoggable:AVFLogFine]) {
            NSLog(@"AVFNativePlayer_setVisualComponent: got the JAWT_SurfaceLayers.");
        }
        // setLayer now works
        //[surfaceLayers setLayer:(CALayer *)[avfPlayer playerLayer]];
        // the extra layer leads to strange behavior when detaching/attaching
        if ([avfPlayer firstConnectionToCanvas] == FALSE) {
            CALayer *hostLayer = [CALayer layer];
            [hostLayer setBackgroundColor:CGColorCreateGenericRGB(0.0, 0.4, 0.8, 1.0)];
            [hostLayer setAutoresizingMask:kCALayerWidthSizable | kCALayerHeightSizable | kCALayerMinXMargin | kCALayerMaxXMargin | kCALayerMinYMargin | kCALayerMaxYMargin];
            [surfaceLayers setLayer: hostLayer];
            [hostLayer setMasksToBounds:YES];
            [hostLayer addSublayer:[avfPlayer playerLayer]];
            [avfPlayer setHostLayer:hostLayer];

            [[avfPlayer playerLayer] setAutoresizingMask:kCALayerWidthSizable | kCALayerHeightSizable];
            // the origin (0, 0) of the layer's coordinate system is left-bottom. (1, 1) is right-top!
            // flip geometry and reset the anchor point so that y coordinates don't need to be
            // recalculated between Java and CALayer
            [[avfPlayer playerLayer] setVideoGravity: AVLayerVideoGravityResize];
            //[[avfPlayer playerLayer] setValue:[NSNumber numberWithFloat:0.0] forKeyPath:@"anchorPoint.x"];//??
            //[[avfPlayer playerLayer] setValue:[NSNumber numberWithFloat:1.0] forKeyPath:@"anchorPoint.y"];
            //[[avfPlayer playerLayer] setGeometryFlipped:YES];// should not be used on macOS 10.8 >?
            [avfPlayer setFirstConnectionToCanvas:TRUE];
            if ([AVFLog isLoggable:AVFLogFine]) {
                NSLog(@"AVFNativePlayer_setVisualComponent: created host layer, first time connection.");
            }
        } else {
            // reconnect the host layer to the surface layer
            [surfaceLayers setLayer: [avfPlayer hostLayer]];
            if ([AVFLog isLoggable:AVFLogFine]) {
                NSLog(@"AVFNativePlayer_setVisualComponent: reconnected the host layer.");
            }
        }

        // free the DrawingSurfaceInfo
        ds->FreeDrawingSurfaceInfo(dsi);
    } else {
        // report or log that the Drawing Surface Info is null
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFNativePlayer_setVisualComponent: unable to get the JAWT Drawing Surface Info.");
        }
    }
    
    ds->Unlock(ds);
    
    awt.FreeDrawingSurface(ds);
    if ([AVFLog isLoggable:AVFLogInfo]) {
        NSLog(@"AVFNativePlayer_setVisualComponent: succesfully added the player layer to the Java component's surface layer.");
    }
}

/*
 * Disconnects the player layer from the surface layer. Only necessary because of the
 * additional layer that is added to support video scaling. This way an AWT Canvas can be detached
 * from its initial component hierarchy and added again to a new (detached) window, without the
 * video layer being lost somewhere in the parent AWT component.
 *
 * Class:     nl_mpi_avf_player_AVFNativePlayer
 * Method:    disconnectVisualComponent
 * Signature: (JLjava/awt/Component;)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFNativePlayer_disconnectVisualComponent
(JNIEnv *env, jobject callerObject, jlong playerId, jobject visualComponent) {
    AVFNativePlayer *avfPlayer = [AVFNativePlayer getPlayerWithId:playerId];
    
    if (avfPlayer == NULL) {
        // log and return
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFNativePlayer_disconnectVisualComponent: no player with id %ld in the map.", playerId);
        }
        return;
    }
    
    // get the AWT
    JAWT awt;
    awt.version = JAWT_VERSION_9; // the latest version
    
    jboolean result = JAWT_GetAWT(env, &awt);
    if (result == JNI_FALSE) {
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFNativePlayer_disconnectVisualComponent: unable to get JAWT.");
        }
        
        return; // CALayer support unavailable, unlikely at this point
    }
    
    // get the drawing surface
    JAWT_DrawingSurface *ds = awt.GetDrawingSurface(env, visualComponent);
    if (ds == NULL) {
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFNativePlayer_disconnectVisualComponent: the JAWT_DrawingSurface for the component is null.");
        }
        return;
    }
    
    // lock the drawing surface
    jint lock = ds->Lock(ds);
    
    if ((lock & JAWT_LOCK_ERROR) != 0) {
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFNativePlayer_disconnectVisualComponent: unable to lock the JAWT Drawing Surface.");
        }
        return;
    }
    
    // get the drawing surface info
    JAWT_DrawingSurfaceInfo *dsi = ds->GetDrawingSurfaceInfo(ds);
    // Check DrawingSurfaceInfo. This can be NULL on Mac OS X if the native
    // component hierachy has not been made visible yet on the AppKit thread.
    
    if (dsi != NULL) {
        if ([AVFLog isLoggable:AVFLogFine]) {
            NSLog(@"AVFNativePlayer_disconnectVisualComponent: got the JAWT Drawing Surface Info.");
        }
        // detach the "root layer" from the AWT Canvas surface layers
        //id <JAWT_SurfaceLayers> surfaceLayers = (__bridge id <JAWT_SurfaceLayers>)dsi->platformInfo;
        //NSLog(@"AVFNativePlayer_disconnectVisualComponent: got the JAWT_SurfaceLayers.");
        //[surfaceLayers setLayer:NULL];
        // only disconnect the host layer from the surface layer, seems to suffice
        [[avfPlayer hostLayer] removeFromSuperlayer];
        if ([AVFLog isLoggable:AVFLogFine]) {
            NSLog(@"AVFNativePlayer_disconnectVisualComponent: succesfully removed the player layer from the surface layer.");
        }
        
        ds->FreeDrawingSurfaceInfo(dsi);
    } else {
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFNativePlayer_disconnectVisualComponent: unable to get the JAWT Drawing Surface Info.");
        }
    }
    
    ds->Unlock(ds);
    awt.FreeDrawingSurface(ds);
}


/*
 * This method doesn't seem to be necessary. Setting the frame bounds seems sufficient.
 * Class:     nl_mpi_avf_player_AVFNativePlayer
 * Method:    setVideoScaleFactor
 * Signature: (JF)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFNativePlayer_setVideoScaleFactor
(JNIEnv *env, jobject callerObject, jlong playerId, jfloat scaleFactor) {
    /* not needed anymore?
    AVFNativePlayer *avfPlayer = [AVFNativePlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL) {
        if ([avfPlayer playerLayer] != NULL) {
            if (scaleFactor == 1.0f) {
                //?? more probably needs to be set/changed
                [[avfPlayer playerLayer] setVideoGravity: AVLayerVideoGravityResize];
            } else {
                //?? more probably needs to be set/changed
                [[avfPlayer playerLayer] setVideoGravity: AVLayerVideoGravityResize];
            }
            [[avfPlayer playerLayer] display];
        }
    }
     */
}

/*
 Situation in case of scaled video (zoom > 100%):
 
 video top left corner, vx,vy relative to x,y
 +------------------------------------------------+
 |                                                |
 |      x,y canvas top left corner (is 0,0)       |
 |      +-----------------------+                 |
 |      |                       |                 |
 |      |                       | h               |
 |      |                       |                 | vh
 |      |                       |                 |
 |      +-----------------------+                 |
 |                  w                             |
 |in CALayer y = 0 at the bottom                  |
 +------------------------------------------------+
                        vw
 
 Attempts are made to always cover the entire area of the canvas with a part of the
 video, so vx <= 0 && vy <= 0 and vx >= w - vw && vy >= h - vh
 */

/*
 * The passed (x,y) are the coordinates of the left top corner. The origin property of
 * a CALayer corresponds to the left bottom corner, so the y value needs to be recalculated,
 * unless the "geometry flipped" property of the player layer has been set to YES.
 * Setting this property is discouraged on macOS 10.8 or later in the
 * "Core Animation Programming Guide"
 *
 * Class:     nl_mpi_avf_player_AVFNativePlayer
 * Method:    setVideoBounds
 * Signature: (JIIII)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFNativePlayer_setVideoBounds
(JNIEnv *env, jobject callerObject, jlong playerId, jint vx, jint vy, jint vw, jint vh) {
    //  if scale factor == 1, return
    AVFNativePlayer *avfPlayer = [AVFNativePlayer getPlayerWithId:playerId];
   
    if (avfPlayer != NULL) {
        if ([avfPlayer playerLayer] != NULL) {
            CGRect hRect = [[[avfPlayer playerLayer] superlayer] bounds];
            // correct x and y coordinates here instead of changing the anchor point of any layer and/or setting the Geometry Flipped flag
            CGFloat cy = vy;
            if (hRect.size.height > 0) {
                cy = hRect.size.height - vh - vy;// should be <= 0
                //cy = cy + hRect.size.height / 2;
            }
            if ([AVFLog isLoggable:AVFLogFine]) {
                NSLog(@"AVFNativePlayer_setVideoBounds: setting video bounds x:%d, y:%f, w:%d, h:%d", vx, cy, vw, vh);
            }
            [[avfPlayer playerLayer] setFrame:CGRectMake(vx, cy, vw, vh)];
        }
    }
}

/*
 * Returns the current size and location of the bounds of the enlarged video.
 * The location is relative to the (smaller) host panel, the y-coordinate is recalculated
 * from the left bottom corner to the left top corner.
 *
 * Class:     nl_mpi_avf_player_AVFNativePlayer
 * Method:    getVideoBounds
 * Signature: (J)[I
 */
JNIEXPORT jintArray JNICALL Java_nl_mpi_avf_player_AVFNativePlayer_getVideoBounds
(JNIEnv *env, jobject callerObject, jlong playerId) {
    AVFNativePlayer *avfPlayer = [AVFNativePlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL) {
        if ([avfPlayer playerLayer] != NULL) {
            CGRect plRect = [[avfPlayer playerLayer] frame];
            CGRect hostRect = [[[avfPlayer playerLayer] superlayer] bounds];
            CGFloat cy = hostRect.size.height - plRect.size.height - plRect.origin.y;
            
            jintArray boundArray = env->NewIntArray(4);
            jboolean isCopy;
            jint* pIntArray;
            pIntArray = env->GetIntArrayElements(boundArray, &isCopy);
            pIntArray[0] = (jint) plRect.origin.x;
            //pIntArray[1] = (jint) plRect.origin.y;
            pIntArray[1] = (jint) cy;
            pIntArray[2] = (jint) plRect.size.width;
            pIntArray[3] = (jint) plRect.size.height;
            
            env->ReleaseIntArrayElements(boundArray, pIntArray, 0);
            
            return boundArray;
        }
    }
    
    return NULL;
}

/*
 * Setting the "NeedsDisplay" flag and triggering a "displayIfNeeded" doesn't
 * seem to work in the JNI situation. A resize is necessary on the NSView level
 * to trigger a display or redraw action (the default behaviour of NSView).
 *
 * Class:     nl_mpi_avf_player_AVFNativePlayer
 * Method:    repaintVideo
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_nl_mpi_avf_player_AVFNativePlayer_repaintVideo
(JNIEnv *env, jobject callerObject, jlong playerId) {
    AVFNativePlayer *avfPlayer = [AVFNativePlayer getPlayerWithId:playerId];
    
    if (avfPlayer != NULL) {
        if ([avfPlayer playerLayer] != NULL) {
            [[avfPlayer playerLayer] setNeedsDisplay];
            [[avfPlayer playerLayer] displayIfNeeded];
        }
    }
}

/*
 Scrap code from setVisualComponent:
 
 //[[avfPlayer playerLayer] setVideoGravity: AVLayerVideoGravityResize];// initial setting without scaling
 
 //[hostLayer removeAllAnimations];
 //[hostLayer removeAnimationForKey:@"bounds"];
 //[hostLayer removeAnimationForKey:@"contents"];
 //[hostLayer removeAnimationForKey:kCATransition];
 //[[hostLayer presentationLayer] removeAllAnimations];
 //[[hostLayer presentationLayer] removeAnimationForKey:kCATransition];
 //[[avfPlayer playerLayer] removeAllAnimations];
 //[[[avfPlayer playerLayer] presentationLayer] removeAllAnimations];
 //[[[avfPlayer playerLayer] presentationLayer] removeAnimationForKey:kCATransition];
 //[[avfPlayer playerLayer] removeAnimationForKey:@"bounds"];
 //[[avfPlayer playerLayer] removeAnimationForKey:@"contents"];
 //[[avfPlayer playerLayer] removeAnimationForKey:kCATransition];
 //[[surfaceLayers windowLayer] removeAllAnimations];
 //[[surfaceLayers windowLayer] removeAnimationForKey:kCATransition];
 
 // addSublayer does not seem to work
 //[[surfaceLayers layer] addSublayer:(CALayer *)[avfPlayer playerLayer]];
 // not necessary when automatic scaling is enabled
 //[(CALayer *)[avfPlayer playerLayer] setBounds: CGRectMake(0, 0, 200, 200)];
 //[[avfPlayer playerLayer] setContentsRect: CGRectMake(0.0, 0.0, 2.0, 2.0)];// no effect
 //[[avfPlayer playerLayer] setContentsCenter: CGRectMake(0.2, 0.2, 0.7, 0.7)];// no effect
 //[[avfPlayer playerLayer] setContentsScale: 2.0];// no effect
 // this setting has some effect?, the default seems right
 //[[avfPlayer playerLayer] setVideoGravity: AVLayerVideoGravityResize];
 //[[avfPlayer playerLayer] setFrame:CGRectMake(-0.2, -0.2, 2.0, 2.0)];// no effect
 //[[avfPlayer playerLayer] setBounds: CGRectMake(-0.2, -0.2, 2.0, 2.0)];
 //[[avfPlayer playerLayer] setOpacity:0.5];// this has an effect!
 //avfPlayer.playerLayer.superlayer.anchorPoint = CGPointMake(0.3, 0.3);
 //[[avfPlayer playerLayer] setHidden:NO];// not necessary
 
 Scrap code from Java_nl_mpi_avf_player_AVFNativePlayer_setVideoBounds:
 
 CGRect hRect = [[[avfPlayer playerLayer] superlayer] bounds];
 CGFloat cy = vy;
 if (hRect.size.height > 0) {
 cy = hRect.size.height - vh - vy;// should be <= 0
 }
 
//[[avfPlayer playerLayer] setValue:[NSValue valueWithRect:CGRectMake(vx, vy, vw, vh)] forKey:@"bounds"];
// after every call the removed animation with key "bounds" is there again

 [[avfPlayer playerLayer] removeAllAnimations];
 [[avfPlayer hostLayer] removeAllAnimations];
 [[avfPlayer hostLayer] removeAnimationForKey: kCATransition];
 [[avfPlayer playerLayer] removeAnimationForKey: kCATransition];
 
 NSLog(@"Number of player animations: %lu", [[[avfPlayer playerLayer] animationKeys] count]);
 NSLog(@"Number of host animations: %lu", [[[avfPlayer hostLayer] animationKeys] count]);
 NSLog(@"Number of player presentation animations: %lu", [[[[avfPlayer playerLayer] presentationLayer] animationKeys] count]);
 NSLog(@"Number of host presentation animations: %lu", [[[[avfPlayer hostLayer] presentationLayer] animationKeys] count]);
 
//            NSArray * animKeys = [[avfPlayer playerLayer] animationKeys];
//            for (int i = 0; i < [animKeys count]; i++) {
//                NSLog(@"K: %d V: %@", i, animKeys[i]);
//            }

 // try action for key
 //NSLog(@"Action for key '%@': %p", @"bounds", [[avfPlayer playerLayer] actionForKey:@"bounds"]);// this action is there by default
 //            NSLog(@"Default action for key '%@': %p", @"bounds", [AVPlayerLayer defaultActionForKey:@"bounds"]);// the class level action, null, 0x0
 //NSLog(@"Action for key %@: %p", kCATransition, [[avfPlayer playerLayer] actionForKey:kCATransition]);// not there
 //            NSLog(@"Number of actions %lu", [[[avfPlayer playerLayer] actions] count]);// 0
 //NSLog(@"Has delegate %p", [[avfPlayer playerLayer] delegate]);//no delegate
 
 // this all doesn't force a repaint, it seems like the trigger has to come from the Java side, e.g. by a resize of the Java component (Canvas)?
 //[[avfPlayer playerLayer] setNeedsDisplay];// all no effect
 //[[avfPlayer playerLayer] displayIfNeeded];
 //            [[[avfPlayer playerLayer] superlayer] setNeedsDisplay];
 //            [[[avfPlayer playerLayer] superlayer] displayIfNeeded];
 // the following on the main thread has no effect either
 //[[[[avfPlayer playerLayer] superlayer] superlayer] performSelectorOnMainThread:@selector(setNeedsDisplay) withObject:NULL waitUntilDone:YES];
 //[[[[avfPlayer playerLayer] superlayer] superlayer] performSelectorOnMainThread:@selector(displayIfNeeded) withObject:NULL waitUntilDone:YES];
 //            [[avfPlayer windowLayer] performSelectorOnMainThread:@selector(setNeedsDisplay) withObject:NULL waitUntilDone:YES];// no effect
 //            [[avfPlayer windowLayer] performSelectorOnMainThread:@selector(displayIfNeeded) withObject:NULL waitUntilDone:YES];
 //            [[avfPlayer hostLayer] performSelectorOnMainThread:@selector(setNeedsDisplay) withObject:NULL waitUntilDone:YES];
 //            [[avfPlayer hostLayer] performSelectorOnMainThread:@selector(displayIfNeeded) withObject:NULL waitUntilDone:YES];
 
 //[[avfPlayer hostLayer] layoutSublayers];//no effect
 //[[avfPlayer windowLayer] layoutSublayers];
 //            [[avfPlayer player] didChangeValueForKey:@"bounds"];// no effect
 //            [[avfPlayer playerLayer] didChangeValueForKey:@"bounds"];
 
 */
