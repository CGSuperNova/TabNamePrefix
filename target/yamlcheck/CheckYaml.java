import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;

public class CheckYaml {
    public static void main(String[] a) throws Exception {
        for (String f : a) {
            try {
                Object o = new Yaml().load(new String(Files.readAllBytes(Paths.get(f)), "UTF-8"));
                System.out.println("OK   " + f + "  top=" + o);
            } catch (Exception e) {
                System.out.println("FAIL " + f + "  " + e);
            }
        }
    }
}