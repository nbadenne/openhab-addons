package org.openhab.binding.freebox.internal.api.model;

import java.util.List;
import java.util.Map;

import org.openhab.binding.freebox.internal.api.model.FreeboxHomeNodeType;

public class FreeboxHomeNode {

    private Integer id;
    private String label;
    private String name;
    private Integer adapter;
    private String category;
    private String status;
    private Map<String, String> props;
    private Map<String, String> group;
    private List<FreeboxHomeNodeEndpoint> show_endpoints;
    private List<FreeboxHomeNodeLink> signal_links;
    private List<FreeboxHomeNodeLink> slot_links;
    private FreeboxHomeNodeType type;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAdapter() {
        return adapter;
    }

    public void setAdapter(Integer adapter) {
        this.adapter = adapter;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public Map<String, String> getGroup() {
        return group;
    }

    public void setGroup(Map<String, String> group) {
        this.group = group;
    }

    public List<FreeboxHomeNodeEndpoint> getShow_endpoints() {
        return show_endpoints;
    }

    public void setShow_endpoints(List<FreeboxHomeNodeEndpoint> show_endpoints) {
        this.show_endpoints = show_endpoints;
    }

    public List<FreeboxHomeNodeLink> getSignal_links() {
        return signal_links;
    }

    public void setSignal_links(List<FreeboxHomeNodeLink> signal_links) {
        this.signal_links = signal_links;
    }

    public List<FreeboxHomeNodeLink> getSlot_links() {
        return slot_links;
    }

    public void setSlot_links(List<FreeboxHomeNodeLink> slot_links) {
        this.slot_links = slot_links;
    }

    public FreeboxHomeNodeType getType() {
        return type;
    }

    public void setType(FreeboxHomeNodeType type) {
        this.type = type;
    }

}