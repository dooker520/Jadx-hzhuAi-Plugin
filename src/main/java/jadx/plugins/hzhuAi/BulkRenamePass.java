package jadx.plugins.hzhuAi;

import jadx.api.data.CommentStyle;
import jadx.api.plugins.pass.JadxPassInfo;
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo;
import jadx.api.plugins.pass.types.JadxDecompilePass;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.FieldNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.RootNode;
import jadx.core.dex.visitors.ssa.SSATransform;
import jadx.core.utils.exceptions.JadxException;
import jadx.plugins.hzhuAi.data.ClassRenameData;
import jadx.plugins.hzhuAi.data.RenameData;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BulkRenamePass implements JadxDecompilePass {

    private final RenameData renameData;

    public BulkRenamePass(RenameData renameData) {
        this.renameData = renameData;
    }

    @Override
    public JadxPassInfo getInfo() {
        return new OrderedJadxPassInfo(
                "BulkRename",
                "Rename everything")
                .after("FinishTypeInference")
                .after("SSATransform");
    }

    @Override
    public void init(RootNode root) {
    }

    @Override
    public boolean visit(ClassNode cls) {
        String clsFullName = cls.getName();
        ClassRenameData clsRenameData = renameData.getClsRenames().get(clsFullName);
        boolean isSetCodeComment = false;

        if (clsRenameData != null) {
            //logger.info("类重命名 从: {} 到: {} 作用描述：{}", clsFullName, clsRenameData.getClassName(), clsRenameData.getDescription());
            cls.rename(clsRenameData.getClassName());
            // rename fields
            List<ClassRenameData.Field> Fields = clsRenameData.getFields();
            int fieldIndex =0;
            for (FieldNode field : cls.getFields()) {
                if (Fields.size()>=fieldIndex+1) {
                    ClassRenameData.Field fieldInfo = Fields.get(fieldIndex);
                    fieldIndex++;
                    field.rename(fieldInfo.getName());
                    field.addCodeComment( fieldInfo.getDescription());
                    isSetCodeComment = true;
                    //logger.info("类 字段重命名 从: {} 到: {} 作用描述：{}", field.getName(),fieldInfo.getName(), fieldInfo.getDescription());
                }
            }
            // rename methods
            Map<String, ClassRenameData.Method> methods =clsRenameData.getMethods();
            for (MethodNode method : cls.getMethods()) {
                String methodFullName = method.getName();

                // 确保 SSA 变量已被优化
                SSATransform ssaTransform = new SSATransform();
                try {
                    ssaTransform.visit(method);
                } catch (JadxException e) {
                    throw new RuntimeException(e);
                }
                //logger.info("函数名 {}", method.getName());
                if (Objects.equals(methodFullName, "<init>")){
                    // rename init ClassMethod  Parameters
                    List<String> InitParameters = clsRenameData.getParameters();
                    if(InitParameters!=null){
                        List<RegisterArg> registerArgs = method.getArgRegs();
                        int InitParameterIndex = 0 ;
                        for (RegisterArg arg : registerArgs) {
                            if (InitParameters.size()>=InitParameterIndex+1){
                                String newParamName =  InitParameters.get(InitParameterIndex);
                                if(newParamName!=null){
                                    //logger.info("初始化类函数 {} 修改参数  {}  ", methodFullName, newParamName);
                                    arg.setName(newParamName);
                                }
                            }
                            InitParameterIndex++;
                        }
                    }
                    method.addCodeComment(clsRenameData.getClassName() + "\n" +clsRenameData.getDescription(),CommentStyle.JAVADOC);
                }else{
                    ClassRenameData.Method methodInfo = methods.get(methodFullName);
                    if (methodInfo != null) {
                        // rename method Parameters
                        List<String> Parameters = methodInfo.getParameters();
                        List<RegisterArg> registerArgs = method.getArgRegs();
                        int ParameterIndex = 0 ;
                        for (RegisterArg arg : registerArgs) {
                            if (Parameters.size()>=ParameterIndex+1){
                                String newParamName =  Parameters.get(ParameterIndex);
                                if(newParamName!=null){
                                    //logger.info("函数 {} 修改参数  {}  ", methodInfo.getName(), newParamName);
                                    arg.setName(newParamName);
                                }
                            }
                            ParameterIndex++;
                        }
                        method.rename(methodInfo.getName());
                        method.addCodeComment(methodInfo.getName()+ "\n" + methodInfo.getDescription(),CommentStyle.JAVADOC);
                }
                        // todo Features not yet implemented
//                    ArrayList<String[]> LocalVars = mthRenameData.getLocalVars();
//                    for (String[] var : LocalVars) {
//                        if (var.length>=2){
//                            logger.info("函数 {} 修改变量  {} 作用描述:{}  ", method.getName(),var[0], var[1]);
//                        }
//                    }
                    }
            }

            if (!isSetCodeComment) {
                cls.addCodeComment(clsRenameData.getClassName()+ "\n" + clsRenameData.getDescription(),CommentStyle.JAVADOC);

            }
        }
        return true;
    }





    @Override
    public void visit(MethodNode mth) {
    }
}