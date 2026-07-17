package smally.server.domain.template.entity;

import jakarta.persistence.*;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import smally.server.domain.common.entity.BaseEntity;

@Entity
@Table(name = "option_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionDefinition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_definition_id")
    private Long id;

    @Column(name = "option_key", nullable = false, unique = true, updatable = false)
    private String key;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String controlType;

    @Column(nullable = false)
    private String scope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_values")
    private List<Object> allowedValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_value")
    private Object defaultValue;

    @Builder
    private OptionDefinition(String key, String label, String controlType, String scope,
                            List<Object> allowedValues, Object defaultValue) {
        this.key = key;
        this.label = label;
        this.controlType = controlType;
        this.scope = scope;
        this.allowedValues = allowedValues;
        this.defaultValue = defaultValue;
    }
}
