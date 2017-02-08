package com.intuit.karate;

import com.jayway.jsonpath.DocumentContext;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

/**
 *
 * @author pthomas3
 */
public class ScriptValue {

    private static final Logger logger = LoggerFactory.getLogger(ScriptValue.class);

    public static final ScriptValue NULL = new ScriptValue(null);

    public static enum Type {
        NULL,
        UNKNOWN,
        PRIMITIVE,
        STRING,
        MAP,
        LIST,
        JSON,
        XML,
        JS_ARRAY,
        JS_OBJECT,
        JS_FUNCTION,
        INPUT_STREAM
    }

    private final Object value;
    private final Type type;

    public Object getValue() {
        return value;
    }
    
    public String getTypeAsShortString() {
        switch(type) {
            case NULL: return "null";
            case UNKNOWN: return "?";
            case PRIMITIVE: return "num";
            case STRING: return "str";
            case MAP: return "map";
            case LIST: return "list";
            case JSON: return "json";
            case XML: return "xml";
            case JS_ARRAY: return "js[]";
            case JS_OBJECT: return "js{}";
            case JS_FUNCTION: return "js()";
            case INPUT_STREAM: return "stream";
            default: return "??";
        }
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public boolean isString() {
        return type == Type.STRING;
    }
    
    public String getAsString() {
        switch (type) {
            case NULL:
                return null;
            case XML:
                Node node = getValue(Node.class);
                if (node.getTextContent() != null) {
                    return node.getTextContent();
                } else {
                    return node.getNodeValue();
                }
            case INPUT_STREAM:
                try {
                    return IOUtils.toString(getValue(InputStream.class), "utf-8");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            default:
                return value.toString();
        }
    }
    
    public String getAsStringForDisplay() {
        switch (type) {
            case NULL:
                return "";
            case XML:
                Node node = getValue(Node.class);
                return XmlUtils.toString(node);
            case JSON:
                DocumentContext doc = getValue(DocumentContext.class);
                return doc.jsonString();
            case INPUT_STREAM:
                try {
                    return IOUtils.toString(getValue(InputStream.class), "utf-8");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            default:
                return value.toString();
        }        
    }

    public Object getAfterConvertingToMapIfNeeded() {
        switch (type) {
            case JSON:
                DocumentContext json = getValue(DocumentContext.class);
                return json.read("$");
            case XML:
                Node xml = getValue(Node.class);
                return XmlUtils.toMap(xml);
            default:
                return getValue();
        }
    }

    public Type getType() {
        return type;
    }

    public <T> T getValue(Class<T> clazz) {
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    public ScriptValue(Object value) {
        this.value = value;
        if (value == null) {
            logger.trace("script value constructed as null");
            type = Type.NULL;
        } else if (value instanceof DocumentContext) {
            type = Type.JSON;
        } else if (value instanceof Node) {
            type = Type.XML;
        } else if (value instanceof List) {
            type = Type.LIST;
        } else if (value instanceof Map) {
            if (value instanceof ScriptObjectMirror) {
                ScriptObjectMirror som = (ScriptObjectMirror) value;
                if (som.isArray()) {
                    type = Type.JS_ARRAY;
                } else if (som.isFunction()) {
                    type = Type.JS_FUNCTION;
                } else {
                    type = Type.JS_OBJECT;
                }
            } else {
                type = Type.MAP;
            }
        } else if (value instanceof String) {
            type = Type.STRING;
        } else if (value instanceof InputStream) {
            type = Type.INPUT_STREAM;
        } else if (ClassUtils.isPrimitiveOrWrapper(value.getClass())) {
            type = Type.PRIMITIVE;
        } else {
            type = Type.UNKNOWN;
            logger.trace("value init unknown type: {} - {}", value.getClass(), value);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[type: ").append(type);
        sb.append(", value: ").append(value);
        sb.append("]");
        return sb.toString();
    }

}
