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

    /**
     * Rewrite the time using the format of the example time, which must start with
     * $Y-$jT, $Y-$jZ, or $Y-$m-$d. For example,
     * <pre>
     * {@code
     * from org.hapiserver.TimeUtil import *
     * print rewriteIsoTime( '2020-01-01T00:00Z', '2020-112Z' ) # ->  '2020-04-21T00:00Z'
     * }
     * </pre> This allows direct comparisons of times for sorting. 
     * This works by looking at the character in the 8th position (starting with zero) of the 
     * exampleForm to see if a T or Z is present (YYYY-jjjTxxx).
     *
     * TODO: there's
     * an optimization here, where if input and output are both $Y-$j or both
     * $Y-$m-$d, then we need not break apart and recombine the time
     * (isoTimeToArray call can be avoided).
     *
     * @param exampleForm isoTime string.
     * @param time the time in any allowed isoTime format
     * @return same time but in the same form as exampleForm.
     */
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

monthNames = {
    "jan", "feb", "mar", "apr", "may", "jun",
    "jul", "aug", "sep", "oct", "nov", "dec"
};

/**
 * return the English month name, abbreviated to three letters, for the
 * month number.
 *
 * @param i month number, from 1 to 12.
 * @return the month name, like "Jan" or "Dec"
 */
function monthNameAbbrev(i) {
    return monthNames[i - 1];
}

/**
 * return the month number for the English month name, such as "Jan" (1) or
 * "December" (12). The first three letters are used to look up the number,
 * and must be one of: "Jan", "Feb", "Mar", "Apr", "May", "Jun",
 * "Jul", "Aug", "Sep", "Oct", "Nov", or "Dec" (case insensitive).
 * @param s the name (case-insensitive, only the first three letters are used.)
 * @return the number, for example 1 for "January"
 * @throws ParseException when month name is not recognized.
 */
function monthNumber(s) {
    if (s.length() < 3) {
        throw "need at least three letters";
    }
    s = s.substring(0, 3).toLowerCase()
    for (var i = 0; i < 12; i++) {
        if (s==monthNames[i]) {
            return i + 1;
        }
    }
    throw "Unable to parse month"
}
    
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

/**
 * return the julianDay for the year month and day. This was verified
 * against another calculation (julianDayWP, commented out above) from
 * http://en.wikipedia.org/wiki/Julian_day. Both calculations have 20
 * operations.
 *
 * @param {number} year calendar year greater than 1582.
 * @param {number} month the month number 1 through 12.
 * @param {number} day day of month. For day of year, use month=1 and doy for day.
 * @return {number} the Julian day
 * @see #fromJulianDay(int)
 */
function julianDay(year, month, day) {
    if (year <= 1582) {
        throw error("year must be more than 1582");
    }
    var jd = 367 * year - (7 * (year + ((month + 9) / 12 | 0)) / 4 | 0) 
            - (3 * (((year + ((month - 9) / 7 | 0)) / 100 | 0) + 1) / 4 | 0) 
            + (275 * month / 9 | 0) + day + 1721029;
    return jd;
};

/**
 * Break the Julian day apart into month, day year. This is based on
 * http://en.wikipedia.org/wiki/Julian_day (GNU Public License), and was
 * introduced when toTimeStruct failed when the year was 1886.
 *
 * @see #julianDay( int year, int mon, int day )
 * @param {number} julian the (integer) number of days that have elapsed since the
 * initial epoch at noon Universal Time (UT) Monday, January 1, 4713 BC
 * @return {int[]} a TimeStruct with the month, day and year fields set.
 */
function fromJulianDay(julian) {
    var j = julian + 32044;
    var g = (j / 146097 | 0);
    var dg = j % 146097;
    var c = (((dg / 36524 | 0) + 1) * 3 / 4 | 0);
    var dc = dg - c * 36524;
    var b = (dc / 1461 | 0);
    var db = dc % 1461;
    var a = (((db / 365 | 0) + 1) * 3 / 4 | 0);
    var da = db - a * 365;
    var y = g * 400 + c * 100 + b * 4 + a;
    var m = ((da * 5 + 308) / 153 | 0) - 2;
    var d = da - ((m + 4) * 153 / 5 | 0) + 122;
    var Y = y - 4800 + ((m + 2) / 12 | 0);
    var M = (m + 2) % 12 + 1;
    var D = d + 1;
    var result = (function (s) { var a = []; while (s-- > 0)
        a.push(0); return a; })(TimeUtil.TIME_DIGITS);
    result[0] = Y;
    result[1] = M;
    result[2] = D;
    return result;
};

/**
 * subtract the offset from the base time.
 *
 * @param {int[]} base a time
 * @param {int[]} offset offset in each component.
 * @return {int[]} a time
 */
function subtract(base, offset) {
    var result = (function (s) { var a = []; while (s-- > 0)
        a.push(0); return a; })(TIME_DIGITS);
    for (var i = 0; i < TIME_DIGITS; i++) {
        result[i] = base[i] - offset[i];
    }
    if (result[0] > 400) {
        normalizeTime(result);
    }
    return result;
};

/**
 * add the offset to the base time. This should not be used to combine two
 * offsets, because the code has not been verified for this use.
 *
 * @param {int[]} base a time
 * @param {int[]} offset offset in each component.
 * @return {int[]} a time
 */
function add(base, offset) {
    var result = (function (s) { var a = []; while (s-- > 0)
        a.push(0); return a; })(TimeUtil.TIME_DIGITS);
    for (var i = 0; i < TimeUtil.TIME_DIGITS; i++) {
        result[i] = base[i] + offset[i];
    }
    TimeUtil.normalizeTime(result);
    return result;
};