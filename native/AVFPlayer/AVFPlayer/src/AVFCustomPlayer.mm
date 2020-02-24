//
//  AVFCustomPlayer.m
//  AVFPlayer
//
//  Created by Han Sloetjes on 02/02/18.
//  Copyright (c) 2018 MPI. All rights reserved.
//
#import "AVFCustomPlayer.h"
#import "AVFLog.h"
#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>

// private method for AVFCustomPlayer
@interface AVFCustomPlayer ()
- (void) detectFrameSizeInBytes;
@end

/*
 * Implementation of the custom player that produces arrays of decoded images
 * for rendering somewhere else.
 */
@implementation AVFCustomPlayer

@synthesize videoImageWidth;
@synthesize videoImageHeight;
@synthesize bytesPerPixel;
@synthesize bytesPerRow;
@synthesize bytesPerFrame;

/*
 NSArray *mimeTypes = [AVURLAsset audiovisualMIMETypes];
 for (int i = 0; i < [mimeTypes count]; i++) {
 NSLog(@"Mime Type %d : %@", i, mimeTypes[i]);
 }
 
 NSArray *avTypes = [AVURLAsset audiovisualTypes];
 for (int i = 0; i < [avTypes count]; i++) {
 NSLog(@"UTI %d : %@", i, avTypes[i]);
 }
 */

/*
 * Could maybe get the UTI or MimeType of the file and 
 * check it against the list of available UTI's or MimeTypes (see above).
 */
- (id) initWithURL: (NSURL *) url {
    self = [super initWithURL:url];
    // initialize some fields
    videoImageWidth = 0;
    videoImageHeight = 0;
    bytesPerPixel = 0;
    bytesPerRow = 0;
    bytesPerFrame = 0;
    
    // action at end: if AVPlayerActionAtItemEndNone: the clock continues beyond end of media, isPlaying returns true
    // if AVPlayerActionAtItemEndPause: the clock stop at end of media, isPlaying sometimes returns false, sometimes
    // true?. Anyway, difficult to discern from a pause by the user.
    //[player setActionAtItemEnd:AVPlayerActionAtItemEndPause];
    //AVPlayerActionAtItemEndNone
    //AVPlayerActionAtItemEndPause
    
    // create output settings once, on macOS when using RGB kCVPixelFormatType_32ARGB is recommended
    outputSettings = [NSMutableDictionary dictionaryWithCapacity:2];
    [outputSettings setObject:@(kCVPixelFormatType_24RGB) forKey:(id)kCVPixelBufferPixelFormatTypeKey];
    //outputSettings = @{
                        //AVVideoCodecKey: kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange
                        //(id)kCVPixelBufferPixelFormatTypeKey: @(kCVPixelFormatType_32BGRA)
                        //(id)kCVPixelBufferPixelFormatTypeKey: @(kCVPixelFormatType_32ARGB)
                        //(id)kCVPixelBufferPixelFormatTypeKey: @(kCVPixelFormatType_24RGB) // requires smaller size for byte buffer
                        // kCVPixelFormatType_8IndexedGray_WhiteIsZero// should be inverted
                        // kCVPixelFormatType_422YpCbCr8FullRange
                        //(id)kCVPixelBufferPixelFormatTypeKey: @(kCVPixelFormatType_24RGB)
                        //};
    
    // create an AssetReader everytime it is needed (no re-use)
    
    if ([super hasVideo]) {
        videoTrack = [[[super mediaAsset] tracksWithMediaType:AVMediaTypeVideo] firstObject];
        // load the first frame(s) so that relevant fields get initialized
        [self detectFrameSizeInBytes];
    }

    return self;
}

/*
 * Sets the pixel format to be used by the Asset Reader.
 * Five format strings are supported at this time:
 * 24RGB, 32ARGB, 32BGRA, Gray, 422YpCbCr8
 * Other strings will be ignored
 */
