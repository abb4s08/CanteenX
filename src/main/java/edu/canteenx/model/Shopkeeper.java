package edu.canteenx.model;
public final class Shopkeeper extends User {
    private final long canteenId;
    public Shopkeeper(long id,String name,String email,long canteenId){super(id,name,email);this.canteenId=canteenId;}
    public long getCanteenId(){return canteenId;}
    @Override public String roleName(){return "SHOPKEEPER";}
    @Override public String dashboardMessage(){return "Verify and redeem tokens for your assigned canteen.";}
}
