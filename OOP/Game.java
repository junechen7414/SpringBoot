// 測試遊戲
public class Game {
    public static void main(String[] args) {
        // 創建角色物件時，使用具體的子類別 (Player, Enemy)
        Character player = new Player("關二爺", 100, 20);
        Character enemy = new Enemy("哥布林", 50);

        // 呼叫 attack 方法，會執行各自類別中的實作 (多型)
        player.attack(); // 輸出: 勇者 使用劍攻擊，造成 20 點傷害！
        enemy.attack();  // 輸出: 哥布林 使用火球攻擊！

        // 封裝：透過 getter 取得 Player 的 power
        // 需要先將 player 轉型回 Player 才能呼叫 Player 特有的方法
        if (player instanceof Player) {
            Player playerObj = (Player) player;
            System.out.println(playerObj.getName() + "的力量值：" + playerObj.getPower());

            // 示範封裝：透過 setter 修改 Player 的 power
            playerObj.setPower(25);
            System.out.println(playerObj.getName() + "強化了力量！");
            playerObj.attack(); // 輸出: 勇者 使用劍攻擊，造成 25 點傷害！
            System.out.println(playerObj.getName() + "目前的力量值：" + playerObj.getPower()); // 輸出: 勇者目前的力量值：25 (因為無效值被拒絕)
        }
    }
}
