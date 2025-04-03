import java.util.ArrayList;
import java.util.List;

public class ListExample {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        names.add("Alice");
        names.add("Bob");
        names.add("Alice"); // 允許重複
        System.out.println(names); // 輸出：[Alice, Bob, Alice]
        System.out.println(names.get(0)); // 輸出：Alice (根據索引存取)
    }
}