- (BOOL) setPixelFormat: (NSString *) formatString {
    if (outputSettings != NULL) {
        if ([formatString containsString:@"24RGB"]) {
            [outputSettings setObject:@(kCVPixelFormatType_24RGB) forKey:(id)kCVPixelBufferPixelFormatTypeKey];
            if ([AVFLog isLoggable:AVFLogInfo]) {
                NSLog(@"AVFCustomPlayer_setPixelFormat: set pixel format to 24RGB");
            }
            return TRUE;
        }
        if ([formatString containsString:@"32ARGB"]) {
            [outputSettings setObject:@(kCVPixelFormatType_32ARGB) forKey:(id)kCVPixelBufferPixelFormatTypeKey];
            if ([AVFLog isLoggable:AVFLogInfo]) {
                NSLog(@"AVFCustomPlayer_setPixelFormat: set pixel format to 32ARGB");
            }
            return TRUE;
        }
        if ([formatString containsString:@"32BGRA"]) {
            [outputSettings setObject:@(kCVPixelFormatType_32BGRA) forKey:(id)kCVPixelBufferPixelFormatTypeKey];
            if ([AVFLog isLoggable:AVFLogInfo]) {
                NSLog(@"AVFCustomPlayer_setPixelFormat: set pixel format to 32BGRA");
            }
            return TRUE;
        }
        if ([formatString containsString:@"Gray"]) {
            [outputSettings setObject:@(kCVPixelFormatType_8IndexedGray_WhiteIsZero) forKey:(id)kCVPixelBufferPixelFormatTypeKey];
            if ([AVFLog isLoggable:AVFLogInfo]) {
                NSLog(@"AVFCustomPlayer_setPixelFormat: set pixel format to Gray (8IndexedGray_WhiteIsZero)");
            }
            return TRUE;
        }
        if ([formatString containsString:@"422YpCbCr8"]) {
            [outputSettings setObject:@(kCVPixelFormatType_422YpCbCr8FullRange) forKey:(id)kCVPixelBufferPixelFormatTypeKey];
            if ([AVFLog isLoggable:AVFLogInfo]) {
                NSLog(@"AVFCustomPlayer_setPixelFormat: set pixel format to 422YpCbCr8 Full Range");
            }
            return TRUE;
        }
        return FALSE;
    }
    return FALSE;
}

/*
 * Returns the current pixel format as a short string
 * (see comment at setPixelFormat()).
 */
- (NSString *) getPixelFormat {
    if (outputSettings != NULL) {
        id pixelFormat = [outputSettings objectForKey:(id)kCVPixelBufferPixelFormatTypeKey];
        if ([pixelFormat isEqual: @(kCVPixelFormatType_24RGB)]) {
            return @"24RGB";
        }
        if ([pixelFormat isEqual: @(kCVPixelFormatType_32ARGB)]) {
            return @"32ARGB";
        }
        if ([pixelFormat isEqual: @(kCVPixelFormatType_32BGRA)]) {
            return @"32BGRA";
        }
        if ([pixelFormat isEqual: @(kCVPixelFormatType_8IndexedGray_WhiteIsZero)]) {
            return @"Gray";
        }
        if ([pixelFormat isEqual: @(kCVPixelFormatType_422YpCbCr8FullRange)]) {
            return @"422YpCbCr8";
        }
    }
    return NULL;
}

/*
 * Apparently AVAssetReader and AVAssetReaderTrackOutput cannot be reused for multiple read actions, 
 * at least, since the time range of the reader cannot be set multiple times, it can only continue reading
 * from the point of the last read. This is not useful for a media player which has to support jumps
 * to any position in the file/stream.
 * This functions calls the seconds based variant.
 */
- (long) getFrameSequenceFromTime:(long)time1 toTime:(long)time2 outputBuffer:(char *)bufferAddress bufferSize:(int)bufferSize outputIndexArray:(double [])outIndices outputArraySize:(int)arraySize {
    
    return [self getFrameSequenceFromTimeSeconds:(time1 / 1000.0) toTime:(time2 / 1000.0) outputBuffer:bufferAddress bufferSize:bufferSize outputIndexArray:outIndices outputArraySize:arraySize];
}

