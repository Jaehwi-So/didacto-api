package com.didacto.api.v1.member;

import com.didacto.common.response.CommonResponse;
import com.didacto.config.security.SecurityUtil;
import com.didacto.domain.Member;
import com.didacto.dto.member.MemberEditRequest;
import com.didacto.dto.member.MemberFindResponse;
import com.didacto.repository.member.MemberRepository;
import com.didacto.service.member.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "MEMBER API", description = "회원과 관련된 API") // Swagger Docs : API 이름
@RestController
@RequiredArgsConstructor

@RequestMapping("api/v1")
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "MEMBER_01 : 회원 전체 조회 API", description = "전체 회원을 조회한다.")
    @GetMapping("/members")
    @ResponseStatus(HttpStatus.OK)
    public CommonResponse findAllMembers(){
        List<MemberFindResponse> result =  memberService.findAllMembers();
        return new CommonResponse<>(true, HttpStatus.OK, "회원 조회에 성공했습니다.", result);
    }

    @Operation(summary = "MEMBER_02 : 회원 개별 조회 API", description = "개별 회원을 조회한다.")
    @GetMapping("/members/{id}")
    public CommonResponse findMember(@PathVariable("id") Long id) {
        MemberFindResponse result= memberService.findMember(id);
        return new CommonResponse<>(true, HttpStatus.OK, "회원 조회에 성공했습니다.", result);

    }





    @Operation(summary = "MEMBER_03 : 회원 정보 수정 API", description = "회원 정보를 수정한다.")
    @PutMapping("/members")
    public CommonResponse editMemberInfo(@RequestBody MemberEditRequest memberEditRequest){
        Long userId = SecurityUtil.getCurrentMemberId();
        memberService.editMemberInfo(userId, memberEditRequest);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userId,
                memberEditRequest.getPassword());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        return new CommonResponse<>(true, HttpStatus.OK, "회원 정보 수정에 성공했습니다.", null);
    }


    @Operation(summary = "MEMBER_04 : 회원 탈퇴 API", description = "회원을 탈퇴시킨다.")
    @DeleteMapping("/members")
    public CommonResponse deleteMemberInfo() {
        Long userId = SecurityUtil.getCurrentMemberId();
        memberService.deleteMember(userId);
        return new CommonResponse<>(true, HttpStatus.OK, "회원이 탈퇴되었습니다.", null);
    }


}
