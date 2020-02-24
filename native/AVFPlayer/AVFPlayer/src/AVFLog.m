//
//  AVFLog.m
//  AVFPlayer
//
//  Created by Han Sloetjes on 26/04/18.
//  Copyright (c) 2018 MPI. All rights reserved.
//

#import "AVFLog.h"

// the scope of this field is this class / this file
static long prLogLevel = AVFLogInfo;

/*
 * Implementation of the AV logging settings.
 */
@implementation AVFLog

/*
 * Sets the logging level.
 */
+ (void) setLogLevel: (long) logLevel {
    prLogLevel = logLevel;
}

/*
 * Returns the current level for logging.
 */
+ (long) getLogLevel {
    return prLogLevel;
}

/*
 * Returns whether the specified level should be logged
 * (logLevel >= currentLogLevel).
 */
+ (BOOL) isLoggable: (AVFLogLevel) logLevel {
    return (BOOL)(logLevel >= prLogLevel);
}

@end
