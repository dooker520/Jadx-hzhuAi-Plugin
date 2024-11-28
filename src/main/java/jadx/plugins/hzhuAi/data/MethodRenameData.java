package jadx.plugins.hzhuAi.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodRenameData {
    private final String newName;
    private final String CodeComment;
    private final Map<String,String> paramNames = new HashMap<>();

    public MethodRenameData(String newName, String codeComment) {
        this.newName = newName;
        CodeComment = codeComment;
    }

    public String getCodeComment() {
        return CodeComment;
    }

    public String getNewName() {
        return newName;
    }

    public Map<String,String> getParamNames() {
        return paramNames;
    }
}