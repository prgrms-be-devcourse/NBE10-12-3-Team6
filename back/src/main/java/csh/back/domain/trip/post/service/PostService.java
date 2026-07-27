package csh.back.domain.trip.post.service;

import csh.back.domain.trip.group.entity.TripGroup;
import csh.back.domain.trip.group.service.TripGroupService;
import csh.back.domain.trip.member.entity.TripMember;
import csh.back.domain.trip.member.repository.TripMemberRepository;
import csh.back.domain.trip.member.validator.TripMemberValidator;
import csh.back.domain.trip.place.entity.TripPlace;
import csh.back.domain.trip.post.dto.request.UpdatePostRequest;
import csh.back.domain.trip.post.dto.response.PostResponse;
import csh.back.domain.trip.post.dto.response.PostTimeLineResponse;
import csh.back.domain.trip.post.dto.response.PostsDailyResponse;
import csh.back.domain.trip.post.entity.Post;
import csh.back.domain.trip.post.repository.PostRepository;
import csh.back.domain.trip.timeline.entity.TimeLine;
import csh.back.domain.trip.timeline.repository.TimeLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TimeLineRepository timeLineRepository;
//    private final PostImageService postImageService;
    private final S3UploadService s3UploadService;
    private final TripMemberValidator tripMemberValidator;
    private final TripGroupService tripGroupService;

    @Transactional(readOnly = true)
    public List<PostsDailyResponse> getPosts(Long tripId, Long memberId) {
        tripMemberValidator.validMember(tripId, memberId);

        TripGroup tripGroup = tripGroupService.findTripGroupById(tripId);
        List<TripMember> tripMembers = tripMemberRepository.findByTripGroupId(tripGroup.getId());

        // 사진 목록 (timeline + confirmedPlace fetch join 되어 있음)
        List<Post> posts = postRepository.findWithTimeLineAndPlaceByAuthorIdIn(tripMembers);

        // 여행 전체 timeline 한 번 조회 → 날짜별 맵 (빈 칸 슬롯 계산에 재사용, 쿼리 1번)
        List<TimeLine> allSchedules = timeLineRepository.findByTripGroupIdSorted(tripId);
        Map<LocalDate, List<TimeLine>> scheduleByDate = allSchedules.stream()
                .collect(Collectors.groupingBy(t -> t.getStartTime().toLocalDate()));

        // 사진 날짜별 그룹핑 (TreeMap으로 날짜 오름차순 자동 정렬)
        Map<LocalDate, List<Post>> postsByDate = posts.stream()
                .collect(Collectors.groupingBy(
                        post -> post.getCreatedAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        return postsByDate.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<Post> dailyPosts = entry.getValue();

                    // 그날 일정 (없는 날이면 빈 리스트 → 전부 빈 칸 슬롯으로 계산됨)
                    List<TimeLine> daySchedules = scheduleByDate.getOrDefault(date, List.of());

                    List<PostsDailyResponse.PostSummary> summaries = dailyPosts.stream()
                            .map(post -> toSummaryWithSlot(post, daySchedules))
                            .toList();

                    return new PostsDailyResponse(date, summaries);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(Long tripId, Long postId) {
        //값 검사
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다."));
        //여행별로 포스트를 구분하고 검증하는 IF문 추가
        if (!post.getTimeLine().getTripGroup().getId().equals(tripId)) {
            throw new IllegalArgumentException("해당 여행의 게시글이 아닙니다.");
        }

        return PostResponse.from(post);
    }

    // 게시글 수정
    @Transactional
    public void update(Long tripId, Long postId, UpdatePostRequest request) {

        Post post = findAuthorizedPost(tripId, postId);

        post.update(
                request.content(),
                request.location()
        );
    }
    // 게시글 삭제
    @Transactional
    public void delete(Long tripId, Long postId) {

        Post post = findAuthorizedPost(tripId, postId);

        postRepository.delete(post);
    }
    // 게시글 생성
    @Transactional
    public PostResponse create(
            Long tripId,
            Long memberId,
            Long timeLineId,
            MultipartFile image
    ) {
        tripMemberValidator.validMember(tripId, memberId);

        // 여행 멤버 조회
        TripMember author = tripMemberRepository
                .findByMemberIdAndTripGroupId(memberId, tripId)
                .orElseThrow(() -> new IllegalArgumentException("여행 멤버가 존재하지 않습니다."));
        // 타임라인 조회
        TimeLine timeLine = null;
        if(timeLineId != null) timeLine = timeLineRepository.findById(timeLineId).orElse(null);

        // 이미지 저장
        String imageUrl = null;

        if (image != null && !image.isEmpty()) {
            try {
                imageUrl = s3UploadService.uploadImage(image);
            } catch (IOException ie) {
                ie.getMessage();
            }
        }

        // 게시글 생성
        Post post = Post.builder()
                .author(author)
                .timeLine(timeLine)
                .isImg(image != null && !image.isEmpty())
                .contentUrl(imageUrl)
                .build();

        Post savedPost = postRepository.save(post);

        return PostResponse.from(savedPost);
    }



    /**
     * 현재 시각이 속한 슬롯 하나의 상태를 반환.
     * - 일정(timeline) 안이면 그 일정의 실제 start~end
     * - 빈 시간이면 정시 격자 규칙으로 계산한 조각 (직전 일정 끝 or 정시 기준)
     * - isTaken: 그 유저가 이 슬롯 시간대에 이미 사진을 올렸는지
     */
    public PostTimeLineResponse getCurrentSlot(Long tripId, Long memberId, int dayNumber) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();   // 오늘 00:00:00
        LocalDateTime dayEnd = dayStart.plusDays(1);                 // 내일 00:00:00
        TripMember tripMember = tripMemberRepository.findByMemberIdAndTripGroupId(memberId, tripId).orElseThrow(RuntimeException::new);
        // 1. 오늘 그 유저 사진 (하루치 + 유저 한정이라 30장 미만, 메모리 처리 OK)
        //    TODO: 실제 메서드/파라미터 경로 확인 (p.member.id, p.tripGroup.id, createdAt 오늘 범위 등)
        List<Post> todayPosts = postRepository.findByAuthorIdAndCreatedAtBetween(tripMember.getId(), dayStart, dayEnd);

        // 2. 오늘 일정 목록 (startTime asc 정렬)
        //    TODO: dayNumber로 조회하는 구조면 today → dayNumber 변환해서 넘길 것
        List<TimeLine> schedules = timeLineRepository.findByTripAndDateSorted(tripId, dayNumber);

        // 3. 현재 시각이 속한 슬롯 범위 계산 + placeName 계산
        LocalDateTime slotStart;
        LocalDateTime slotEnd;
        String confirmedPlaceName = null;
        Long timeLineId = null;   // 빈 칸이면 null 유지

        Optional<TimeLine> current = schedules.stream()
                .filter(s -> !now.isBefore(s.getStartTime()) && now.isBefore(s.getEndTime()))
                .findFirst();

        if (current.isPresent()) {
            // 일정 슬롯 → 일정 실제 범위 그대로
            TimeLine timeLine = current.get();
            slotStart = timeLine.getStartTime();
            slotEnd = timeLine.getEndTime();
            timeLineId = timeLine.getId();   // ← 일정 슬롯이면 timeLineId 채움

            // 장소 확정된 경우만 이름, 미확정이면 null
            TripPlace place = timeLine.getConfirmedPlace();
            confirmedPlaceName = (place != null) ? place.getName() : null;

        } else {
            // 빈 칸 슬롯 → 정시 격자 규칙, placeName/timeLineId 는 null 유지
            slotStart = calcEmptySlotStart(now, schedules);
            slotEnd = calcEmptySlotEnd(slotStart, schedules);
        }

        // 4. isTaken: 이 유저 사진 중 created_at이 [slotStart, slotEnd) 안에 있나
        boolean isTaken = todayPosts.stream()
                .anyMatch(p -> !p.getCreatedAt().isBefore(slotStart)
                        && p.getCreatedAt().isBefore(slotEnd));

        return new PostTimeLineResponse(slotStart, slotEnd, timeLineId, confirmedPlaceName, isTaken);
    }

    private PostsDailyResponse.PostSummary toSummaryWithSlot(Post post, List<TimeLine> daySchedules) {
        TimeLine timeLine = post.getTimeLine();

        // 일정 슬롯: timeline 값 그대로
        if (timeLine != null) {
            TripPlace tripPlace = timeLine.getConfirmedPlace();
            return new PostsDailyResponse.PostSummary(
                    post.getId(),
                    post.getContentUrl(),
                    timeLine.getId(),
                    timeLine.getStartTime(),
                    timeLine.getEndTime(),
                    (tripPlace != null) ? tripPlace.getName() : null,
                    post.getCreatedAt()
            );
        }

        // 빈 칸 슬롯: createdAt 기준 슬롯 범위 계산
        LocalDateTime captured = post.getCreatedAt();
        LocalDateTime slotStart = calcSlotStart(captured, daySchedules);
        LocalDateTime slotEnd = calcSlotEnd(slotStart, daySchedules);

        return new PostsDailyResponse.PostSummary(
                post.getId(),
                post.getContentUrl(),
                null,          // timeLineId 없음
                slotStart,     // 계산된 슬롯 시작
                slotEnd,       // 계산된 슬롯 끝
                null,          // 빈 칸이니 장소 없음
                post.getCreatedAt()
        );
    }

    private LocalDateTime calcSlotStart(LocalDateTime time, List<TimeLine> daySchedules) {
        LocalDateTime hourFloor = time.truncatedTo(ChronoUnit.HOURS);

        LocalDateTime lastScheduleEnd = daySchedules.stream()
                .map(TimeLine::getEndTime)
                .filter(end -> !end.isAfter(time))   // time 이전에 끝난 일정만
                .max(Comparator.naturalOrder())
                .orElse(hourFloor);

        return hourFloor.isAfter(lastScheduleEnd) ? hourFloor : lastScheduleEnd;
    }

    private LocalDateTime calcSlotEnd(LocalDateTime slotStart, List<TimeLine> daySchedules) {
        LocalDateTime end = isOnTheHour(slotStart)
                ? slotStart.plusHours(1)
                : slotStart.truncatedTo(ChronoUnit.HOURS).plusHours(1);

        LocalDateTime nextScheduleStart = daySchedules.stream()
                .map(TimeLine::getStartTime)
                .filter(start -> start.isAfter(slotStart))
                .min(Comparator.naturalOrder())
                .orElse(end);

        return end.isBefore(nextScheduleStart) ? end : nextScheduleStart;
    }

    private boolean isOnTheHour(LocalDateTime t) {
        return t.getMinute() == 0 && t.getSecond() == 0 && t.getNano() == 0;
    }

    /**
     * 빈 칸 슬롯의 시작 시각.
     * 현재 시각 이전에 끝난 일정의 끝점 vs 현재 시각 정시 내림 → 더 늦은 쪽.
     * (15:30에 일정 끝, 지금 15:45 → 15:30 / 지금 16:20 → 16:00)
     */
    private LocalDateTime calcEmptySlotStart(LocalDateTime now, List<TimeLine> schedules) {
        LocalDateTime hourFloor = now.truncatedTo(ChronoUnit.HOURS);

        LocalDateTime lastScheduleEnd = schedules.stream()
                .map(TimeLine::getEndTime)
                .filter(end -> !end.isAfter(now))        // now 이전에 끝난 것
                .max(Comparator.naturalOrder())
                .orElse(hourFloor);

        return hourFloor.isAfter(lastScheduleEnd) ? hourFloor : lastScheduleEnd;
    }

    /**
     * 빈 칸 슬롯의 종료 시각.
     * slotStart 기준 다음 정시. 단 그 사이에 시작하는 다음 일정이 있으면 거기서 컷.
     * (15:30 → 16:00, 16:00 → 17:00, 중간에 일정 있으면 그 시작 시각)
     */
    private LocalDateTime calcEmptySlotEnd(LocalDateTime slotStart, List<TimeLine> schedules) {
        LocalDateTime end = isOnTheHour(slotStart)
                ? slotStart.plusHours(1)
                : slotStart.truncatedTo(ChronoUnit.HOURS).plusHours(1);

        LocalDateTime nextScheduleStart = schedules.stream()
                .map(TimeLine::getStartTime)
                .filter(start -> start.isAfter(slotStart))
                .min(Comparator.naturalOrder())
                .orElse(end);

        return end.isBefore(nextScheduleStart) ? end : nextScheduleStart;
    }

    private Long getCurrentMemberId() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return (Long) authentication.getDetails();
    }

    private void validateAuthor(Post post) {

        Long memberId = getCurrentMemberId();

        if (!post.getAuthor().getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("작성자만 수정 및 삭제할 수 있습니다.");
        }
    }

    private Post findAuthorizedPost(Long tripId, Long postId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글이 존재하지 않습니다."));

        if (!post.getTimeLine().getTripGroup().getId().equals(tripId)) {
            throw new IllegalArgumentException("해당 여행의 게시글이 아닙니다.");
        }

        validateAuthor(post);

        return post;
    }


}