package com.Acrobot.ChestShop.Utils;

public class ChestShopStats {

    private final double totalVolume;
    private final double totalAfterTax;
    private final double gcVolume;

    public ChestShopStats(double totalVolume, double totalAfterTax, double gcVolume) {
        this.totalVolume = totalVolume;
        this.totalAfterTax = totalAfterTax;
        this.gcVolume = gcVolume;
    }

    public double getTotalVolume() {
        return totalVolume;
    }

    public double getTotalAfterTax() {
        return totalAfterTax;
    }

    public double getGcVolume() {
        return gcVolume;
    }
}
