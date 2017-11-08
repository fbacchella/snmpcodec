package fr.jrds.snmpcodec.log;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.snmp4j.log.LogFactory;
import org.snmp4j.log.LogLevel;

public class LogAdapter {

    private static final Map<String, LogAdapter> loggercache = new ConcurrentHashMap<>();

    public static LogAdapter getLogger(Class<?> c) {
        return getLogger(c.getName());
    }

    public static LogAdapter getLogger(String l) {
        return loggercache.computeIfAbsent(l,  i-> new LogAdapter(i));
    }

    private final org.snmp4j.log.LogAdapter adapter;

    private LogAdapter(String l) {
        adapter = LogFactory.getLogger(l);
    }

    public boolean isDebugEnabled() {
        return adapter.isDebugEnabled();
    }

    public boolean isInfoEnabled() {
        return adapter.isInfoEnabled();
    }

    public boolean isWarnEnabled() {
        return adapter.isWarnEnabled();
    }

    public void debug(String format, Object... args) {
        adapter.debug(LogString.make(format, args));
    }

    public void info(String format, Object... args) {
        adapter.info(LogString.make(format, args));
    }

    public void warn(String format, Object... args) {
        adapter.warn(LogString.make(format, args));
    }

    public void error(String format, Object... args) {
        adapter.error(LogString.make(format, args));
    }

    public void fatal(String format, Object... args) {
        adapter.fatal(LogString.make(format, args));
    }

    public void error(Throwable throwable, String format, Object... args) {
        adapter.error(LogString.make(format, args), throwable);
    }

    public void fatal(Throwable throwable, String format, Object... args) {
        adapter.fatal(LogString.make(format, args), throwable);
    }

    public void setLogLevel(LogLevel level) {
        adapter.setLogLevel(level);
    }

    public LogLevel getLogLevel() {
        return adapter.getLogLevel();
    }

    public LogLevel getEffectiveLogLevel() {
        return adapter.getEffectiveLogLevel();
    }

    public String getName() {
        return adapter.getName();
    }

    public Iterator<?> getLogHandler() {
        return adapter.getLogHandler();
    }

}
