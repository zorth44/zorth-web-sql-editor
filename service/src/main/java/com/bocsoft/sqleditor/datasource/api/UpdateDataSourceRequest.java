package com.bocsoft.sqleditor.datasource.api;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class UpdateDataSourceRequest extends ConnectionRequest {
    @NotBlank @Size(max=100) private String name;
    @Size(max=500) private String description;
    @NotNull @Min(1) private Long version;
    public String getName() { return name; } public void setName(String v) { name=v; }
    public String getDescription() { return description; } public void setDescription(String v) { description=v; }
    public Long getVersion() { return version; } public void setVersion(Long v) { version=v; }
}
