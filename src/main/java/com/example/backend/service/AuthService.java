package com.example.backend.service;

import com.example.backend.dto.auth.*;
import com.example.backend.exception.base_exceptions.BadRequestException;
import com.example.backend.exception.base_exceptions.ResourceNotFoundException;
import com.example.backend.model.Member;
import com.example.backend.model.QMember;
import com.example.backend.model.enumSet.MemberActiveEnum;
import com.example.backend.repository.MemberRepository;
import com.example.backend.security.AESUtil;
import com.example.backend.util.TokenProvider;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final TokenService tokenService;
    private final JPAQueryFactory queryFactory;


    // 비밀번호 재설정 전 인증
    @Transactional
    public boolean checkAuth(CheckAuthDTO checkAuth) {
        QMember qMember = QMember.member;

        Member member = queryFactory.selectFrom(qMember)
                .where(
                        qMember.loginId.eq(checkAuth.getLoginId())
                                .and(qMember.email.eq(checkAuth.getEmail()))
                )
                .fetchOne();

        // 회원이 존재하면 true, 없으면 false 반환
        boolean isValid = member != null;
        log.info("인증 체크 결과: loginId={}, email={}, isValid={}",
                checkAuth.getLoginId(), checkAuth.getEmail(), isValid);
        return isValid;
    }

    // 비밀번호 재설정
    @Transactional
    public void resetPassword(PasswordResetRequestDTO passwordResetRequest) {
        QMember qMember = QMember.member;

        log.info("비밀번호 재설정 요청: loginId={}", passwordResetRequest.getLoginId());

        // QueryDSL로 사용자 조회
        Member member = queryFactory.selectFrom(qMember)
                .where(
                        qMember.loginId.eq(passwordResetRequest.getLoginId())
                )
                .fetchOne();

        if (member == null) {
            log.error("사용자를 찾을 수 없습니다: loginId={}", passwordResetRequest.getLoginId());
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(passwordResetRequest.getNewPassword());

        // 비밀번호 업데이트
        member.setPassword(encodedPassword);

        log.info("비밀번호 재설정 완료: loginId={}", passwordResetRequest.getLoginId());
    }





    // 회원가입
    public ResponseEntity<String> signup(SignupRequestDTO signupRequest) {
        log.info("회원가입 요청 수신: {}", signupRequest.getLoginId());

        // 1. 중복 검사
        if (memberRepository.findByLoginId(signupRequest.getLoginId()).isPresent()) {
            log.warn("회원가입 실패: 중복된 아이디 {}", signupRequest.getLoginId());
            throw new BadRequestException("해당 아이디는 이미 사용 중입니다");
        }

        if (memberRepository.findByPhoneNumber(signupRequest.getPhoneNumber()).isPresent()) {
            log.warn("회원가입 실패: 중복된 휴대번호 {}", signupRequest.getPhoneNumber());
            throw new BadRequestException("해당 휴대번호는 이미 사용 중입니다");
        }

        if (memberRepository.findByEmail(signupRequest.getEmail()).isPresent()) {
            log.warn("회원가입 실패: 중복된 이메일 {}", signupRequest.getEmail());
            throw new BadRequestException("해당 이메일은 이미 사용 중입니다");
        }

        // 주민등록번호 암호화
        String encryptedIdentityNumber;
        try {
            encryptedIdentityNumber = AESUtil.encrypt(signupRequest.getIdentityNumber());
        } catch (Exception e) {
            log.error("암호화 실패: 주민등록번호 {}", signupRequest.getIdentityNumber(), e);
            throw new RuntimeException("회원가입 중 오류 발생");
        }


        // 2. 새로운 Member 객체 생성 및 정보 설정
        Member member = Member.builder()
                .loginId(signupRequest.getLoginId())
                .password(passwordEncoder.encode(signupRequest.getPassword()))
                .name(signupRequest.getName())
                .phoneNumber(signupRequest.getPhoneNumber())
                .email(signupRequest.getEmail())
                .createdAt(LocalDateTime.now())
                .identityNumber(encryptedIdentityNumber) // 암호화 된 주민등록번호 설정
                .activity(MemberActiveEnum.ACTIVE)
                .build();

        // 3. DB에 저장
        memberRepository.save(member);
        log.info("회원가입 성공: {}", signupRequest.getLoginId());
        return ResponseEntity.ok("회원가입 성공");
    }

    // 로그인
    public String login(LoginRequestDTO loginRequest, HttpServletResponse response) {
        log.info("로그인 요청 수신: {}", loginRequest.getLoginId());


        // 1. 계정 조회 및 예외 처리
        Member member = memberRepository.findByLoginId(loginRequest.getLoginId())
                .orElseThrow(() -> {
                    log.warn("로그인 실패: 잘못된 계정 {}", loginRequest.getLoginId());
                    return new ResourceNotFoundException("존재하지 않는 계정입니다");
                });

        // 2. 계정 활성 상태 확인
        if (member.getActivity() == MemberActiveEnum.INACTIVE) {
            throw new BadRequestException("계정이 비활성화되었습니다. 활성화를 원하면 확인 버튼을 누르세요.");
        }

        // 3. 비밀번호 검증
        if (!passwordEncoder.matches(loginRequest.getPassword(), member.getPassword())) {
            log.warn("로그인 실패: 잘못된 비밀번호");
            throw new BadRequestException("잘못된 비밀번호");
        }

        // 4. 액세스 토큰, 리프레시 토큰 생성
        String accessToken = tokenProvider.createAccessToken(member.getLoginId(), member.getMemberId());
        String refreshToken = tokenProvider.createRefreshToken(member.getLoginId(), member.getMemberId());
        log.info("토큰 생성 완료 - accessToken 및 refreshToken 생성");

        // 4. 리프레시 토큰 Redis에 저장
        tokenService.saveRefreshToken(member.getLoginId(), refreshToken);
        log.info("Redis에 리프레시 토큰 저장 완료");

        // 5. 액세스 토큰을 쿠키에 저장
        tokenProvider.setAccessTokenCookie(accessToken, response);
        log.info("accessToken 쿠키 설정 완료: " + accessToken);

        return refreshToken;
    }


    // 로그아웃
    public void logout(LogoutRequestDTO logoutRequest, HttpServletRequest request, HttpServletResponse response) {
        log.info("로그아웃 요청 수신: {}", logoutRequest);

        // 액세스 토큰 쿠키 로그 기록
        String accessToken = tokenProvider.resolveAccessToken(request);

        if (accessToken != null) {
            log.info("로그아웃 요청 시 액세스 토큰: {}", accessToken);
            tokenService.deleteRefreshToken(logoutRequest.getLoginId());
            log.info("-- Redis에서 리프레시 토큰 삭제 완료");

            tokenProvider.expireAccessTokenCookie(response);
            log.info("-- accessToken 쿠키 만료 설정 완료");
            log.info("로그아웃 성공! : {}", logoutRequest.getLoginId());
        } else {
            log.warn("로그인 된 상태가 아닙니다!");
            throw new BadRequestException("로그인된 상태가 아닙니다.");
        }

    }

    // 회원 탈퇴 (active -> inactive)
    public void inActiveMember(ActivityMemberRequestDTO activityMemberRequest) {
        Member member = memberRepository.findByLoginId(activityMemberRequest.getLoginId())
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 회원입니다."));

        member.setActivity(MemberActiveEnum.INACTIVE);
        memberRepository.save(member); // 변경된 상태 저장

        log.info("회원 탈퇴 성공: {}", activityMemberRequest);
    }

    // 회원 활성화 (inactive -> active)
    public void activeMember(ActivityMemberRequestDTO activityMemberRequest) {
        Member member = memberRepository.findByLoginId(activityMemberRequest.getLoginId())
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 회원입니다."));

        member.setActivity(MemberActiveEnum.ACTIVE);
        memberRepository.save(member); // 변경된 상태 저장

        log.info("회원 활성화 성공: {}", activityMemberRequest);
    }


    // 아이디 중복 확인 (true - 중복, false - 중복 아님)
    public boolean checkLoginID(CheckIdRequestDTO checkIdRequest) {
        return memberRepository.findByLoginId(checkIdRequest.getLoginId()).isPresent();
    }
    // 폰 번호 중복 확인 (true - 중복, false - 중복 아님)
    public boolean checkPhoneNumber(CheckPhoneNumberRequestDTO checkPhoneRequest) {
        return memberRepository.findByPhoneNumber(checkPhoneRequest.getPhoneNumber()).isPresent();
    }

    // 이메일 중복 확인 (true - 중복, false - 중복 아님)
    public boolean checkEmail(CheckEmailRequestDTO checkEmailRequest) {
        return memberRepository.findByEmail(checkEmailRequest.getEmail()).isPresent();
    }

}


