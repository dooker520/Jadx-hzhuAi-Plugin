package jadx.plugins.hzhuAi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import jadx.api.*;
import jadx.api.data.CommentStyle;
import jadx.api.metadata.ICodeNodeRef;
import jadx.api.metadata.annotations.VarNode;
import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.JadxPluginContext;
import jadx.api.plugins.JadxPluginInfo;
import jadx.api.plugins.JadxPluginInfoBuilder;
import jadx.api.plugins.gui.JadxGuiContext;
import jadx.api.plugins.input.data.IDebugInfo;
import jadx.core.dex.instructions.args.CodeVar;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.instructions.args.SSAVar;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.FieldNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.utils.exceptions.DecodeException;
import javax.swing.*;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Random;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class hzhuAiPlugin<JavaParameter> implements JadxPlugin {
    private static final String PLUGIN_NAME = "hzhuAi Plugin";
    private static final String LLM_ENDPOINT = "http://43.156.23.54:6004/sse2?conversationId=%s";  // 只保留你给出的API
    private static final Logger logger = LoggerFactory.getLogger(hzhuAiPlugin.class);

    private static final String DEFAULT_PROMPT_TEMPLATE = """
        你是一个 Java 代码分析专家，请分析以下代码，并为混淆后的类名、方法名、变量名、参数名提供新的描述性名称，以及加上详细的注释。
        返回的格式为如下标准的 JSON 内容，不需要给出多余的解释：
        {
        "class_renames": {
            "OldClassName": {
            "new_name": "NewClassName",
            "description": "这是类的描述",
            "fields": {
                "oldFieldName": {
                "new_name": "newFieldName",
                "description": "这是字段的描述"
                }
            },
            "methods": {
                "oldMethodName": {
                "new_name": "newMethodName",
                "description": "这是方法的描述",
                "parameters": {
                    "oldParamName": {
                    "new_name": "newParamName",
                    "description": "这是参数的描述"
                    }
                },
                "local_variables": {
                    "oldVarName": {
                    "new_name": "newVarName",
                    "description": "这是局部变量的描述"
                    }
                }
                }
            }
            }
        }
        }
        代码：
        """;
    private JadxGuiContext guiContext;
    private String conversationId;
    private JadxDecompiler decompiler;

    @Override
    public JadxPluginInfo getPluginInfo() {
        return JadxPluginInfoBuilder.pluginId("hzhuAi-plugin")
                .name(PLUGIN_NAME)
                .description("自动Ai去混淆")
                .homepage("https://www.hzhu.com")
                .provides("0.0.1")
                .build();
    }
    @Override
    public void init(JadxPluginContext context) {
        if (context.getGuiContext() != null) {
            this.guiContext = context.getGuiContext();
            this.decompiler =context.getDecompiler();
            initializeGUIComponents();
        }
    }

    private void initializeGUIComponents() {
        // 使用 addPopupMenuAction 方法添加一个动态启用的菜单项
        guiContext.addPopupMenuAction("AI去混淆", (node) -> {
            if (node != null) {
                JavaNode javaNode = decompiler.getJavaNodeByRef(node);
                return javaNode instanceof JavaClass;
            }
            return false;
        }, null, (node) -> {
            try {
                JavaNode javaNode = decompiler.getJavaNodeByRef(node);
                if (javaNode instanceof JavaClass) {
                    analyzeCode((JavaClass) javaNode);
                } else {
                    showError("请将光标置于类内进行分析");
                }
            } catch (Exception ex) {
                handleError("获取代码失败", ex);
            }
        });
    }


    private void analyzeCode(JavaClass javaClass) {
        String code = javaClass.getCode();
        String prompt = DEFAULT_PROMPT_TEMPLATE + code;
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return sendLLMRequest(prompt);
            }
            @Override
            protected void done() {
                try {
                    String response = get();
                    applyRenaming2(response, javaClass);
                    showAnalysisResult("处理完毕!");
                } catch (Exception e) {
                    handleError("分析代码时出错" + e.getMessage(), e);
                }
            }
        };
        worker.execute();
    }


    // 通过JADX获取RootNode的哈希值作为 conversationId
    private String generateSHA256HashFromJadx() throws IOException, NoSuchAlgorithmException {
        char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
        Random RANDOM = new Random();
        // 获取JADX中的RootNode对象
        char[] uuid = new char[36];
        int r;
        // Generate the UUID pattern 'xxxxxxxx-4xxx-yxxx-xxxxxxxxxxxx'
        for (int i = 0; i < 36; i++) {
            if (uuid[i] == 0) {
                r = RANDOM.nextInt(16);
                uuid[i] = HEX_ARRAY[i == 19 ? r & 0x3 | 0x8 : r];
            }
        }
        // Set the version (4) and variant (8, 9, A, or B) bits according to RFC 4122
        uuid[14] = '4';
        uuid[19] |= (char) ((RANDOM.nextInt(4) << 3) | 0x8);
        return new String(uuid);
    }



    private String sendLLMRequest(String prompt) throws IOException, InterruptedException {
        try {
            if (conversationId == null || conversationId.isEmpty()) {
                conversationId = generateSHA256HashFromJadx();
            }
            String endpoint = String.format(LLM_ENDPOINT, conversationId);
            JsonObject requestBody = new JsonObject();
            String prompt2 = prompt.replaceAll("\r?\n", " ").replaceAll("\s+", " ");
            requestBody.add("text", new JsonPrimitive(prompt2));
            requestBody.add("model", new JsonPrimitive("gpt-4o"));
            requestBody.add("streaming", new JsonPrimitive(false));
            JsonArray history = new JsonArray();
            requestBody.add("history", history);
            String jsonBody = new com.google.gson.Gson().toJson(requestBody);
            //logger.info("发送的请求内容: " + jsonBody);
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost request = new HttpPost(endpoint);
            request.setHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(jsonBody,"UTF-8"));

            try (CloseableHttpResponse response = client.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    throw new IOException("请求失败: " + statusCode);
                }
                HttpEntity entity = response.getEntity();
                return EntityUtils.toString(entity, "UTF-8");
            }
        } catch (Exception e) {
            throw new IOException("请求失败: " + e.getMessage(), e);
        }
    }

    public void applyRenaming2(String jsonResponse, JavaClass currentClass) throws DecodeException {
        logger.info("回答的内容: {}", jsonResponse);
        JsonObject json = JsonParser.parseString(jsonResponse).getAsJsonObject();
        JsonObject classRenames = json.getAsJsonObject("class_renames");

        // 获取旧类名
        String oldClassName = currentClass.getName();
        if (classRenames.has(oldClassName)) {
            JsonObject classInfo = classRenames.getAsJsonObject(oldClassName);
            String newClassName = classInfo.get("new_name").getAsString();
            String classDescription = classInfo.get("description").getAsString();
            // 重命名类
            ClassNode classNode = currentClass.getClassNode();
            classNode.rename(newClassName);
            logger.info("类重命名 从: {} 到: {} 作用描述：{}", oldClassName, newClassName, classDescription);
            // 重命名字段
            JsonObject fields = classInfo.getAsJsonObject("fields");
            for (JavaField field : currentClass.getFields()) {
                String oldFieldName = field.getName();
                if (oldFieldName!=null &&  fields.has(oldFieldName)) {
                    JsonObject fieldInfo = fields.getAsJsonObject(oldFieldName);
                    String newFieldName = fieldInfo.get("new_name").getAsString();
                    String fieldDescription = fieldInfo.get("description").getAsString();
                    FieldNode fieldNode = field.getFieldNode();
                    fieldNode.rename(newFieldName);
                    guiContext.applyNodeRename(fieldNode);
                    logger.info("类 变量重命名 从: {} 到: {} 作用描述：{}", oldFieldName, newFieldName, fieldDescription);
                }
            }
            // 重命名方法及其参数和局部变量
            JsonObject methods = classInfo.getAsJsonObject("methods");
            for (JavaMethod method : currentClass.getMethods()) {
                String oldMethodName = method.getName();
                if (oldMethodName!=null && methods.has(oldMethodName)) {

                    JsonObject methodInfo = methods.getAsJsonObject(oldMethodName);
                    String newMethodName = methodInfo.get("new_name").getAsString();
                    String methodDescription = methodInfo.get("description").getAsString();
                    MethodNode methodNode = method.getMethodNode();
                    logger.info("函数重命名 从: {} 到: {} 作用描述：{}", oldMethodName, newMethodName, methodDescription);
                    // 重命名参数
                    JsonObject parameters = methodInfo.getAsJsonObject("parameters");
                    if (parameters != null) {
                        List<VarNode> argNodes = methodNode.collectArgNodes();
                        for (VarNode varNode : argNodes) {
                            String oldArgName = varNode.getName();
                            if (parameters.has(oldArgName)) {
                                JsonObject paramInfo = parameters.getAsJsonObject(oldArgName);
                                String newArgName = paramInfo.get("new_name").getAsString();
                                String ArgNameDescription = methodInfo.get("description").getAsString();
                                varNode.setName(newArgName);
                                guiContext.applyNodeRename(varNode);
                                logger.info("函数（{}）的参数重命名 从: {} 到: {} 作用描述：{}", newMethodName, oldArgName, newArgName, ArgNameDescription);
                            }
                        }
                    }
                    // 局部变量的重命名（处理无法直接修改局部变量名）
                    JsonObject localVariables = methodInfo.getAsJsonObject("local_variables");
                    if (localVariables != null) {
                        for (String oldVarName : localVariables.keySet()) {
                            JsonObject varInfo = localVariables.getAsJsonObject(oldVarName);
                            String newVarName = varInfo.get("new_name").getAsString();
                            String varDescription = varInfo.get("description").getAsString();
                            // 记录日志而不是实际重命名
                            logger.info("函数（{}）的局部变量建议重命名 从: {} 到: {} 作用描述：{}", newMethodName, oldVarName, newVarName, varDescription);
                        }
                    }
                    methodNode.rename(newMethodName);
                    methodNode.addCodeComment(methodDescription, CommentStyle.BLOCK);
                    guiContext.applyNodeRename(methodNode);
                }
            }
            classNode.addCodeComment(classDescription, CommentStyle.BLOCK);
            guiContext.applyNodeRename(classNode);
        }
    }

    private void showError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void handleError(String message, Exception e) {
        showError(message + "\n" + e.getMessage());
    }

    private void showAnalysisResult(String analysis) {
        JOptionPane.showMessageDialog(null, analysis, "结果", JOptionPane.INFORMATION_MESSAGE);
    }

}
