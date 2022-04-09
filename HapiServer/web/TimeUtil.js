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
    var nn = isoTimeToArray(normalizeTimeString(time));
    if ( c==='T' ) {
        nn[2] = dayOfYear(nn[0], nn[1], nn[2]);
        nn[1] = 1;
        time = format4( nn[0] ) + "-" + format3(nn[2]) + 
                "T" + format2( nn[3] ) +":" + format2( nn[4] ) + ":" + format2( nn[5] ) + '.' + format9(nn[6]) + "Z";
    } else if ( c==='Z' ) {
        nn[2] = dayOfYear(nn[0], nn[1], nn[2]);
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

monthNames = [
    "jan", "feb", "mar", "apr", "may", "jun",
    "jul", "aug", "sep", "oct", "nov", "dec"
];

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

/**
 * fast parser requires that each character of string is a digit.  Note this 
 * does not check the the numbers are digits!
 *
 * @param s string containing an integer
 * @return the integer
 */
function parseInt(s) {
    var result;
    var len = s.length;
    for (var i = 0; i < len; i++) {
        var c = s.charAt(i);
        if ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(c) < 48 || (function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(c) >= 58) {
            throw "only digits are allowed in string";
        }
    }
    switch ((len)) {
        case 2:
            result = 10 * ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(0)) - 48) + ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(1)) - 48);
            return result;
        case 3:
            result = 100 * ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(0)) - 48) + 10 * ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(1)) - 48) + ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(2)) - 48);
            return result;
        default:
            result = 0;
            for (var i = 0; i < s.length; i++) {
                result = 10 * result + ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(i)) - 48);
            }
            return result;
    }
};
  
/**
 * fast parser requires that each character of string is a digit.
 *
 * @param s the number, containing 1 or more digits.
 * @return the int value
 */  
function parseInt(s, deft) {
    if (s == null) {
        return deft;
    }
    var result;
    for (var i = 0; i < s.length; i++) {
        var c = s.charAt(i);
        if ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(c) < 48 || (function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(c) >= 58) {
            throw Error("only digits are allowed in string");
        }
    }
    switch ((s.length)) {
        case 2:
            result = 10 * ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(0)) - 48) + ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(1)) - 48);
            return result;
        case 3:
            result = 100 * ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(0)) - 48) + 10 * ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(1)) - 48) + ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(2)) - 48);
            return result;
        default:
            result = 0;
            for (var i = 0; i < s.length; i++) {
                result = 10 * result + ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(s.charAt(i)) - 48);
            }
            return result;
    }
};

function parseDouble(val, deft) {
    if (val == null) {
        if (deft !== -99) {
            return deft;
        }
        else {
            throw Error("bad digit");
        }
    }
    var n = val.length - 1;
    if ( /* isLetter *//[a-zA-Z]/.test(val.charAt(n)[0])) {
        return /* parseDouble */ parseFloat(val.substring(0, n));
    }
    else {
        return /* parseDouble */ parseFloat(val);
    }
};

const simpleFloat = new RegExp("\\d?\\.?\\d+");

/**
  * Pattern matching valid ISO8601 durations, like "P1D" and "PT3H15M"
 */
const iso8601durationPattern = new RegExp("P((\\d+)Y)?((\\d+)M)?((\\d+)D)?(T((\\d+)H)?((\\d+)M)?((\\d?\\.?\\d+)S)?)?");

/**
 * returns a 7 element array with [year,mon,day,hour,min,sec,nanos]. Note
 * this does not allow fractional day, hours or minutes! Examples
 * include:<ul>
 * <li>P1D - one day
 * <li>PT1M - one minute
 * <li>PT0.5S - 0.5 seconds
 * </ul>
 * TODO: there exists more complete code elsewhere.
 *
 * @param {string} stringIn theISO8601 duration.
 * @return {int[]} 7-element array with [year,mon,day,hour,min,sec,nanos]
 * @throws ParseException if the string does not appear to be valid.
 * @see #iso8601duration
 * @see #TIME_DIGITS
 */
function parseISO8601Duration(stringIn) {
    const m= iso8601durationPattern.exec(stringIn);
    if ( m ) {
        var dsec = parseDouble(m[13], 0);
        var sec = (dsec | 0);
        var nanosec = (((dsec - sec) * 1.0E9) | 0);
        return [ parseInt(m[2], 0), parseInt(m[4], 0), parseInt(m[6], 0), parseInt(m[9], 0), parseInt(m[11], 0), sec, nanosec];
    } else {
        if ( /* contains */(stringIn.indexOf("P") != -1) && /* contains */ (stringIn.indexOf("S") != -1) && !(stringIn.indexOf("T") != -1)) {
            throw Error("ISO8601 duration expected but not found.  Was the T missing before S?");
        } else {
            throw Error("ISO8601 duration expected but not found.");
        }
    }
};
            
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
        throw Error("time[3] is greater than 24 (hours)");
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
                throw Error("time[2] is too big");
            }
        }
    };
    return time;
}

