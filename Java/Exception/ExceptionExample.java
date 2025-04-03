package Java.Exception;

public class ExceptionExample {
    public static void main(String[] args) {
        try {
            int result = 10 / 0; // 可能發生 ArithmeticException
        } catch (ArithmeticException e) {
            System.out.println("錯誤: 除數不能為 0");
        }
        System.out.println("程式繼續執行...");
    }
}
