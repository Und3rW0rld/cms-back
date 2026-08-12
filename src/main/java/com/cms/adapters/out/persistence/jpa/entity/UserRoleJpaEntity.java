package com.cms.adapters.out.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "user_roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserRoleJpaEntity.UserRoleId.class)
public class UserRoleJpaEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "role_id")
    private Integer roleId;

    /**
     * Composite key for {@link UserRoleJpaEntity}.
     */
    public record UserRoleId(Long userId, Integer roleId) implements Serializable {
    }
}
