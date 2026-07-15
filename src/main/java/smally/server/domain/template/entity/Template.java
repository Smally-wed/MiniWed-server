package smally.server.domain.template.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;

import java.util.Map;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import smally.server.domain.common.entity.BaseEntity;

@Entity
@Table(name = "templates", indexes = @Index(name = "idx_template_uid", columnList = "templateUid"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Template extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, columnDefinition = "UUID")
    private UUID templateUid;

    @Column(nullable = false)
    private String name;

    @Column
    private String thumbnail;

    @Column
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "section_schema", nullable = false)
    private Map<String, Object> sectionSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private Map<String, Object> variants;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_schema")
    private Map<String, Object> optionsSchema;

    @Builder
    private Template(String name, String thumbnail, String category,
                     Map<String, Object> sectionSchema, Map<String, Object> variants,
                     Map<String, Object> optionsSchema) {
        this.name = name;
        this.thumbnail = thumbnail;
        this.category = category;
        this.sectionSchema = sectionSchema;
        this.variants = variants;
        this.optionsSchema = optionsSchema;
    }

    @PrePersist
    protected void onCreate() {
        if (this.templateUid == null) {
            this.templateUid = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
