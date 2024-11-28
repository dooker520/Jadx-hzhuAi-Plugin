package jadx.plugins.hzhuAi.data;

import java.util.HashMap;
import java.util.Map;

public class ClassRenameData {
    private final String clsNewName;
    private final String CodeComment;
    private Map<String, Object> fldNames = new HashMap<>();
    private Map<String, MethodRenameData> mthData = new HashMap<>();


    public ClassRenameData(String clsNewName,String codeComment) {
        this.clsNewName = clsNewName;
        this.CodeComment = codeComment;
    }

    public String getClsNewName() {
        return clsNewName;
    }

    public String getCodeComment() {
        return CodeComment;
    }


    public Map<String, Object> getFldNames() {
        return fldNames;
    }

    public Map<String, MethodRenameData> getMthData() {
        return mthData;
    }
}