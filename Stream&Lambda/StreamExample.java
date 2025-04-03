import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StreamExample {
    public static void main(String[] args) {
        List<Integer> numbers = Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6, 5, 3);
        
        // 1. 篩選出偶數 (filter)
        List<Integer> evens = numbers.stream()
                .filter(n -> n % 2 == 0)
                .collect(Collectors.toList());
        
        // 2. 將數字平方 (map)
        List<Integer> squares = numbers.stream()
                .map(n -> n * n)
                .collect(Collectors.toList());
        
        // 3. 計算總和 (reduce)
        int sum = numbers.stream()
                .reduce(0, Integer::sum);
        
        // 4. 排序 (sorted)
        List<Integer> sortedNumbers = numbers.stream()
                .sorted()
                .collect(Collectors.toList());

        // 輸出結果
        System.out.println("偶數: " + evens);
        System.out.println("平方: " + squares);
        System.out.println("總和: " + sum);
        System.out.println("排序後: " + sortedNumbers);
        System.out.println("原始集合:" + numbers);
    }
}
