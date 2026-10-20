package edu.canteenx.model;
public final class Student extends User {
    public Student(long id,String name,String email){super(id,name,email);}
    @Override public String roleName(){return "STUDENT";}
    @Override public String dashboardMessage(){return "Browse menus, place orders and track your tokens.";}
}
