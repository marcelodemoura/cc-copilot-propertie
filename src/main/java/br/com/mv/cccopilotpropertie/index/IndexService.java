package br.com.mv.cccopilotpropertie.index;


import java.io.IOException;

public interface IndexService {

    IndexJob indexPath(String rootPath) throws IOException;
}