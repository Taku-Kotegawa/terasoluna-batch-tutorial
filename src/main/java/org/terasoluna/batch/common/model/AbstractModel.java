package org.terasoluna.batch.common.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class AbstractModel {
    private Long version;
    private String createdBy;
    private String lastModifiedBy;
    private LocalDateTime createdDate;
    private LocalDateTime lastModifiedDate;
}
