package smally.server.domain.template.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;

import java.util.List;
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
    @Column(name = "sections", nullable = false)
    private List<Map<String, Object>> sections;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "theme")
    private Map<String, Object> theme;

    @Builder
    private Template(String name, String thumbnail, String category,
                     List<Map<String, Object>> sections, Map<String, Object> theme) {
        this.name = name;
        this.thumbnail = thumbnail;
        this.category = category;
        this.sections = sections;
        this.theme = theme;
    }

    @PrePersist
    protected void onCreate() {
        if (this.templateUid == null) {
            this.templateUid = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
