package smally.server.domain.component.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "component_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComponentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "component_type_id")
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String name;

    @Builder
    private ComponentType(String name) {
        this.name = name;
    }
}