/**
 * calculate the day of week, where 0 means Monday, 1 means Tuesday, etc.  For example,
 * 2022-03-12 is a Saturday, so 5 is returned.
 * @param {number} year the year
 * @param {number} month the month
 * @param {number} day the day of the month
 * @return {number} the day of the week.
 */
function dayOfWeek(year, month, day) {
    var jd = julianDay(year, month, day);
    var daysSince2022 = jd - julianDay(2022, 1, 1);
    var mod7 = (daysSince2022 - 2) % 7;
    if (mod7 < 0)
        mod7 = mod7 + 7;
    return mod7;
};

/**
 * calculate the week of year, inserting the month into time[1] and day into time[2]
 * for the Monday which is the first day of that week.  Note week 0 is excluded from
 * ISO8601, but since the Linux date command returns this in some cases, it is allowed to
 * mean the same as week 52 of the previous year.  See
 * <a href='https://en.wikipedia.org/wiki/ISO_8601#Week_dates' target='_blank'>Wikipedia ISO8601#Week_dates</a>.
 *
 * @param {number} year the year of the week.
 * @param {number} weekOfYear the week of the year, where week 01 is starting with the Monday in the period 29 December - 4 January.
 * @param {int[]} time the result is placed in here, where time[0] is the year provided, and the month and day are calculated.
 */
function fromWeekOfYear(year, weekOfYear, time) {
    time[0] = year;
    var day = dayOfWeek(year, 1, 1);
    var doy;
    if (day < 4) {
        doy = (weekOfYear * 7 - 7 - day) + 1;
        if (doy < 1) {
            time[0] = time[0] - 1;
            doy = doy + (isLeapYear(time[0]) ? 366 : 365);
        }
    }
    else {
        doy = weekOfYear * 7 - day + 1;
    }
    time[1] = 1;
    time[2] = doy;
    normalizeTime(time);
};

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
 * return seven-element array [ year, months, days, hours, minutes, seconds, nanoseconds ]
 * preserving the day of year notation if this was used. See the class
 * documentation for allowed time formats, which are a subset of ISO8601
 * times.  This also supports "now", "now-P1D", and other simple extensions.  Note
 * ISO8601-1:2019 disallows 24:00 to be used for the time, but this is still allowed here.
 * The following are valid inputs:<ul>
 * <li>2021
 * <li>2020-01-01
 * <li>2020-01-01Z
 * <li>2020-01-01T00Z
 * <li>2020-01-01T00:00Z
 * <li>2022-W08
 * <li>now
 * <li>now-P1D
 * <li>lastday-P1D
 * <li>lasthour-PT1H
 * </ul>
 *
 * @param {string} time isoTime to decompose
 * @return {int[]} the decomposed time
 * @throws IllegalArgumentException when the time cannot be parsed.
 * @see #isoTimeFromArray(int[])
 * @see #parseISO8601Time(java.lang.String)
 */
