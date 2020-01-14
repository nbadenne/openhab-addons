package org.openhab.binding.freebox.internal.api.model;

import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class FreeboxHomeNodeType {

    @SerializedName("abstract")  
    private Boolean _abstract;
    private List<FreeboxHomeNodeEndpoint> endpoints;
    private Boolean generic;
    private String icon;
    private String inherit;
    private String label;
    private String name;
    private Map<String, String> params;
    private Boolean physical;

    public Boolean get_abstract() {
        return _abstract;
    }

    public void set_abstract(Boolean _abstract) {
        this._abstract = _abstract;
    }

    public List<FreeboxHomeNodeEndpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<FreeboxHomeNodeEndpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public Boolean getGeneric() {
        return generic;
    }

    public void setGeneric(Boolean generic) {
        this.generic = generic;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getInherit() {
        return inherit;
    }

    public void setInherit(String inherit) {
        this.inherit = inherit;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public Boolean getPhysical() {
        return physical;
    }

    public void setPhysical(Boolean physical) {
        this.physical = physical;
    }
}