package br.com.mv.cccopilotpropertie.index;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class FileScannerService {

    public List<Path> scan(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walk(root)
                .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".yml"))
                .forEach(files::add);
        return files;
    }
}