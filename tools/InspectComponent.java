import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import rt4.Buffer;
import rt4.Component;

public class InspectComponent {
    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            Component c = new Component();
            byte[] bytes = Files.readAllBytes(Path.of(arg));
            try {
                Buffer buffer = new Buffer(bytes);
                if ((bytes[0] & 0xFF) == 0xFF) {
                    c.decodeIf3(buffer);
                } else {
                    c.decodeIf1(buffer);
                }
            } catch (Throwable t) {
                System.out.println(arg + " decode warning: " + t);
            }
            System.out.println(arg);
            System.out.println(" text=" + c.text);
            System.out.println(" type=" + c.type + " buttonType=" + c.buttonType + " clientCode=" + c.clientCode);
            System.out.println(" color=" + c.color + " activeColor=" + c.activeColor + " overColor=" + c.overColor);
            System.out.println(" compsOpcodes=" + Arrays.toString(c.cs1ComparisonOpcodes));
            System.out.println(" compsOperands=" + Arrays.toString(c.cs1ComparisonOperands));
            if (c.cs1Scripts != null) {
                for (int i = 0; i < c.cs1Scripts.length; i++) {
                    if (c.cs1Scripts[i] != null) {
                        System.out.println(" script[" + i + "]=" + Arrays.toString(c.cs1Scripts[i]));
                    }
                }
            } else {
                System.out.println(" scripts=null");
            }
            System.out.println();
        }
    }
}
