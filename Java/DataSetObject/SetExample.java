package Java.DataSetObject;
import java.util.HashSet;
import java.util.Set;

public class SetExample {
    public static void main(String[] args) {
        Set<String> uniqueNames = new HashSet<>();
        uniqueNames.add("Alice");
        uniqueNames.add("Bob");
        uniqueNames.add("Alice"); // 不允許重複，不會被加入
        System.out.println(uniqueNames); // 輸出：[Bob, Alice] (順序可能不同)
    }
}
