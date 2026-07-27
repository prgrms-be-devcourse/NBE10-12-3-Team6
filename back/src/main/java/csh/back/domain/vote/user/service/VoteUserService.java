package csh.back.domain.vote.user.service;

import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import csh.back.domain.vote.item.entity.VoteItem;
import csh.back.domain.vote.user.dto.response.VoteUserSaveResponseDto;
import csh.back.domain.vote.user.entity.VoteUser;
import csh.back.domain.vote.user.repository.VoteUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class VoteUserService {

    private final VoteUserRepository voteUserRepository;
    private final TripMemberRepository tripMemberRepository;

    private final Integer DEFAULT_UPDATE_COUNT = 0;

    @Transactional
    public VoteUserSaveResponseDto saveVoteUser(VoteItem voteItem, Long tripId, Long memberId) {

        TripMember tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripId)
                .orElseThrow(RuntimeException::new);
        VoteUser voteUser = voteUserRepository.findByVoteIdAndTripMemberId(
                voteItem.getVote().getId(), tripMember.getId())
                .orElse(null);
        if(voteUser == null) {
            voteUser = VoteUser.builder()
                    .vote(voteItem.getVote())
                    .voteItem(voteItem)
                    .tripMember(tripMember)
                    .updateCount(DEFAULT_UPDATE_COUNT)
                    .build();
            VoteUser saved = voteUserRepository.save(voteUser);
            return VoteUserSaveResponseDto.from(saved);
        }
        if(voteUser.getUpdateCount() == 2) throw new RuntimeException();
        voteUser.updateVoteItemAndincreaseUpdateCount(voteItem);
        return VoteUserSaveResponseDto.from(voteUser);
    }
}
