/**
 * @deprecated Use {@link PatchInfernalCape} — single infernal lava patch path.
 */
public final class PatchInfernalTexture {
    public static void main(String[] args) throws Exception {
        PatchInfernalCape.main(args);
    }

    /** @deprecated Use {@link PatchInfernalCape#encodeBundle}. */
    static byte[] encodeBundle(ParseTextureConfigBundle.Info[] infos) {
        return PatchInfernalCape.encodeBundle(infos);
    }
}
