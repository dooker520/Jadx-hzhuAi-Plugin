package jadx.plugins.hzhuAi;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaMethod;
import jadx.api.JavaNode;
import jadx.api.metadata.ICodeNodeRef;
import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.JadxPluginContext;
import jadx.api.plugins.JadxPluginInfo;
import jadx.api.plugins.JadxPluginInfoBuilder;
import jadx.api.plugins.gui.JadxGuiContext;
import jadx.plugins.hzhuAi.data.ClassRenameData;
import jadx.plugins.hzhuAi.data.RenameData;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Random;

public class hzhuAiPlugin implements JadxPlugin {
    private static final String PLUGIN_NAME = "hzhuAi Plugin";
    private static final String LLM_ENDPOINT = "http://43.156.23.54:6004/sse2?conversationId=%s";  // 只保留你给出的API
    public static final Logger logger = LoggerFactory.getLogger(hzhuAiPlugin.class);
    private final RenameData renameData = new RenameData();
    private static final String DEFAULT_PROMPT_TEMPLATE = """
        你是一个 Java 代码分析专家，请分析以下代码，并为混淆后的类名、类初始化的传参名、方法名、参数名提供新的描述性名称，以及加上详细的中文注释。
        返回的格式为如下标准的 JSON 内容 要注意重复的函数名只保留参数最多的那个，不需要给出多余的解释：
        {
            "className": "NewClassName",
            "description": "类的描述 \\n 新参数名1：描述 \\n 新参数名2：描述",
            "fields": [
                {
                    "name": "newName",
                    "description": "字段的描述"
                }
            ],
            "parameters": [
                "新参数名"
            ],
            "methods": {
                "oldName": {
                    "name": "newName",
                    "description": "方法的描述 \\n 新参数名1：描述 \\n 新参数名2：描述 ",
                    "parameters": [
                        "newParamName"
                    ]
                }
            }
        }
        代码：
        """;

