package jadx.plugins.hzhuAi.data;

import java.util.List;
import java.util.Map;

public class ClassRenameData {
    private String className;
    private String description;
    private List<Field> fields;
    private List<String> parameters;
    private Map<String, Method> methods;

    // Getters
    public String getClassName() {
        return className;
    }

    public String getDescription() {
        return description;
    }

    public List<Field> getFields() {
        return fields;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public Map<String, Method> getMethods() {
        return methods;
    }

    public static class Field {
        private String name;
        private String description;

        // Getters
        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class Method {
        private String name;
        private String description;
        private List<String> parameters;

        // Getters
        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getParameters() {
            return parameters;
        }
    }
}