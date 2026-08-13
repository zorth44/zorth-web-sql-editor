package com.bocsoft.sqleditor.datasource.api;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class CreateDataSourceRequest extends ConnectionRequest {
    @NotBlank @Size(max=100) private String name;
    @NotBlank private String engine;
    @Size(max=500) private String description;
    @Override @NotNull @Size(min=1,max=1024) public String getPassword() { return super.getPassword(); }
    public String getName() { return name; } public void setName(String v) { name=v; }
    public String getEngine() { return engine; } public void setEngine(String v) { engine=v; }
    public String getDescription() { return description; } public void setDescription(String v) { description=v; }
}
