package org.openhab.binding.freebox.internal.api.model;

public class FreeboxHomeType {

    private Boolean astract;
    private Boolean generic;
    private String inherit;
    private String name;

    public Boolean isAstract() {
        return astract;
    }

    public void setAstract(Boolean astract) {
        this.astract = astract;
    }

    public Boolean isGeneric() {
        return generic;
    }

    public void setGeneric(Boolean generic) {
        this.generic = generic;
    }

    public String getInherit() {
        return inherit;
    }

    public void setInherit(String inherit) {
        this.inherit = inherit;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}