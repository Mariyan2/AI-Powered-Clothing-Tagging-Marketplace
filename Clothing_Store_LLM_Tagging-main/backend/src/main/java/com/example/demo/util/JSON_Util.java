package com.example.demo.util;



public final class JSON_Util {
    private JSON_Util() {}

    public static String escape(String s) {

        if (s == null) return "\"\"";
        String e = s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");

        return "\"" + e + "\"";
    }
}
