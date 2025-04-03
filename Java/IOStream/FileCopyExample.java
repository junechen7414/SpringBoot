package Java.IOStream;
import java.io.*;

public class FileCopyExample {
    public static void main(String[] args) {
        // 指定來源檔案與目標檔案路徑
        String inputFile = "input.txt";
        String outputFile = "output.txt";

        // 使用 try-with-resources 自動關閉 InputStream 與 OutputStream
        try (InputStream in = new FileInputStream(inputFile);
             OutputStream out = new FileOutputStream(outputFile)) {
             
            byte[] buffer = new byte[1024]; // 定義緩衝區大小
            int bytesRead;
            
            // 逐步讀取檔案內容並寫入至目標檔案
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            
            System.out.println("檔案複製完成！");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
