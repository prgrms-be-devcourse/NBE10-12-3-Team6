package csh.back.domain.member.entity;

import csh.back.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

//멤버 엔티티
@Getter
@Entity
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {
    //회원 이메일
    private String email;
    //회원 비밀번호
    private String password;
    //회원 이름 혹은 닉네임
    private String name;

    @Column(unique = true)
    private String refreshToken;

    @Builder
    private Member(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.refreshToken = UUID.randomUUID().toString();
    }

    // 로그아웃 시 refreshToken을 새 값으로 교체하여 기존 값을 무효화
    public void invalidateRefreshToken() {
        this.refreshToken = UUID.randomUUID().toString();
    }
}