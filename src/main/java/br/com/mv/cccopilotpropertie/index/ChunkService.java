package br.com.mv.cccopilotpropertie.index;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChunkService {


    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        int size = 1200;
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return chunks;
    }
}