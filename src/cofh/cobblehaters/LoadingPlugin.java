package cofh.cobblehaters;

import static org.objectweb.asm.Opcodes.*;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(9001)
public class LoadingPlugin implements IFMLLoadingPlugin, IClassTransformer {

	public static boolean runtimeDeobfEnabled = false;

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {

		if ("net.minecraft.entity.item.EntityItem".equals(transformedName)) {
			ClassNode cn = new ClassNode();
			new ClassReader(bytes).accept(cn, 0);
			String method = "func_70097_a", setDead = "func_70106_y";
			if (!runtimeDeobfEnabled) {
				method = "attackEntityFrom";
				setDead = "setDead";
			}

			MethodNode onAttack = null;
			for (MethodNode m : cn.methods) {
				if (method.equals(m.name)) {
					onAttack = m;
					break;
				}
			}

			for (AbstractInsnNode n = onAttack.instructions.getFirst(); n != null; n = n.getNext()) {
				if (n.getOpcode() == INVOKEVIRTUAL && setDead.equals(((MethodInsnNode) n).name)) {
					onAttack.instructions.insert(n, n = new VarInsnNode(ALOAD, 0));
					onAttack.instructions.insert(n, n = new VarInsnNode(ALOAD, 1));
					onAttack.instructions.insert(n, n = new MethodInsnNode(INVOKESTATIC, "cofh/cobblehaters/CobbleHaters",
						"itemDeath", "(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/DamageSource;)V", false));
				}
			}

			ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			cn.accept(cw);
			bytes = cw.toByteArray();
		}
		return bytes;
	}

	@Override
	public String[] getASMTransformerClass() {

		return new String[] { getClass().getName() };
	}

	@Override
	public String getModContainerClass() {

		return null;
	}

	@Override
	public String getSetupClass() {

		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {

		runtimeDeobfEnabled = (Boolean) data.get("runtimeDeobfuscationEnabled");
	}

	@Override
	public String getAccessTransformerClass() {

		return null;
	}

}
