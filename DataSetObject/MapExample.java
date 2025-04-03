import java.util.HashMap;
import java.util.Map;

public class MapExample {
    public static void main(String[] args) {
        Map<String, Integer> ages = new HashMap<>();
        ages.put("Alice", 30);
        ages.put("Bob", 25);
        ages.put("Alice", 31); // 後面的值會覆蓋前面的值
        System.out.println(ages); // 輸出：{Alice=31, Bob=25} (順序可能不同)
        System.out.println(ages.get("Alice")); // 輸出：31 (根據鍵存取值)
    }
}
