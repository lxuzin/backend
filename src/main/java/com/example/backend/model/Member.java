package com.example.backend.model;

import com.example.backend.model.enumSet.MemberActiveEnum;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "member")
public class Member extends BaseTime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 20)
    private String loginId; // 아이디

    @Column(name = "password", nullable = true, length = 255)
    private String password;

    @Column(name = "name", nullable = true, length = 50)
    private String name;

    @Column(name = "phone_number", nullable = true, length = 15)
    private String phoneNumber;

    // 주민등록 번호
    @Column(name = "identity_number", nullable = true, length = 30)
    private String identityNumber;

    @Column(name = "email", nullable = true, length = 30)
    private String email;

    @Column(name = "activity", nullable = true, length = 30)
    @Enumerated(value = EnumType.STRING)
    private MemberActiveEnum activity;


    // Builder를 이용하면 Service에서 체인 형태로 나타낼 수 있어 가독성이 높아짐
    @Builder
    public Member(String loginId, String password, String name, String phoneNumber, String email, String identityNumber, MemberActiveEnum activity) {
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.identityNumber = identityNumber;
        this.activity = MemberActiveEnum.ACTIVE;
    }
}

