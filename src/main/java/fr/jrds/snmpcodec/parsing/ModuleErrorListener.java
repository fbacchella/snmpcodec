package fr.jrds.snmpcodec.parsing;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import fr.jrds.snmpcodec.MibException;
import fr.jrds.snmpcodec.log.LogAdapter;

public class ModuleErrorListener extends BaseErrorListener {

    final LogAdapter logger = LogAdapter.getLogger(ModuleErrorListener.class);

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer,
            Object offendingSymbol, int line, int charPositionInLine,
            String msg, RecognitionException e) {
        if (recognizer instanceof ASNLexer) {
            // useless noise
        }  else if (recognizer instanceof ASNParser) {
            if (e instanceof WrappedException) {
                WrappedException mex = (WrappedException)e;
                if (mex.getRootException() instanceof MibException.DuplicatedModuleException) {
                    //throw ((MibException.DuplicatedModuleException)mex.getRootException()).getNonChecked();
                } else if (mex.getRootException() instanceof MibException.DuplicatedSymbolException) {
                } else {
                    logger.debug("%s at %s", mex.getMessage(), mex.getLocation());
                }
            } else {
                String message = "";
                if (e != null && e.getMessage() != null) {
                    message = e.getMessage();
                } else if (e!= null) {
                    message = e.getClass().getSimpleName();
                } else {
                    message = "Some error";
                }
                logger.debug("%s at %s, line %s:%s", message, recognizer.getInputStream().getSourceName(), line, charPositionInLine);
            }
        }
    }

}
