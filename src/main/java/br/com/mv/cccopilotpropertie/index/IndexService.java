package br.com.mv.cccopilotpropertie.index;


import br.com.mv.cccopilotpropertie.domain.IndexJob;

import java.io.IOException;

public interface IndexService {

    IndexJob indexPath(String rootPath) throws IOException;
}