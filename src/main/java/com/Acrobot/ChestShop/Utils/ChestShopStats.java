package com.Acrobot.ChestShop.Utils;

public class ChestShopStats {

    private final double totalVolume;
    private final double totalAfterTax;

    public ChestShopStats(double totalVolume, double totalAfterTax) {
        this.totalVolume = totalVolume;
        this.totalAfterTax = totalAfterTax;
    }

    public double getTotalVolume() {
        return totalVolume;
    }

    public double getTotalAfterTax() {
        return totalAfterTax;
    }
}
