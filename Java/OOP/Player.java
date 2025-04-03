package Java.OOP;
// 繼承 & 封裝
class Player extends Character {
    private int power; // 封裝

    public Player(String name, int health, int power) {
        super(name, health);
        this.power = power;
    }

    @Override // 標註覆寫父類別方法
    public void attack() { // 多型
        System.out.println(getName() + " 使用劍攻擊，造成 " + power + " 點傷害！");
    }

    // 封裝 - 提供 getter 方法 (如果需要從外部訪問 power)
    public int getPower() {
        return power;
    }

    // 封裝 - 提供 setter 方法 (如果需要從外部修改 power)
    public void setPower(int power) {
        this.power = power;
    }    
}