function isoTimeToArray(time) {
    var result;
    if (time.length === 4) {
        result = [ parseInt(time), 1, 1, 0, 0, 0, 0];
    }
    else if ( /* startsWith */(function (str, searchString, position) {
        if (position === void 0) { position = 0; }
        return str.substr(position, searchString.length) === searchString;
    })(time, "now") || /* startsWith */ (function (str, searchString, position) {
        if (position === void 0) { position = 0; }
        return str.substr(position, searchString.length) === searchString;
    })(time, "last")) {
        var n = void 0;
        var remainder = void 0;
        if ( /* startsWith */(function (str, searchString, position) {
            if (position === void 0) { position = 0; }
            return str.substr(position, searchString.length) === searchString;
        })(time, "now")) {
            n = now();
            remainder = time.substring(3);
        }
        else {
            var re = new RegExp("last([a-z]+)([\\+|\\-]P.*)?");
            var m = re.exec(time);
            if (m) {
                n = now();
                var unit = m[1];
                remainder = m[2];
                var idigit = 0;
                switch (unit) {
                    case "year":
                        idigit = 1;
                        break;
                    case "month":
                        idigit = 2;
                        break;
                    case "day":
                        idigit = 3;
                        break;
                    case "hour":
                        idigit = 4;
                        break;
                    case "minute":
                        idigit = 5;
                        break;
                    case "second":
                        idigit = 6;
                        break;
                    default:
                        throw Error("unsupported unit: " + unit);
                }
                for (var id = Math.max(1, idigit); id < DATE_DIGITS; id++) {
                    n[id] = 1;
                }
                for (var id = Math.max( DATE_DIGITS, idigit); id < TIME_DIGITS; id++) {
                    n[id] = 0;
                }
            }
            else {
                throw Error("expected lastday+P1D, etc");
            }
        }
        if (remainder == null || remainder.length === 0) {
            return n;
        }
        else if ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(remainder.charAt(0)) == '-'.charCodeAt(0)) {
            try {
                return subtract(n, parseISO8601Duration(remainder.substring(1)));
            }
            catch (ex) {
                throw new Error(ex.message);
            }
        }
        else if ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(remainder.charAt(0)) == '+'.charCodeAt(0)) {
            try {
                return add(n, parseISO8601Duration(remainder.substring(1)));
            }
            catch (ex) {
                throw new Error(ex.message);
            }
        }
        return now();
    }
    else {
        if (time.length < 7) {
            throw new Error("time must have 4 or greater than 7 elements");
        }
        if (time.length === 7) {
            if ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(time.charAt(4)) == 'W'.charCodeAt(0)) {
                var year = parseInt(time.substring(0, 4));
                var week = parseInt(time.substring(5));
                result = [year, 0, 0, 0, 0, 0, 0];
                fromWeekOfYear(year, week, result);
                time = "";
            }
            else {
                result = [parseInt(time.substring(0, 4)), parseInt(time.substring(5, 7)), 1, 0, 0, 0, 0];
                time = "";
            }
        }
        else if (time.length === 8) {
            if ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(time.charAt(5)) == 'W'.charCodeAt(0)) {
                var year = parseInt(time.substring(0, 4));
                var week = parseInt(time.substring(6));
                result = [year, 0, 0, 0, 0, 0, 0];
                fromWeekOfYear(year, week, result);
                time = "";
            }
            else {
                result = [parseInt(time.substring(0, 4)), 1, parseInt(time.substring(5, 8)), 0, 0, 0, 0];
                time = "";
            }
        }
        else if ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(time.charAt(8)) == 'T'.charCodeAt(0)) {
            result = [parseInt(time.substring(0, 4)), 1, parseInt(time.substring(5, 8)), 0, 0, 0, 0];
            time = time.substring(9);
        }
        else if ((function (c) { return c.charCodeAt == null ? c : c.charCodeAt(0); })(time.charAt(8)) == 'Z'.charCodeAt(0)) {
            result = [parseInt(time.substring(0, 4)), 1, parseInt(time.substring(5, 8)), 0, 0, 0, 0];
            time = time.substring(9);
        }
        else {
            result = [parseInt(time.substring(0, 4)), parseInt(time.substring(5, 7)), parseInt(time.substring(8, 10)), 0, 0, 0, 0];
            if (time.length === 10) {
                time = "";
            }
            else {
                time = time.substring(11);
            }
        }
        if ( /* endsWith */(function (str, searchString) { var pos = str.length - searchString.length; var lastIndex = str.indexOf(searchString, pos); return lastIndex !== -1 && lastIndex === pos; })(time, "Z")) {
            time = time.substring(0, time.length - 1);
        }
        if (time.length >= 2) {
            result[3] = parseInt(time.substring(0, 2));
        }
        if (time.length >= 5) {
            result[4] = parseInt(time.substring(3, 5));
        }
        if (time.length >= 8) {
            result[5] = parseInt(time.substring(6, 8));
        }
        if (time.length > 9) {
            result[6] = ((Math.pow(10, 18 - time.length)) | 0) * parseInt(time.substring(9));
        }
        normalizeTime(result);
    }
    return result;
};

/**
 * return the day of year for the given year, month, and day. For example, in
 * Jython:
 * <pre>
 * {@code
 * from org.hapiserver.TimeUtil import *
 * print dayOfYear( 2020, 4, 21 ) # 112
 * }
 * </pre>
 *
 * @param {number} year the year
 * @param {number} month the month, from 1 to 12.
 * @param {number} day the day in the month.
 * @return {number} the day of year.
 */
function dayOfYear(year, month, day) {
    if (month === 1) {
        return day;
    }
    if (month < 1) {
        throw new Error("month must be greater than 0.");
    }
    if (month > 12) {
        throw new Error("month must be less than 12.");
    }
    if (day > 366) {
        throw new Error("day (" + day + ") must be less than 366.");
    }
    var leap = isLeapYear(year) ? 1 : 0;
    return DAY_OFFSET[leap][month] + day;
}

/**
 * return "2" (February) for 45 for example.
 * @param {number} year the year
 * @param {number} doy the day of year.
 * @return {number} the month 1-12 of the day.
 */
function monthForDayOfYear(year, doy) {
    var leap = isLeapYear(year) ? 1 : 0;
    var dayOffset = DAY_OFFSET[leap];
    if (doy < 1)
        throw new Error("doy must be 1 or more");
    if (doy > dayOffset[13]) {
        throw new Error("doy must be less than or equal to " + dayOffset[13]);
    }
    for (var i = 12; i > 1; i--) {
        if (dayOffset[i] < doy) {
            return i;
        }
    }
    return 1;
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
        throw Error("year must be more than 1582");
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
        a.push(0); return a; })(TIME_DIGITS);
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
        a.push(0); return a; })(TIME_DIGITS);
    for (var i = 0; i < TIME_DIGITS; i++) {
        result[i] = base[i] + offset[i];
    }
    normalizeTime(result);
    return result;
};