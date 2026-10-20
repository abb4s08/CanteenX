package edu.canteenx.model;
public final class Admin extends User {
    public Admin(long id,String name,String email){super(id,name,email);}
    @Override public String roleName(){return "ADMIN";}
    @Override public String dashboardMessage(){return "Manage menus and review campus sales.";}
}
