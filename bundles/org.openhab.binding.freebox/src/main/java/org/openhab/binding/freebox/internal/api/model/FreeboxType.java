package org.openhab.binding.freebox.internal.api.model;

public enum FreeboxType {
    FREEBOX_REVOLUTION_R1("fbxgw-r1/full"), 
    FREEBOX_REVOLUTION_R2("fbxgw-r2/full"), 
    FREEBOX_REVOLUTION_R3("fbxgw-r3/full"), 
    FREEBOX_MINI_4K_R1("fbxgw-r2/mini"), 
    FREEBOX_MINI_4K_R2("fbxgw-r1/mini"), 
    FREEBOX_ONE_R1("fbxgw-r1/one"), 
    FREEBOX_ONE_R2("fbxgw-r2/one"), 
    FREEBOX_DELTA_R1("fbxgw7-r1/full");
 
    private String name;
 
    FreeboxType(String name) {
        this.name = name;
    }
 
    public String getName() {
        return name;
    }
}