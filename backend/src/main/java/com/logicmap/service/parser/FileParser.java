package com.logicmap.service.parser;

import com.logicmap.model.RawFinding;
import java.util.List;

public interface FileParser {

    List<RawFinding> parse(String filePath, String content);

    boolean supports(String filePath);
}
