package br.com.mv.cccopilotpropertie.util;

import java.util.ArrayList;
import java.util.List;

public class TextSplitter {


    public static List<String> split(String text, int max) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += max) {
            parts.add(text.substring(i, Math.min(i + max, text.length())));
        }
        return parts;
    }
}
