package fr.jrds.snmpcodec;

import org.snmp4j.log.ConsoleLogFactory;
import org.snmp4j.log.LogFactory;
import org.snmp4j.log.LogLevel;

import fr.jrds.snmpcodec.log.LogAdapter;

public class LogUtils {

    public static LogAdapter setLevel(Class<?> clazz, String... loggers) {
        LogLevel level = LogLevel.toLevel(System.getProperty("testloglevel", "trace").toUpperCase());
        LogFactory.setLogFactory(new ConsoleLogFactory());
        LogAdapter logger = LogAdapter.getLogger(clazz);
        logger.setLogLevel(level);
        for(String c: loggers) {
            LogFactory.getLogger(c).setLogLevel(level);
        }
        return logger;
    }

}
