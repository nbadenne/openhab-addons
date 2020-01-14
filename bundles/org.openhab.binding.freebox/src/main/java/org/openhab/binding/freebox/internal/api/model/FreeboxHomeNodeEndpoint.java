package org.openhab.binding.freebox.internal.api.model;

public class FreeboxHomeNodeEndpoint {

    private Integer id;
    private String ep_type;
    private String label;
    private String name;
    private Object value;
    private String value_type;
    private String visibility;
    private Integer refresh;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEp_type() {
        return ep_type;
    }

    public void setEp_type(String ep_type) {
        this.ep_type = ep_type;
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

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getValue_type() {
        return value_type;
    }

    public void setValue_type(String value_type) {
        this.value_type = value_type;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public Integer getRefresh() {
        return refresh;
    }

    public void setRefresh(Integer refresh) {
        this.refresh = refresh;
    }

}