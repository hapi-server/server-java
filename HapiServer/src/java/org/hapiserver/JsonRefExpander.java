
package org.hapiserver;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * Resolves references within the JSON Object.  Written by ChatGPT, reviewed by jbf.
 * @author jbf
 */
public class JsonRefExpander {

    /**
     * Expand all local $ref objects in the given JSON tree.Returns a new object tree, leaving the original root untouched.
     *
     * @param root the JSONObject
     * @return the node with references resolved
     * @throws org.codehaus.jettison.json.JSONException
     */
    public static Object expandRefs(JSONObject root) throws JSONException, IllegalArgumentException {
        // Deep-copy the input so we don't mutate the caller's original object.
        Object copy = deepCopy(root);
        return expandNode(copy, root, new HashSet<>());
    }

    /**
     * Recursively expand refs in a node.
     */
    private static Object expandNode(Object node, JSONObject root, Set<String> refStack) throws JSONException, IllegalArgumentException {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;

            // If this object is exactly {"$ref": "..."} or at least contains $ref,
            // treat it as a ref object. For schema-processing purposes, this is often
            // what people want. Adjust if you want stricter behavior.
            if (obj.has("$ref")) {
                String ref = obj.getString("$ref");

                if (refStack.contains(ref)) {
                    throw new IllegalStateException("Circular $ref detected: " + refStack + " -> " + ref);
                }

                refStack.add(ref);
                Object target = resolveRef(root, ref);

                // Deep copy before expanding so the resolved object can be reused safely.
                Object replacement = deepCopy(target);
                Object expanded = expandNode(replacement, root, refStack);

                refStack.remove(ref);
                return expanded;
            }

            JSONObject out = new JSONObject();
            Iterator<?> keys = obj.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                Object value = obj.get(key);
                out.put(key, expandNode(value, root, refStack));
            }
            return out;

        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                out.put(expandNode(arr.get(i), root, refStack));
            }
            return out;

        } else {
            // primitives: String, Number, Boolean, JSONObject.NULL
            return node;
        }
    }

    /**
     * Resolve a local JSON Pointer ref like "#/definitions/Foo".
     * @param root
     * @param ref
     * @return
     * @throws org.codehaus.jettison.json.JSONException
     */
    public static Object resolveRef(JSONObject root, String ref) throws IllegalArgumentException, JSONException {
        if (!ref.startsWith("#")) {
            throw new IllegalArgumentException("Only local refs are supported: " + ref);
        }

        if (ref.equals("#")) {
            return root;
        }

        String pointer = ref.substring(1);  // remove leading '#'

        if (!pointer.startsWith("/")) {
            throw new IllegalArgumentException("Invalid JSON Pointer fragment: " + ref);
        }

        Object current = root;
        String[] parts = pointer.substring(1).split("/");

        for (String rawPart : parts) {
            String part = unescapeJsonPointer(rawPart);

            if (current instanceof JSONObject) {
                current = ((JSONObject) current).get(part);
            } else if (current instanceof JSONArray) {
                int idx = Integer.parseInt(part);
                current = ((JSONArray) current).get(idx);
            } else {
                throw new IllegalArgumentException(
                    "Cannot descend through non-container while resolving ref " + ref + ": " + current
                );
            }
        }

        return current;
    }

    /**
     * Deep copy a JSON value.
     */
    private static Object deepCopy(Object value) throws IllegalArgumentException, JSONException {
        if (value instanceof JSONObject) {
            JSONObject src = (JSONObject) value;
            JSONObject dst = new JSONObject();
            Iterator<?> keys = src.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                dst.put(key, deepCopy(src.get(key)));
            }
            return dst;

        } else if (value instanceof JSONArray) {
            JSONArray src = (JSONArray) value;
            JSONArray dst = new JSONArray();
            for (int i = 0; i < src.length(); i++) {
                dst.put(deepCopy(src.get(i)));
            }
            return dst;

        } else {
            // primitives and JSONObject.NULL
            return value;
        }
    }

    /**
     * JSON Pointer unescaping:
     *   ~1 => /
     *   ~0 => ~
     */
    private static String unescapeJsonPointer(String s) {
        return s.replace("~1", "/").replace("~0", "~");
    }
}