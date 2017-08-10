package fr.jrds.snmpcodec.parsing;

import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

public class WrappedException extends RecognitionException {
    
    private final Exception rootException;

    public WrappedException(Exception e, Recognizer<?, ?> recognizer, IntStream input, ParserRuleContext ctx) {
        super(recognizer, input, ctx);
        this.rootException = e;
        setOffendingToken(ctx.start);
    }

    public String getLocation() {
        Token badToken = getOffendingToken();
        if (badToken != null) {
            return String.format("file %s, line %s:%s", getInputStream().getSourceName(), badToken.getLine(), badToken.getCharPositionInLine());
        } else {
            throw this;
        }
    }

    @Override
    public String getMessage() {
        return rootException.getMessage();
    }

    /**
     * @return the rootException
     */
    public Exception getRootException() {
        return rootException;
    }

}
