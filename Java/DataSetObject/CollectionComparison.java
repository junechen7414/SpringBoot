package Java.DataSetObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CollectionComparison {
    public static void main(String[] args) {
        // List
        List<String> names = new ArrayList<>();
        names.add("Alice");
        names.add("Bob");
        names.add("Alice"); // 允許重複
        System.out.println("List: " + names); // 輸出：[Alice, Bob, Alice]
        System.out.println("List get(0): " + names.get(0)); // 輸出：Alice (根據索引存取)

        // Set
        Set<String> uniqueNames = new HashSet<>();
        uniqueNames.add("Alice");
        uniqueNames.add("Bob");
        uniqueNames.add("Alice"); // 不允許重複，不會被加入
        System.out.println("Set: " + uniqueNames); // 輸出：[Bob, Alice] (順序可能不同)

        // Map
        Map<String, Integer> ages = new HashMap<>();
        ages.put("Alice", 30);
        ages.put("Bob", 25);
        ages.put("Alice", 31); // 後面的值會覆蓋前面的值
        System.out.println("Map: " + ages); // 輸出：{Alice=31, Bob=25} (順序可能不同)
        System.out.println("Map get(\"Alice\"): " + ages.get("Alice")); // 輸出：31 (根據鍵存取值)
    }
}
