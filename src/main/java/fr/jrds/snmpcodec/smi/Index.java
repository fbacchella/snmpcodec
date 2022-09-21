package fr.jrds.snmpcodec.smi;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.snmp4j.smi.OID;
import org.snmp4j.smi.Variable;

import fr.jrds.snmpcodec.MibStore;
import fr.jrds.snmpcodec.OidTreeNode;
import fr.jrds.snmpcodec.Utils;
import fr.jrds.snmpcodec.log.LogAdapter;

public class Index {

    static class Parsed {
        public int[] content = null;
        public int[] next = null;
        @Override
        public String toString() {
            return (content != null ? Utils.dottedNotation(content) : "_") + "/" + (next != null ? Utils.dottedNotation(next) : "_");
        }
    }

    private static final LogAdapter logger = LogAdapter.getLogger(Index.class);

    private final List<OidTreeNode> indexes;

    public Index(List<OidTreeNode> indexes) {
        this.indexes = indexes;
    }

    @Override
    public String toString() {
        return indexes.toString();
    }

    public Map<String, Object> resolve(int[] oid, MibStore store) {
        Map<String, Object> indexesValues = new LinkedHashMap<>(indexes.size());
        int[] oidParsed = Arrays.copyOf(oid, oid.length);
        for (OidTreeNode i: indexes) {
            ObjectType column = store.objects.get(i);
            if(column == null) {
                logger.error("index not found: %s", i);
                break;
            }
            Syntax codec = column.getSyntax();
            logger.debug("given %s, found %s %s", i, codec, column);
            Parsed parsed;
            if(codec.getConstrains() != null) {
                parsed = codec.getConstrains().extract(oidParsed);
            } else {
                parsed = new Parsed();
                parsed.content = Arrays.copyOf(oidParsed, 1);
                if(oidParsed.length > 1) {
                    parsed.next = Arrays.copyOfRange(oidParsed, 1, oidParsed.length);
                }
            }
            if(parsed == null) {
                break;
            }
            logger.debug("parsed %s from %s with %s/%s", parsed, oidParsed, i, codec);
            Variable v =  codec.getVariable();
            if (v == null) {
                // The definition was incomplete, null variable returned, stop now
                break;
            }
            OID subIndex = new OID(parsed.content);
            v.fromSubIndex(subIndex, true);
            Object o = codec.convert(v);
            if (codec.isNamed()) {
                o = codec.getNameFromNumer(v.toInt());
            }
            indexesValues.put(i.getSymbol(), o);
            oidParsed = parsed.next;
            if (oidParsed == null) {
                break;
            }
        }
        if (oidParsed != null) {
            String traillings = Arrays.stream(oidParsed).mapToObj(Integer::toString).collect(Collectors.joining("."));
            throw new IllegalArgumentException("Trailing elements in index: " + traillings);
        }
        logger.debug("will resolve %s to %s", oid, indexesValues);
        return indexesValues;
    }

}
