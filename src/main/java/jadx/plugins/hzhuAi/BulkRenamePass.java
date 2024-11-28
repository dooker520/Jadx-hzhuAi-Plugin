package jadx.plugins.hzhuAi;

import com.google.gson.JsonObject;
import jadx.api.metadata.annotations.VarNode;
import jadx.api.plugins.pass.JadxPassInfo;
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo;
import jadx.api.plugins.pass.types.JadxDecompilePass;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.instructions.args.SSAVar;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.FieldNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.RootNode;
import jadx.plugins.hzhuAi.data.ClassRenameData;
import jadx.plugins.hzhuAi.data.MethodRenameData;
import jadx.plugins.hzhuAi.data.RenameData;

import java.util.List;
import java.util.Map;

import static jadx.plugins.hzhuAi.hzhuAiPlugin.logger;

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
                .after("FinishTypeInference");
    }

    @Override
    public void init(RootNode root) {
    }

    @Override
    public boolean visit(ClassNode cls) {
        String clsFullName = cls.getName();
        ClassRenameData clsRenameData = renameData.getClsRenames().get(clsFullName);
        if (clsRenameData != null) {
            logger.info("类重命名 从: {} 到: {} 作用描述：{}", clsFullName, clsRenameData.getClsNewName(), clsRenameData.getCodeComment());
            cls.rename(clsRenameData.getClsNewName());
            cls.addCodeComment(clsRenameData.getCodeComment());
            // rename fields
            Map<String, Object> fldNames = clsRenameData.getFldNames();
            for (FieldNode field : cls.getFields()) {
                String[] newName = (String[]) fldNames.get(field.getName());
                if (newName != null) {
                    field.rename(newName[0]);
                    field.addCodeComment(newName[1]);
                    logger.info("类 字段重命名 从: {} 到: {} 作用描述：{}", field.getName(),newName[0], newName[1]);
                }
            }

            // rename methods
            Map<String, MethodRenameData> mthData = clsRenameData.getMthData();
            for (MethodNode method : cls.getMethods()) {
                MethodRenameData mthRenameData = mthData.get(method.getName());
                if (mthRenameData != null) {
                    method.rename(mthRenameData.getNewName());
                    method.addCodeComment(mthRenameData.getCodeComment());
                    logger.info("函数重命名 从: {} 到: {} 作用描述：{}", method.getName(), mthRenameData.getNewName(), mthRenameData.getCodeComment());
                    // rename method parameters

                    // todo An error will be reported here
//                    Map<String, String> paramNames = mthRenameData.getParamNames();
//                    List<VarNode> argNodes = method.collectArgNodes();
//                    for (VarNode varNode : argNodes) {
//                        String oldArgName = varNode.getName();
//                        String newName = paramNames.get(oldArgName);
//                        if(newName!=null) {
//                            logger.info("函数（{}）的参数重命名 从: {} 到: {}", method.getName(),oldArgName, newName);
//                            varNode.setName(newName);
//                        }
//                    }

                    // todo Features not yet implemented
                    // method.getLocals();///?

                }
            }
        }
        return true;
    }

    @Override
    public void visit(MethodNode mth) {
    }
}