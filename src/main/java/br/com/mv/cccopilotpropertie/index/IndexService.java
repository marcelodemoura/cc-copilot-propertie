package br.com.mv.cccopilotpropertie.index;

import java.io.IOException;

public interface IndexService {

    IndexResult indexPath(String path, String knowledgeBase) throws IOException;


}