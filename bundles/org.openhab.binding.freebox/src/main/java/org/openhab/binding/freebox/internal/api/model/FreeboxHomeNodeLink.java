package org.openhab.binding.freebox.internal.api.model;

import java.util.List;
import java.util.Map;

public class FreeboxHomeNodeLink {

    private Integer id;
    private String category;
    private Map<String, String> group;
    private Integer adapter;
    private String label;
    private String name;
    private String status;
    private FreeboxHomeNodeType type;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Map<String, String> getGroup() {
        return group;
    }

    public void setGroup(Map<String, String> group) {
        this.group = group;
    }

    public Integer getAdapter() {
        return adapter;
    }

    public void setAdapter(Integer adapter) {
        this.adapter = adapter;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public FreeboxHomeNodeType getType() {
        return type;
    }

    public void setType(FreeboxHomeNodeType type) {
        this.type = type;
    }

}