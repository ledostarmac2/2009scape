import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

public class InspectQuests {
    public static void main(String[] args) throws Exception {
        File jar = new File(args.length > 0 ? args[0] : "game/server.jar");
        URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, InspectQuests.class.getClassLoader());
        Class<?> questBase = Class.forName("core.game.node.entity.player.link.quest.Quest", false, loader);
        Method getQuest = questBase.getMethod("getQuest");
        Method getIndex = questBase.getMethod("getIndex");
        Method getButtonId = questBase.getMethod("getButtonId");
        Method getQuestPoints = questBase.getMethod("getQuestPoints");
        Method getConfigs = questBase.getMethod("getConfigs");

        try (JarFile jf = new JarFile(jar)) {
            jf.stream()
                .filter(e -> e.getName().endsWith(".class"))
                .map(e -> e.getName().replace('/', '.').replaceAll("\\.class$", ""))
                .sorted()
                .forEach(name -> {
                    try {
                        Class<?> c = Class.forName(name, false, loader);
                        if (c == questBase || !questBase.isAssignableFrom(c)) {
                            return;
                        }
                        Constructor<?> ctor = c.getDeclaredConstructor();
                        ctor.setAccessible(true);
                        Object q = ctor.newInstance();
                        int[] configs = (int[]) getConfigs.invoke(q);
                        StringBuilder cfg = new StringBuilder();
                        for (int i = 0; i < configs.length; i++) {
                            if (i > 0) cfg.append(",");
                            cfg.append(configs[i]);
                        }
                        System.out.printf("%s\t%s\t%s\t%s\t%s\t%s%n",
                            getQuest.invoke(q),
                            getIndex.invoke(q),
                            getButtonId.invoke(q),
                            getQuestPoints.invoke(q),
                            cfg,
                            name);
                    } catch (NoSuchMethodException ignored) {
                    } catch (Throwable t) {
                        System.out.printf("ERR\t%s\t%s%n", name, t.getClass().getSimpleName() + ":" + t.getMessage());
                    }
                });
        }
    }
}