/*
 * Apparently AVAssetReader and AVAssetReaderTrackOutput cannot be reused for multiple read actions,
 * at least, since the time range of the reader cannot be set multiple times, it can only continue reading
 * from the point of the last read. This is not useful for a media player which has to support jumps
 * to any position in the file/stream.
 * Detects the bytesPerFrame if it hasn't already been detected.
 */
- (long) getFrameSequenceFromTimeSeconds: (double)time1 toTime:(double)time2 outputBuffer:(char *)bufferAddress bufferSize:(int)bufferSize outputIndexArray:(double [])outIndices outputArraySize:(int)arraySize {
    NSError *readerError = nil;
    AVAssetReader *assetReader = [AVAssetReader assetReaderWithAsset:self.mediaAsset error:&readerError];

    if (readerError != nil) {
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: AssetReader is NULL. %@", [readerError localizedDescription]);
        }
        [self setLastError: readerError];
        assetReader = NULL;
        return -1;
    }
    
    AVAssetReaderTrackOutput *trackOutput = [AVAssetReaderTrackOutput assetReaderTrackOutputWithTrack:videoTrack outputSettings:outputSettings];
    //[trackOutput setAlwaysCopiesSampleData:NO];// doesn't make a difference?
    [assetReader addOutput:trackOutput];
    [assetReader setTimeRange:CMTimeRangeFromTimeToTime(CMTimeMakeWithSeconds(time1, 1000), CMTimeMakeWithSeconds(time2, 1000))];

    if ([AVFLog isLoggable:AVFLogFine]) {
        NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: time range start: %f - duration %f, buffer size %d", CMTimeGetSeconds([assetReader timeRange].start), CMTimeGetSeconds([assetReader timeRange].duration), bufferSize);
    }
    
    BOOL readyForRead = [assetReader startReading];
    
    if (!readyForRead) {
        if ([AVFLog isLoggable:AVFLogWarning]) {
            NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: AssetReader is not ready for reading, returning");
        }
        assetReader = NULL;
        trackOutput = NULL;
        return -1;//?? return or retry after a wait?
    }
    
    long numBytesRead = 0;
    int index = 0;
    // test the reader status in a loop
    while (assetReader.status != AVAssetReaderStatusCompleted) {//assetReader.status == AVAssetReaderStatusReading
        CMSampleBufferRef sampleRef = [trackOutput copyNextSampleBuffer];

        // inspect samples
        if (sampleRef == NULL) {
            if ([AVFLog isLoggable:AVFLogFine]) {
                NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: No sample buffer, error: %@", [assetReader error]);
            }
            self.lastError = [assetReader error];
            // one frame or sample missed, return?
            // No: the loop always/mostly seems to end with a failed last sample read attempt
        } else {
            CMSampleTimingInfo timingInfoOut;
            CMSampleBufferGetSampleTimingInfo(sampleRef, 0, &timingInfoOut);
            double presTime = CMTimeGetSeconds(timingInfoOut.presentationTimeStamp);

            if ([AVFLog isLoggable:AVFLogFine]) {
                NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: Presentation time: %f (%f)", presTime, CMTimeGetSeconds(timingInfoOut.presentationTimeStamp));
            }
            
            CVImageBufferRef imgBufferRef = CMSampleBufferGetImageBuffer(sampleRef);
            if (imgBufferRef == NULL) {
                if ([AVFLog isLoggable:AVFLogWarning]) {
                    NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: CVImageBufferRef is null");// what to do, return??
                }
            } else {
                if ([AVFLog isLoggable:AVFLogAll] && numBytesRead == 0 && bytesPerFrame == 0) {// log once per session
                    size_t dataSize = CVPixelBufferGetDataSize(imgBufferRef);
                    NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: CVImageBufferRef Data size: %lu, Data width: %lu, Data height: %lu, Bytes per row: %lu", dataSize, CVPixelBufferGetWidth(imgBufferRef),CVPixelBufferGetHeight(imgBufferRef), CVPixelBufferGetBytesPerRow(imgBufferRef));

                    size_t padLeft, padRight;
                    size_t padTop, padBot;
                    CVPixelBufferGetExtendedPixels(imgBufferRef, &padLeft, &padRight, &padTop, &padBot);
                    NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: CVImageBufferRef Data padding: Left: %lu, Right: %lu, Top: %lu, Bottom: %lu", padLeft, padRight, padTop, padBot);
                    //CVPixelBufferGetPixelFormatType, conversion to string doesn't always work?
                    CFStringRef osTypeString = UTCreateStringForOSType(CVPixelBufferGetPixelFormatType(imgBufferRef));
                    NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: CVImageBufferRef pixel format type %@", osTypeString);
                }
                
                CVReturn lock = CVPixelBufferLockBaseAddress(imgBufferRef, kCVPixelBufferLock_ReadOnly);
                if (lock == kCVReturnSuccess) {
                    if ([AVFLog isLoggable:AVFLogAll]){
                        NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: successfully locked the pixel buffer");
                    }

                    UInt8* baseAddress = (UInt8*)CVPixelBufferGetBaseAddress(imgBufferRef);
                    // the number of bytes per image should be the same for each image, initialize the first time
                    if (bytesPerFrame == 0) {
                        bytesPerRow = CVPixelBufferGetBytesPerRow(imgBufferRef);
                        videoImageWidth = CVPixelBufferGetWidth(imgBufferRef);
                        videoImageHeight = CVPixelBufferGetHeight(imgBufferRef);// should be the same as videoHeight
                        
                        // log only once
                        if (videoImageWidth != self.videoWidth) {
                            if ([AVFLog isLoggable:AVFLogInfo]){
                                NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: The width of the pixel buffer is not equal to the width of the video image: %ld - %f", videoImageWidth, self.videoWidth);
                            }
                        }
                        if (videoImageHeight != self.videoHeight) {
                            if ([AVFLog isLoggable:AVFLogInfo]){
                                NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: The height of the pixel buffer is not equal to the height of the video image: %ld - %f", videoImageHeight, self.videoHeight);
                            }
                        }
                        // deal with padding in case the buffer width > video image width?
                        bytesPerFrame = bytesPerRow * videoImageHeight;
                        bytesPerPixel = bytesPerRow / videoImageWidth;
                        if ([AVFLog isLoggable:AVFLogInfo]){
                            NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: Bytes per Frame: %ld", bytesPerFrame);
                        }
                    }
                    
                    // copy into the bytebuffer, if it fits. First check time1 < frame duration (first frame)
                    if (numBytesRead + bytesPerFrame <= bufferSize) {
                        // special case if time1, the begin time, is 0, or at least < sample_duration
                        // sometimes the first presentation time is then >= sample_duration
                        if (time1 < self.frameDurationSeconds && index == 0) {
                            if (presTime >= self.frameDurationSeconds) {
                                // insert one(?) extra frame with time 0, a duplicate of the first frame
                                // this would be insufficient if the difference is more than 1 frame
                                memcpy(bufferAddress + numBytesRead, baseAddress, bytesPerFrame);
                                outIndices[index] = 0.0;
                                // increment
                                numBytesRead += bytesPerFrame;
                                index++;
                                if ([AVFLog isLoggable:AVFLogInfo]){
                                    NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: Inserted one frame at position 0, before presentation time %f", presTime);
                                }
                            }
                        }
                    }
                    // copy into the bytebuffer, if it fits
                    if (numBytesRead + bytesPerFrame <= bufferSize) {
                        // copy
                        memcpy(bufferAddress + numBytesRead, baseAddress, bytesPerFrame);
                        outIndices[index] = presTime;
                        // increment
                        numBytesRead += bytesPerFrame;
                        index++;
                        if ([AVFLog isLoggable:AVFLogFine]){
                            NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: Inserted frame buffer for presentation time %f", presTime);
                        }
                    } else {
                       if ([AVFLog isLoggable:AVFLogInfo]){
                           NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: Cannot copy image, buffer too small, need: %ld, available: %ld (occupied: %ld, of buffer: %d)", bytesPerFrame, (bufferSize - numBytesRead), numBytesRead, bufferSize);
                       }
                    }
                    
                    CVPixelBufferUnlockBaseAddress(imgBufferRef, kCVPixelBufferLock_ReadOnly);
                } else {
                    if ([AVFLog isLoggable:AVFLogWarning]){
                        NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: Failed to lock the pixel buffer");
                    }
                }
                CFRelease(imgBufferRef);
            }
            CMSampleBufferInvalidate(sampleRef);
            // CFRelease(sampleRef); // crashes
        }

        if (numBytesRead >= bufferSize) {
            // the buffer is full
            if ([AVFLog isLoggable:AVFLogFine]){
                NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: buffer is filled at %f seconds", outIndices[index - 1]);
            }
            break;
        }
        // try to prevent an unsuccesful read and unexpected preroll-complete notification
        if (index > 0 && outIndices[index - 1] + self.frameDurationSeconds > time2) {
            // next read will fail
            if ([AVFLog isLoggable:AVFLogFine]){
                NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: breaking read loop at %f seconds", outIndices[index - 1]);
            }
            [assetReader cancelReading];
            break;
        }
        
        // check status again
        if (assetReader.status == AVAssetReaderStatusCompleted) {
            if ([AVFLog isLoggable:AVFLogFine]){
                NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: Reader completed");
            }
            [assetReader cancelReading];
            break;
        } else if (assetReader.status == AVAssetReaderStatusFailed) {
            if ([AVFLog isLoggable:AVFLogFine]){
                NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: Reader failed");
            }
            [assetReader cancelReading];
            break;
        }
    }
    
    // test the first out index, the time of the first sample might not be the begin time of the first frame
    if (arraySize >= 3) {
        if ([AVFLog isLoggable:AVFLogFine]){
            NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: First three time stamps: %f, %f, %f", outIndices[0], outIndices[1], outIndices[2]);
        }
        if (outIndices[0] != -1 && outIndices[1] != -1 && outIndices[2] != -1) {
            if (outIndices[1] - outIndices[0] < outIndices[2] - outIndices[1]) {
                outIndices[0] = outIndices[1] - (outIndices[2] - outIndices[1]);
                if (outIndices[0] < 0) {
                    outIndices[0] = 0;
                }
                
            }
            if ([AVFLog isLoggable:AVFLogFine]){
                 NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: First three time stamps after correction: %f, %f, %f", outIndices[0], outIndices[1], outIndices[2]);
            }
        }
    }
    
    if ([AVFLog isLoggable:AVFLogFine]){
        NSLog(@"AVFCustomPlayer_getFrameSequenceFromTimeSeconds: Total number of bytes read: %ld (from %f to %f)", numBytesRead, time1, time2);
    }
    
    assetReader = NULL;
    trackOutput = NULL;
    
    return numBytesRead;
}

