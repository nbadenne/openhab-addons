package org.openhab.binding.freebox.internal.api.model;

import java.util.Map;

import org.openhab.binding.freebox.internal.api.model.FreeboxHomeType;

public class FreeboxHomeAdapter {

    private Integer id;
    private String label;
    private String status;
    private Map<String, String> props;
    private FreeboxHomeType type;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    
    public Map<String, String> getProps() {
        return props;
    }

    public void setProps(Map<String, String> props) {
        this.props = props;
    }

    public FreeboxHomeType getType() {
        return type;
    }

    public void setType(FreeboxHomeType type) {
        this.type = type;
    }

}