    private JadxGuiContext guiContext;
    private JadxPluginContext Context;
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
            context.addPass(new BulkRenamePass(renameData));
            this.guiContext = context.getGuiContext();
            this.decompiler =context.getDecompiler();
            this.Context = context;
            initializeGUIComponents();
        }
    }

    private void reloadCode(JadxPluginContext context, JadxGuiContext guiContext) {
        JadxDecompiler decompiler = context.getDecompiler();
        renameData.getClsRenames().keySet().stream()
                .map(decompiler::searchJavaClassByOrigFullName)
                .filter(Objects::nonNull) // better to throw an error in class not found
                .forEach(JavaClass::unload);
        guiContext.reloadAllTabs();
    }

    private void initializeGUIComponents() {
        // 添加 "AI去混淆" 菜单项，绑定快捷键 F8
        guiContext.addPopupMenuAction("AI去混淆",
                (node) -> node != null && decompiler.getJavaNodeByRef(node) instanceof JavaClass,
                "F8", // 这里是快捷键绑定
                (node) -> {
                    try {
                        JavaNode javaNode = decompiler.getJavaNodeByRef(node);
                        if (javaNode instanceof JavaClass) {
                            logger.info("正在处理中.....");
                            analyzeCode((JavaClass) javaNode);
                        } else {
                            showError("请将光标置于类内进行分析");
                        }
                    } catch (Exception ex) {
                        handleError("获取代码失败", ex);
                    }
                });

        // 添加 "AI to Android" 菜单项，绑定快捷键 F9
        guiContext.addPopupMenuAction("AI to Android",
                (node) -> node != null && decompiler.getJavaNodeByRef(node) instanceof JavaMethod,
                "F9", // 这里是快捷键绑定
                this::accept);
        // 添加 "AI to Android" 菜单项，绑定快捷键 F10
        guiContext.addPopupMenuAction("Remove AI to Android",
                (node) -> {
                    JavaNode javaNode = decompiler.getJavaNodeByRef(node);
                    if (node != null){
                        if (javaNode instanceof JavaMethod methodClass) {
                            return loadConversionResultExit(methodClass.getTopParentClass().getPackage(),methodClass.getTopParentClass().getFullName() + "." + methodClass.getName());
                        }
                    }
                    return false;
                },
                "F10", // 这里是快捷键绑定
                this::Remove);
    }



    private String convertSmaliToJava(String smaliCode) throws IOException, InterruptedException {
        String prompt = "请将以下Smali函数代码转换为Android代码，给出详细的中文注释，除了Android代码其他都不需要：\n" + smaliCode;
        return sendLLMRequest(prompt);
    }

    private static String optimizeSmali(JavaMethod javaMethod) {
        // 提取方法的 smali 代码
        String smaliCode = extractSmali(javaMethod);
        if (smaliCode.isEmpty()) {
            return "";
        }
        // 优化：移除 .line 指令
        smaliCode = smaliCode.replaceAll("\\.line \\d+", "");
        String cleaned = smaliCode.replace("\t", " ");
        // Remove leading spaces (indentation) from each line
        cleaned = cleaned.replaceAll("(?m)^\\s+", "");
        // Replace multiple newlines with a single newline
        cleaned = cleaned.replaceAll("\\n+", "\n");
        // Trim leading and trailing whitespace from the entire text
        cleaned = cleaned.trim();
        // 可以根据需要在此处添加更多优化

        return cleaned;
    }

    private static String extractSmali(JavaMethod javaMethod) {
        JavaClass javaClass = javaMethod.getDeclaringClass();
        String smaliCode = javaClass.getSmali();
        String methodDescriptor = javaMethod.getMethodNode().getMethodInfo().getShortId();

        // 构建方法的完整签名
        String methodSignature = ".method " + javaMethod.getAccessFlags().makeString(true)  + methodDescriptor;

        // 查找方法在smali代码中的起始位置
        int methodStartIndex = smaliCode.indexOf(methodSignature);
        if (methodStartIndex == -1) {
            logger.error("在 smali 中未找到方法签名: {}", methodSignature);
            return "";
        }

        // 查找方法在smali代码中的结束位置
        int methodEndIndex = smaliCode.indexOf(".end method", methodStartIndex);
        if (methodEndIndex == -1) {
            logger.error("未找到方法的结束标记: {}", methodSignature);
            return "";
        }
        // 提取方法的smali代码
        return smaliCode.substring(methodStartIndex, methodEndIndex + ".end method".length());
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
                    applyRenaming(response,javaClass);
                    reloadCode(Context, guiContext);
                    //showAnalysisResult("处理完毕!");
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


    private String sendLLMRequestV2(String prompt) throws IOException {
        try {
            // 创建请求体
            JsonObject requestBody = new JsonObject();
            JsonArray messages = new JsonArray();

            // 添加消息内容
            JsonObject systemMessage = new JsonObject();
            systemMessage.add("role", new JsonPrimitive("system"));
            systemMessage.add("content", new JsonPrimitive("你是一个代码分析专家"));
            messages.add(systemMessage);

            JsonObject userMessage = new JsonObject();
            userMessage.add("role", new JsonPrimitive("user"));
            userMessage.add("content", new JsonPrimitive(prompt));
            messages.add(userMessage);

            requestBody.add("messages", messages);
            requestBody.add("model", new JsonPrimitive("deepseek-chat"));
            requestBody.add("stream", new JsonPrimitive(false));

            String jsonBody = new com.google.gson.Gson().toJson(requestBody);

            // 创建HTTP客户端
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpPost request = new HttpPost("https://api.deepseek.com/chat/completions");

                request.setHeader("Content-Type", "application/json");
                request.setHeader("Authorization", "Bearer sk-a79164e6df1d456d8e736ca56cbd430c"); // 替换为你的API密钥
                request.setEntity(new StringEntity(jsonBody, "UTF-8"));

                // 执行请求并返回响应
                try (CloseableHttpResponse response = client.execute(request)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (statusCode != 200) {
                        logger.info("请求失败1: {}", statusCode);
                        throw new IOException("请求失败: " + statusCode);
                    }
                    HttpEntity entity = response.getEntity();
                    return EntityUtils.toString(entity, "UTF-8");
                }catch (Exception e) {
                    logger.info("请求失败2: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                logger.info("请求失败3: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            logger.info("请求失败4: {}", e.getMessage(), e);
            throw new IOException("请求失败: " + e.getMessage(), e);
        }
    }


    public void applyRenaming(String jsonResponse, JavaClass currentClass){
        logger.info(jsonResponse);
        Gson gson = new Gson();
        ClassRenameData classDescription = gson.fromJson(jsonResponse, ClassRenameData.class);
        renameData.getClsRenames().put(currentClass.getName(), classDescription);

    }
    private void showError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void handleError(String message, Exception e) {
        showError(message + "\n" + e.getMessage());
    }

    private void showAnalysisResult(String analysis) {
        JTextArea textArea = new JTextArea(analysis);
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(800, 600));

        JDialog dialog = new JDialog((Frame) null, "Ai Android", false); // 设置为非模态对话框
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(scrollPane);
        dialog.pack();
        dialog.setLocationRelativeTo(null); // 窗口居中
        dialog.setResizable(true); // 允许改变大小
        dialog.setVisible(true);
    }



    private void saveConversionResult(String Package,String result, String className) throws IOException {
        // 将完全限定名转换为目录路径
        String directoryPath = className.replace('.', File.separatorChar);
        Path filePath = Paths.get(Package, directoryPath + ".java");
        logger.info("Saving result to: {}", filePath);

        // 创建父目录
        Files.createDirectories( filePath.getParent());
        Files.writeString(filePath, result);
    }

    private String loadConversionResult(String Package,String className) throws IOException {
        String directoryPath = className.replace('.', File.separatorChar);
        Path filePath = Paths.get(Package,   directoryPath + ".java");

        if (Files.exists(filePath)) {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        }
        return null;
    }

    private boolean loadConversionResultExit(String Package,String className) {
        String directoryPath = className.replace('.', File.separatorChar);
        Path filePath = Paths.get(Package,   directoryPath + ".java");
        return Files.exists(filePath);
    }

    private boolean loadConversionResultRemove(String Package,String className) {
        String directoryPath = className.replace('.', File.separatorChar);
        Path filePath = Paths.get(Package,   directoryPath + ".java");
        try {
            Files.delete(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }


    private void accept(ICodeNodeRef node) {
        try {
            JavaNode javaNode = decompiler.getJavaNodeByRef(node);
            if (javaNode instanceof JavaMethod methodClass) {
                String code = loadConversionResult(methodClass.getTopParentClass().getPackage(), methodClass.getTopParentClass().getFullName() + "." + methodClass.getName());
                if (code == null) {
                    logger.info("正在处理中.....");
                    String smaliCode = optimizeSmali(methodClass);
                    code = convertSmaliToJava(smaliCode);
                    saveConversionResult(methodClass.getTopParentClass().getPackage(), code, methodClass.getTopParentClass().getFullName() + "." + methodClass.getName());
                }
                if (code != null) {
                    showAnalysisResult(code);
                } else {
                    logger.info("获取代码失败");
                }
            } else {
                showError("请将光标置于函数内进行分析");
            }
        } catch (Exception ex) {
            handleError("获取代码失败", ex);
        }
    }

    private void Remove(ICodeNodeRef node) {
        try {
            JavaNode javaNode = decompiler.getJavaNodeByRef(node);
            if (javaNode instanceof JavaMethod methodClass) {
                loadConversionResultRemove(methodClass.getTopParentClass().getPackage(), methodClass.getTopParentClass().getFullName() + "." + methodClass.getName());

            } else {
                showError("请将光标置于函数内操作");
            }
        } catch (Exception ex) {
            handleError("操作失败", ex);
        }
    }
}