/*
 * Reads one frame (making sure that the bytesPerFrame value is calculated)
 * and and writes some information to the log.
 */
- (void) detectFrameSizeInBytes {
    // this can only be done after initialization of the asset reader and track output
    if (self.mediaAsset != nil && self.hasVideo) {
        // read one frame, e.g. by specifying a time range of at least frame duration + 1 ms
        // assume 4 bytes per pixel, allocate a buffer large enough to store two frames
        int bufferSize = 2 * 4 * self.videoWidth * self.videoHeight;
        char *bufArray = new char[bufferSize];
        double indices[3];
        double toTime = self.frameDurationSeconds + 0.004;
        
        long bytesRead = [self getFrameSequenceFromTimeSeconds:0 toTime:toTime outputBuffer:bufArray bufferSize:bufferSize outputIndexArray:indices outputArraySize:3];
        if ([AVFLog isLoggable:AVFLogFine]){
            NSLog(@"AVFCustomPlayer_detectFrameSizeInBytes: \n\
                Number of bytes read: %ld, \n\
                Video width: %f, \n\
                Video height: %f, \n\
                Pixels per row (pixel stride): %ld, \n\
                Bytes per pixel: %u, \n\
                Bytes per frame image: %ld", bytesRead, self.videoWidth, self.videoHeight, bytesPerRow, bytesPerPixel, bytesPerFrame);
        }
    }
}


@end
