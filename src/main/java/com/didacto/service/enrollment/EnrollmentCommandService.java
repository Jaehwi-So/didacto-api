package com.didacto.service.enrollment;


import com.didacto.common.ErrorDefineCode;
import com.didacto.config.exception.custom.exception.AlreadyExistElementException409;
import com.didacto.config.exception.custom.exception.NoSuchElementFoundException404;
import com.didacto.domain.*;
import com.didacto.dto.enrollment.LectureAndMemberType;
import com.didacto.repository.enrollment.EnrollmentRepository;
import com.didacto.repository.lecture.LectureRepository;
import com.didacto.repository.lectureMemer.LectureMemberRepository;
import com.didacto.repository.member.MemberRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentCommandService {
    private final EnrollmentRepository enrollmentRepository;
    private final LectureRepository lectureRepository;
    private final MemberRepository memberRepository;
    private final LectureMemberRepository lectureMemberRepository;


    /**
     * [학생 : 강의 참여 요청]
     * 해당 강의 참여를 교수자에게 요청한다.
     * @param lectureId - 강의 ID
     * @return Long - 초대 PK
     */
    @Transactional
    public Long requestEnrollment(Long lectureId, Long memberId){

        // Validate : lecture, member의 존재여부 확인
        LectureAndMemberType targets = getMemberAndLecture(memberId, lectureId);

        // Validate : 이미 대기중인 초대 요청이 있는 지 조회
        isHaveAlreadyWaitRequest(targets.getMember(), targets.getLecture());

        // Validate : 이미 강의에 소속되어 있는 지 조회
        isHaveAlreadyJoin(targets.getMember(), targets.getLecture());

        // Insert : 데이터베이스 저장
        Enrollment enrollment = Enrollment.builder()
                .status(EnrollmentStatus.WAITING)
                .lecture(targets.getLecture())
                .member(targets.getMember())
                .modified_by(targets.getMember())
                .build();
        enrollment = enrollmentRepository.save(enrollment);

        // Out
       return enrollment.getId();
    }

    /**
     * [학생 : 강의 참여 요청 취소]
     * 참여 요청을 취소한다.
     * @param enrollId - 초대 ID
     * @param memberId - 멤버 ID
     * @return Long - 초대 PK
     */
    @Transactional
    public Long cancelEnrollment(Long enrollId, Long memberId){

        // Validate : 멤버가 존재하는 지 확인
        Member member = memberRepository.findById(memberId).orElseThrow(() -> {
            throw new NoSuchElementFoundException404(ErrorDefineCode.USER_NOT_FOUND);
        });

        // Validate & Find : 멤버와 일치, WAITING 상태, enrollId에 해당하는 레코드 조회
        Enrollment enrollment = enrollmentRepository.findWaitingEnrollment(enrollId, memberId);
        if(enrollment == null){
            throw new NoSuchElementFoundException404(ErrorDefineCode.ALREADY_ENROLL);
        }

        // Update : Status 변경, 수정자 변경
        enrollment.updateStatus(EnrollmentStatus.CANCELLED);
        enrollment.updateModifiedMember(member);
        enrollment = enrollmentRepository.save(enrollment);

        // Out
        return enrollment.getId();
    }


    /**
     * [교수자 : 강의 참여 요청 처리 ]
     * 참여 요청에 대해서 승인, 혹은 거절한다
     * @param enrollId - 초대 ID
     * @param tutorId - 현재 사용자(교수) ID
     * @param action - 승인/거절
     * @return Long - 초대 PK
     */
    @Transactional
    public Long confirmEnrollment(Long enrollId, Long tutorId, EnrollmentStatus action){

        // Validate : 강의 소유자가 존재하는 지 확인
        Member tutor = memberRepository.findById(tutorId).orElseThrow(() -> {
            throw new NoSuchElementFoundException404(ErrorDefineCode.USER_NOT_FOUND);
        });

        // Validate & Find : 강의 소유자와 일치, WAITING 상태, enrollId에 해당하는 레코드 조회
        Enrollment enrollment = enrollmentRepository.findWaitingEnrollmentByTutorId(enrollId, tutorId);
        if(enrollment == null){
            throw new NoSuchElementFoundException404(ErrorDefineCode.ALREADY_ENROLL);
        }

        // Update : Status 변경, 수정자 변경
        enrollment.updateStatus(action);
        enrollment.updateModifiedMember(tutor);
        enrollment = enrollmentRepository.save(enrollment);

        // 참여 승인 시 : Member <-> Lecture 연관 설정
        if(action.equals(EnrollmentStatus.ACCEPTED)){
            Member member = enrollment.getMember();
            Lecture lecture = enrollment.getLecture();

            // Validate : 이미 강의에 소속되어 있는지 검사
            isHaveAlreadyJoin(member, lecture);

            // Relation Set
            LectureMember lectureMember = LectureMember.builder()
                    .member(member)
                    .lecture(lecture)
                    .modifiedBy(tutor)
                    .deleted(false)
                    .build();

            lectureMemberRepository.save(lectureMember);

        }

        // Out : Object 변환 후 반환
       return enrollment.getId();
    }



    /**
     * 초대될 member와, lecture가 존재하는 지 확인하고 그 값들을 반환.
     */
    private LectureAndMemberType getMemberAndLecture(Long memberId, Long lectureId){

        Lecture lecture = lectureRepository.findById(lectureId).orElseThrow(() -> {
            throw new NoSuchElementFoundException404(ErrorDefineCode.LECTURE_NOT_FOUND);
        });

        Member member = memberRepository.findById(memberId).orElseThrow(() -> {
            throw new NoSuchElementFoundException404(ErrorDefineCode.USER_NOT_FOUND);
        });

        return new LectureAndMemberType(lecture, member);
    }


    /**
     * 해당 회원이 이미 Wait 상태인 Member-Lecture Enroll이 존재하는지 확인
     */
    private void isHaveAlreadyWaitRequest(Member member, Lecture lecture){
        boolean exist = enrollmentRepository.existWaitingEnrollmentByMemberId(member.getId(), lecture.getId());

        if(exist) {
            throw new AlreadyExistElementException409(ErrorDefineCode.ALREADY_ENROLL_REQUEST);
        }
    }



    /**
     * 이미 해당 Lecture에 Member가 소속되어 있는지 확인
     */
    private void isHaveAlreadyJoin(Member member, Lecture lecture){
        boolean isJoined = enrollmentRepository.existJoinByMemberAndLecture(
                member.getId(), lecture.getId());

        if(isJoined) {
            throw new AlreadyExistElementException409(ErrorDefineCode.ALREADY_JOIN);
        }
    }




}