//
//  AVFLog.h
//  
//
//  Created by Han Sloetjes on 26/04/18.
//  Copyright (c) 2018 MPI. All rights reserved.
//

#ifndef _AVFLog_h
#define _AVFLog_h

#import "nl_mpi_avf_player_JAVFLogLevel.h"

/*!
 @enum AVFLogLevel
 @abstract
	These constants are short versions of the constants in nl_mpi_avf_player_JAVFLogLevel
 
 @constant	 AVFLogAll
	Indicates that all messages should be logged.
 */
typedef NS_ENUM(NSInteger, AVFLogLevel) {
    AVFLogAll = nl_mpi_avf_player_JAVFLogLevel_ALL,
    AVFLogFine = nl_mpi_avf_player_JAVFLogLevel_FINE,
    AVFLogInfo = nl_mpi_avf_player_JAVFLogLevel_INFO,
    AVFLogWarning = nl_mpi_avf_player_JAVFLogLevel_WARNING,
    AVFLogOff = nl_mpi_avf_player_JAVFLogLevel_OFF
};
/*
 * Interface for logging, only contains class level methods.
 */
@interface AVFLog : NSObject {
 
}

/*
 * Sets the log level, allows other values than specified in the
 * AVFLogLevel enumeration.
 */
+ (void) setLogLevel: (long) level;

/*
 * Returns the current log level as a long value
 */
+ (long) getLogLevel;

/*
 * Returns whether a message of the requested level should be logged
 * given the current log level.
 */
+ (BOOL) isLoggable: (AVFLogLevel) logLevel;

@end
#endif
