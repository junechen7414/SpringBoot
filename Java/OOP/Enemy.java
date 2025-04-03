package Java.OOP;

class Enemy extends Character {
    public Enemy(String name, int health) {
        super(name, health);
    }

    @Override // 標註覆寫父類別方法
    public void attack() { // 多型
        System.out.println(getName() + " 使用火球攻擊！");
    }    
}
