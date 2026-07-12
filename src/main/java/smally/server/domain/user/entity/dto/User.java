package smally.server.domain.user.entity.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import smally.server.domain.common.entity.BaseEntity;
import smally.server.domain.user.enums.UserRole;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole userRole;

    @Column
    private String nickname;

    @Builder
    private User(String email, String password, UserRole userRole, String nickname) {
        if(userRole == null){
            throw new NullPointerException("role is null");
        }
        this.email = email;
        this.password = password;
        this.userRole = userRole;
        this.nickname = nickname;
    }

}
