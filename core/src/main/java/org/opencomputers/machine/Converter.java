package org.opencomputers.machine;

import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bidirectional LuaValue ↔ Java-Object bridge. Java side uses: String, Double,
 * Boolean, byte[] and Map&lt;Integer,Object&gt; (array-like tables). Binary
 * data crosses as ISO-8859-1-decoded LuaStrings so bytes are lossless.
 */
public final class Converter {
    private Converter() {
    }

    public static Object[] toObjects(Varargs args) {
        int n = args.narg();
        Object[] out = new Object[n];
        for (int i = 0; i < n; i++) {
            out[i] = toObject(args.arg(i + 1));
        }
        return out;
    }

    public static Object toObject(LuaValue v) {
        if (v == null || v.isnil()) {
            return null;
        }
        if (v.isboolean()) {
            return v.toboolean();
        }
        if (v.type() == LuaValue.TNUMBER) {
            return v.todouble();
        }
        if (v.type() == LuaValue.TSTRING) {
            // NB: must test the concrete type — LuaJ's isnumber() is also true
            // for numeric *strings* like "1", which would corrupt e.g. file
            // handles and hex addresses passed back into components.
            return v.tojstring();
        }
        if (v.istable()) {
            LuaTable t = (LuaTable) v;
            // Table → list-style map preserving Lua's 1-based indexes.
            Map<Integer, Object> map = new LinkedHashMap<>();
            LuaValue k = LuaValue.NIL;
            while (true) {
                Varargs pair = t.next(k);
                k = pair.arg1();
                if (k.isnil()) {
                    break;
                }
                LuaValue val = pair.arg(2);
                if (k.isnumber() && k.todouble() == Math.rint(k.todouble())) {
                    map.put(k.toint(), toObject(val));
                } else {
                    // Non-integer keys are preserved under their string form.
                    map.put(-absHash(k.tojstring()), k.tojstring());
                    map.put(-absHash(k.tojstring()) - 1, toObject(val));
                }
            }
            return map;
        }
        return v.tojstring();
    }

    private static int absHash(String s) {
        return Math.abs(s.hashCode()) + 2;
    }

    public static Varargs toVarargs(Object[] values) {
        if (values == null || values.length == 0) {
            return LuaValue.NONE;
        }
        LuaValue[] lv = new LuaValue[values.length];
        for (int i = 0; i < values.length; i++) {
            lv[i] = toLua(values[i]);
        }
        return LuaValue.varargsOf(lv);
    }

    @SuppressWarnings("unchecked")
    public static LuaValue toLua(Object o) {
        if (o == null) {
            return LuaValue.NIL;
        }
        if (o instanceof Boolean b) {
            return LuaValue.valueOf(b);
        }
        if (o instanceof Number n) {
            return LuaValue.valueOf(n.doubleValue());
        }
        if (o instanceof byte[] b) {
            return LuaValue.valueOf(new String(b, StandardCharsets.ISO_8859_1));
        }
        if (o instanceof Map<?, ?> map) {
            LuaTable t = new LuaTable();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object key = e.getKey();
                t.set(toLua(key instanceof Number ? ((Number) key).doubleValue() : key),
                        toLua(e.getValue()));
            }
            return t;
        }
        return LuaValue.valueOf(o.toString());
    }
}
