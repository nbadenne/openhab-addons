package org.openhab.binding.freebox.internal.api.model;

import com.google.gson.annotations.SerializedName;

public class FreeboxHomeType {

    @SerializedName("abstract")  
    private Boolean _abstract;
    private Boolean generic;
    private String inherit;
    private String name;

    public Boolean get_abstract() {
        return _abstract;
    }

    public void set_abstract(Boolean _abstract) {
        this._abstract = _abstract;
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