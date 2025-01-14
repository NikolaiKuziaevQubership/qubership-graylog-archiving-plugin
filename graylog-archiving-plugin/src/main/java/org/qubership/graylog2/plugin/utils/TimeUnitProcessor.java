package org.qubership.graylog2.plugin.utils;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class TimeUnitProcessor {

    public long toLong(String s) {
        final Pattern p = Pattern.compile("(\\d+)([hmsd])");
        final Matcher m = p.matcher(s);
        long totalMillis = 0;
        while (m.find())
        {
            final int duration = Integer.parseInt(m.group(1));
            final TimeUnit interval = toTimeUnit(m.group(2));
            final long l = interval.toMillis(duration);
            totalMillis = totalMillis + l;
        }
        return totalMillis;
    }

    public static TimeUnit toTimeUnit(final String c)
    {
        switch (c)
        {
            case "h": return TimeUnit.HOURS;
            case "d": return TimeUnit.DAYS;
            case "m": return TimeUnit.MINUTES;
            default: throw new IllegalArgumentException(String.format("%s is not a valid code [mhd]", c));
        }
    }
}
