package smally.server.domain.component.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import smally.server.domain.common.entity.BaseEntity;

import java.util.Map;

@Entity
@Table(name = "components")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Component extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "component_id")
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String componentUId;

    @Column(nullable = false)
    private String name;

    @Setter
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "component_type", nullable = false)
    private ComponentType componentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_schema", nullable = false)
    private Map<String, Object> dataSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_schema")
    private Map<String, Object> optionSchema;

    @Builder
    private Component(String name, String componentUId,
                      Map<String, Object> dataSchema, Map<String, Object> optionSchema) {
        this.name = name;
        this.componentUId = componentUId;
        this.dataSchema = dataSchema;
        this.optionSchema = optionSchema;
    }

}
