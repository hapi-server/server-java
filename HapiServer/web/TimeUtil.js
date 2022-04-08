/**
 * Utilities for times in IsoTime strings (limited set of ISO8601 times)
 * Examples of isoTime strings include:<ul>
 * <li>2020-04-21Z
 * <li>2020-04-21T12:20Z
 * <li>2020-04-21T23:45:67.000000001Z (nanosecond limit)
 * <li>2020-112Z (day-of-year instead of $Y-$m-$d)
 * <li>2020-112T23:45:67.000000001 (note Z is assumed)
 * </ul>
 *
 * @author jbf
 */

/**
  * Number of time digits: year, month, day, hour, minute, second, nanosecond
  */
TIME_DIGITS = 7;
    
/**
 * Number of digits in time representation: year, month, day
 */
DATE_DIGITS = 3;
    
function format2( d ) {
    if ( d<10 ) {
        return '0'+d;
    } else {
        return ''+d;
    }
}

function format3( d ) {
    if ( d<10 ) {
        return '00'+d;
    } else if ( d<100 ) {
        return '0'+d;
    } else {
        return ''+d;
    }
}

function format4( d ) {
    if ( d<10 ) {
        return '000'+d;
    } else if ( d<100 ) {
        return '00'+d;
    } else if ( d<1000 ) {
        return '0'+d;
    } else {
        return ''+d;
    }
}

function format9( d ) {
    if ( d<10 ) {
        return '00000000'+d;
    } else if ( d<100 ) {
        return '0000000'+d;
    } else if ( d<1000 ) {
        return '000000'+d;
    } else if ( d<10000 ) {
        return '00000'+d;
    } else if ( d<100000 ) {
        return '0000'+d;
    } else if ( d<1000000 ) {
        return '000'+d;
    } else if ( d<10000000 ) {
        return '00'+d;
    } else if ( d<100000000 ) {
        return '0'+d;
    } else {
        return ''+d;
    }
}

function reformatIsoTime(exampleForm, time) {
    var c = exampleForm.charAt(8);
    var nn = TimeUtil.isoTimeToArray(TimeUtil.normalizeTimeString(time));
    if ( c==='T' ) {
        nn[2] = TimeUtil.dayOfYear(nn[0], nn[1], nn[2]);
        nn[1] = 1;
        time = format4( nn[0] ) + "-" + format3(nn[2]) + 
                "T" + format2( nn[3] ) +":" + format2( nn[4] ) + ":" + format2( nn[5] ) + '.' + format9(nn[6]) + "Z";
    } else if ( c==='Z' ) {
        nn[2] = TimeUtil.dayOfYear(nn[0], nn[1], nn[2]);
        nn[1] = 1;
        time = format4( nn[0] ) + "-" + format3(nn[2]) +  "Z";
    } else {
        if (exampleForm.length === 10) {
            c = 'Z';
        } else {
            c = exampleForm.charAt(10);
        }
        if ( c==='T' ) {
            time = format4( nn[0] ) + "-" + format2( nn[1] ) + "-" + format2( nn[2] )
            + "T" + format2( nn[3] ) +":" + format2( nn[4] ) + ":" + format2( nn[5] ) + '.' + format9(nn[6]) + "Z";
        } else if ( c==='Z' ) {
            time = "" + nn[0] + "-" + format2( nn[1] ) + '-' + format2( nn[2] ) + 'Z';
        }                
    }

    if ( exampleForm.endsWith("Z") ) {
        return time.substring(0, exampleForm.length - 1) + "Z";
    }
    else {
        return time.substring(0, exampleForm.length);
    }
};


DAYS_IN_MONTH = [[0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31, 0], 
    [0, 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31, 0]];

DAY_OFFSET = [[0, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365], 
    [0, 0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366]];

function isLeapYear(year) {
    if (year < 1582 || year > 2400) {
        throw "year must be between 1582 and 2400";
    }
    return (year % 4) === 0 && (year % 400 === 0 || year % 100 !== 0);
};

/**
 * normalize the decomposed (seven digit) time by expressing day of year and month and day
 * of month, and moving hour="24" into the next day. This also handles day
 * increment or decrements, by:<ul>
 * <li>handle day=0 by decrementing month and adding the days in the new
 * month.
 * <li>handle day=32 by incrementing month.
 * <li>handle negative components by borrowing from the next significant.
 * </ul>
 * Note that [Y,1,dayOfYear,...] is accepted, but the result will be Y,m,d.
 * @param {int[]} time the seven-component time Y,m,d,H,M,S,nanoseconds
 */
function normalizeTime(time) {
    while ((time[3] >= 24)) {
        time[2] += 1;
        time[3] -= 24;
    }
    ;
    if (time[6] < 0) {
        time[5] -= 1;
        time[6] += 1000000000;
    }
    if (time[5] < 0) {
        time[4] -= 1;
        time[5] += 60;
    }
    if (time[4] < 0) {
        time[3] -= 1;
        time[4] += 60;
    }
    if (time[3] < 0) {
        time[2] -= 1;
        time[3] += 24;
    }
    if (time[2] < 1) {
        time[1] -= 1;
        var daysInMonth = time[1] === 0 ? 31 : DAYS_IN_MONTH[isLeapYear(time[0]) ? 1 : 0][time[1]];
        time[2] += daysInMonth;
    }
    if (time[1] < 1) {
        time[0] -= 1;
        time[1] += time[1] + 12;
    }
    if (time[3] > 24) {
        throw error("time[3] is greater than 24 (hours)");
    }
    if (time[1] > 12) {
        time[0] = time[0] + 1;
        time[1] = time[1] - 12;
    }
    if (time[1] === 12 && time[2] > 31 && time[2] < 62) {
        time[0] = time[0] + 1;
        time[1] = 1;
        time[2] = time[2] - 31;
        return;
    }
    var leap = isLeapYear(time[0]) ? 1 : 0;
    if (time[2] === 0) {
        time[1] = time[1] - 1;
        if (time[1] === 0) {
            time[0] = time[0] - 1;
            time[1] = 12;
        }
        time[2] = DAYS_IN_MONTH[leap][time[1]];
    }
    var d = DAYS_IN_MONTH[leap][time[1]];
    while ((time[2] > d)) {
        {
            time[1]++;
            time[2] -= d;
            d = DAYS_IN_MONTH[leap][time[1]];
            if (time[1] > 12) {
                throw error("time[2] is too big");
            }
        }
    };
    return time;
}

function now() {
    var p = new Date( Date.now() );
    return [ p.getUTCFullYear(), 
        p.getUTCMonth(), 
        p.getUTCDate(), 
        p.getUTCHours(), 
        p.getUTCMinutes(), 
        p.getUTCSeconds(), 
        p.getUTCMilliseconds() * 1e6 ];
}