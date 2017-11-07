package fr.jrds.snmpcodec.parsing;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.snmp4j.log.LogLevel;

import fr.jrds.snmpcodec.MibException;

public class ModuleErrorListener extends BaseErrorListener {

    private final ModuleListener modulelistener;

    public ModuleErrorListener(ModuleListener modulelistener) {
        this.modulelistener = modulelistener;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer,
            Object offendingSymbol, int line, int charPositionInLine,
            String msg, RecognitionException e) {
        LogLevel loggerLevel = MibLoader.MIBPARSINGLOGGER.getEffectiveLogLevel();
        LogLevel usedLevel = LogLevel.INFO;

        if (e instanceof WrappedException) {
            WrappedException mex = (WrappedException)e;
            if (mex.getRootException() instanceof MibException.DuplicatedModuleException
                    || mex.getRootException() instanceof MibException.DuplicatedSymbolException ) {
                usedLevel = LogLevel.DEBUG;
            } else if (mex.getRootException() instanceof NullPointerException) {
                usedLevel = LogLevel.ERROR;
            }
        }
        if (modulelistener.firstError && usedLevel.getLevel() >= loggerLevel.getLevel()) {
            MibLoader.MIBPARSINGLOGGER.info(recognizer.getInputStream().getSourceName());
            modulelistener.firstError = false;
        }

        if (usedLevel.getLevel() == LogLevel.LEVEL_DEBUG) {
            MibLoader.MIBPARSINGLOGGER.debug("    line %s:%s: %s", line, charPositionInLine, msg);
        } else if (usedLevel.getLevel() == LogLevel.LEVEL_INFO) {
            MibLoader.MIBPARSINGLOGGER.info("    line %s:%s: %s", line, charPositionInLine, msg);
        }
    }

}
